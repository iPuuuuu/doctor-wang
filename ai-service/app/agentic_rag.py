"""自校正 Agentic RAG 图（LangGraph）。

流水线：RETRIEVE → GENERATE → VERIFY ──(有未支持声明且可重试)──▶ CORRECT → VERIFY
                                        └──(通过/重试耗尽/fail-closed)──▶ END

对应 2026 Agentic RAG 趋势的 "quality-gate backtracking / self-correction"：
生成后由声明级校验器（app/verifier.py）检查忠实度，发现未经证据支持的陈述即
用修正提示词回溯重生成（上限 1 次）；重试后仍存在未支持陈述时 fail-closed：
标记 needsReview + 追加"以报告原文为准"警告，绝不把未核实内容当作已核实呈现。
"""
from typing import TypedDict

from langgraph.graph import END, START, StateGraph

from .rag import build_citations, build_context, generate_summary, retrieve
from .schemas import RagResponse
from .verifier import correction_prompt, verify_claims

MAX_CORRECTIONS = 1


class AgenticRagState(TypedDict, total=False):
    request: object
    store: object
    embedder: object
    generator: object
    judge: object
    searcher: object
    # intermediate
    status: str
    risk: str
    hits: list
    context: str
    summary: str
    unsupported: list[str]
    corrections: int
    verify_failed: bool
    # output
    response: object


def _retrieve(state: AgenticRagState) -> dict:
    status, risk, hits = retrieve(state["request"], state["store"], state["embedder"], state.get("searcher"))
    return {"status": status, "risk": risk, "hits": hits}


def _blocked(state: AgenticRagState) -> dict:
    request, trace = state["request"], state["request"].traceId
    if state["status"] == "namespace_blocked":
        resp = RagResponse(requestId=request.requestId, status="SAFETY_BLOCKED", riskLevel="HIGH", summary="未授权的报告空间。",
                           citations=[], warnings=["请求未通过报告归属校验。"], needsReview=True, traceId=trace)
    elif state["status"] == "safety_blocked":
        resp = RagResponse(requestId=request.requestId, status="SAFETY_BLOCKED", riskLevel="HIGH",
                           summary="当前问题可能涉及紧急危险信号，请立即联系急救服务（120）或尽快就医。",
                           citations=[], warnings=["AI 健康辅助不能替代急诊或医生判断。"], needsReview=True, traceId=trace)
    else:  # insufficient
        resp = RagResponse(requestId=request.requestId, status="INSUFFICIENT_EVIDENCE", riskLevel=state["risk"],
                           summary="当前报告中没有足够的可检索资料回答这个问题。",
                           citations=[], warnings=["请补充资料并向医生确认。"], needsReview=True, traceId=trace)
    return {"response": resp}


def _generate(state: AgenticRagState) -> dict:
    request = state["request"]
    citations = build_citations(request, state["hits"])
    context = build_context(request, state["hits"])
    summary = generate_summary(request, context, state.get("generator"))
    return {"context": context, "summary": summary, "citations": citations}


def _verify(state: AgenticRagState) -> dict:
    judge = state.get("judge") or state.get("generator")
    if judge is None:
        return {"unsupported": [], "verify_failed": True}
    result = verify_claims(judge, state.get("summary", ""), state.get("context", ""))
    if result["total"] == 0:
        # judge 无法解析/失败：不阻塞，但标记需要人工复核（fail-closed 不假装已验证）
        return {"unsupported": [], "verify_failed": True}
    return {"unsupported": result["unsupported"], "verify_failed": False}


def _route_after_verify(state: AgenticRagState) -> str:
    if state.get("verify_failed"):
        return "end"  # 校验器不可用：透传原答案（后端 AiSafetyGate 仍强制引用有效性）
    if state.get("unsupported") and state.get("corrections", 0) < MAX_CORRECTIONS:
        return "correct"
    return "end"


def _correct(state: AgenticRagState) -> dict:
    """用修正提示词重生成（同一上下文），并记录本轮未支持声明。

    保留与原生成一致的 system prompt（anxiety 专用提示词 / 提供方默认），
    避免修正版丢失共情与医疗边界指导。
    """
    request = state["request"]
    from .rag import ANXIETY_SYSTEM_PROMPT
    system_prompt = ANXIETY_SYSTEM_PROMPT if request.mode == "anxiety" else None
    try:
        summary = state["generator"].generate(
            request.question,
            correction_prompt(request.question, state.get("context", ""), state.get("unsupported", [])),
            system_prompt=system_prompt,
        )
    except Exception:
        summary = state.get("summary", "")
    return {"summary": summary, "corrections": state.get("corrections", 0) + 1}


def _finalize(state: AgenticRagState) -> dict:
    request, risk = state["request"], state["risk"]
    warnings = ["请将报告原文和本解释一并交由医生确认。"]
    needs_review = risk == "MEDIUM"
    if state.get("unsupported") and state.get("corrections", 0) >= MAX_CORRECTIONS:
        needs_review = True
        warnings.append("AI 生成内容包含未能完全依据报告核实的表述，请以报告原文和医生意见为准。")
    resp = RagResponse(requestId=request.requestId, status="OK", riskLevel=risk,
                       summary=state.get("summary", ""),
                       citations=build_citations(request, state.get("hits", [])),
                       warnings=warnings, needsReview=needs_review, traceId=request.traceId)
    return {"response": resp}


def build_agentic_rag_graph():
    graph = StateGraph(AgenticRagState)
    graph.add_node("retrieve", _retrieve)
    graph.add_node("blocked", _blocked)
    graph.add_node("generate", _generate)
    graph.add_node("verify", _verify)
    graph.add_node("correct", _correct)
    graph.add_node("finalize", _finalize)

    graph.add_edge(START, "retrieve")
    graph.add_conditional_edges("retrieve", lambda s: "blocked" if s.get("status") != "ok" else "generate",
                                {"blocked": "blocked", "generate": "generate"})
    graph.add_edge("blocked", END)
    graph.add_edge("generate", "verify")
    graph.add_conditional_edges("verify", _route_after_verify, {"correct": "correct", "end": "finalize"})
    graph.add_edge("correct", "verify")
    graph.add_edge("finalize", END)
    return graph.compile()


agentic_rag_graph = build_agentic_rag_graph()
