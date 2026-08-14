"""Multi-step health assistant agent graph.

Pipeline: SAFETY → TOOL_ROUTER → {SYMPTOM_CHECKER, LAB_LOOKUP, DRUG_CHECK} → SYNTHESIZE → OUTPUT
"""
import uuid
from typing import TypedDict

from langgraph.graph import END, START, StateGraph

from .agent_schemas import AgentRequest, AgentResponse, AgentStep
from .agent_tools import (ToolResult, drug_interaction_check,
                           extract_drugs_from_text,
                           extract_lab_values_from_text, lab_value_lookup,
                           symptom_checker)
from .embeddings import EmbeddingProvider
from .rag import query_rag
from .safety import risk_triage


class AgentState(TypedDict, total=False):
    request: AgentRequest
    store: object
    embedder: EmbeddingProvider
    generator: object
    # Intermediate state
    safety_risk: str
    safety_blocked: bool
    reports_context: list[str]
    tool_results: list[ToolResult]
    steps: list[AgentStep]
    response: AgentResponse


def _safety_check(state: AgentState) -> dict:
    """Step 1: Safety gate. Block HIGH risk immediately."""
    request = state["request"]
    risk = risk_triage(request.message)
    state["safety_risk"] = risk
    state["safety_blocked"] = risk == "HIGH"
    state["steps"] = [AgentStep(
        stepType="SAFETY_CHECK",
        summary=f"安全闸检查完成，风险等级: {risk}",
        needsReview=risk != "LOW",
        confidence=1.0,
    )]
    return state


def _tool_router(state: AgentState) -> str:
    """Step 2: Determine which tool(s) to use based on message content."""
    if state.get("safety_blocked"):
        return "safety_blocked"

    message = state["request"].message.lower()
    # Check for lab value patterns
    has_lab_values = any(lab in message for lab in
        ["ALT", "AST", "血糖", "HbA1c", "肌酐", "白细胞", "血小板", "胆红素", "GGT"])

    # Check for drug patterns
    has_drugs = any(drug in message for drug in
        ["二甲双胍", "华法林", "阿司匹林", "布洛芬", "他汀", "氯吡格雷", "胰岛素"])

    # Check for symptom patterns (vs report queries)
    has_symptoms = any(word in message for word in
        ["症状", "疼", "痛", "晕", "咳嗽", "发热", "肿", "不舒服", "难受", "心悸"])

    if has_symptoms and not has_lab_values:
        return "symptom_checker"
    elif has_drugs:
        return "drug_check"
    elif has_lab_values:
        return "lab_lookup"
    else:
        return "report_search"


def _safety_blocked_respond(state: AgentState) -> dict:
    """Respond when safety gate blocks the query."""
    request = state["request"]
    state["response"] = AgentResponse(
        requestId=request.requestId,
        conversationId=request.conversationId or str(uuid.uuid4()),
        status="SAFETY_BLOCKED",
        riskLevel="HIGH",
        answer="检测到高风险症状，请立即联系急救服务（120）或尽快就医。AI 健康辅助不能替代急诊或医生判断。",
        reasoningSteps=state.get("steps", []),
        warnings=["高风险症状检测", "请立即就医"],
        needsReview=True,
        traceId=request.traceId,
    )
    return state


def _report_search(state: AgentState) -> dict:
    """Step 3a: Search reports for relevant context via RAG."""
    request = state["request"]
    steps = state.get("steps", [])
    reports_context = []

    for report_id in request.reportIds:
        try:
            from .schemas import RagRequest
            rag_req = RagRequest(
                requestId=f"agent-{request.requestId}",
                userId=request.userId,
                reportId=report_id,
                question=request.message,
                namespaceHints=[f"report:{request.userId}:{report_id}"],
                traceId=request.traceId,
            )
            rag_resp = query_rag(rag_req, state["store"], state["embedder"], state.get("generator"))
            if rag_resp.citations:
                reports_context.extend(c.excerpt for c in rag_resp.citations[:3])
        except Exception:
            pass

    context_summary = "\n".join(reports_context[:5]) if reports_context else "未找到相关报告内容"
    steps.append(AgentStep(
        stepType="REPORT_LOOKUP",
        summary=f"检索到 {len(reports_context)} 条相关报告片段",
        findings=reports_context[:3],
        confidence=0.6 if reports_context else 0.0,
    ))
    state["reports_context"] = reports_context
    state["steps"] = steps
    return state


def _symptom_check(state: AgentState) -> dict:
    """Step 3b: Check symptoms against knowledge base."""
    request = state["request"]
    steps = state.get("steps", [])
    reports_context = state.get("reports_context", [])

    result = symptom_checker(request.message, reports_context)
    steps.append(AgentStep(
        stepType="SYMPTOM_CHECKER",
        summary="症状分析完成",
        findings=result.findings,
        confidence=result.confidence,
        needsReview=result.needsReview,
    ))
    state["steps"] = steps
    state.setdefault("tool_results", []).append(result)
    return state


def _lab_lookup(state: AgentState) -> dict:
    """Step 3c: Look up lab values from message text."""
    steps = state.get("steps", [])
    message = state["request"].message

    lab_values = extract_lab_values_from_text(message)
    findings = []
    needs_review = False
    for lab_name, value in lab_values:
        result = lab_value_lookup(lab_name, value)
        findings.extend(result.findings)
        needs_review = needs_review or result.needsReview

    if not findings:
        findings.append("未在消息中检测到已知检验指标")

    steps.append(AgentStep(
        stepType="LAB_VALUE_LOOKUP",
        summary="检验指标分析完成",
        findings=findings,
        confidence=0.8 if lab_values else 0.1,
        needsReview=needs_review,
    ))
    state["steps"] = steps
    return state


def _drug_check(state: AgentState) -> dict:
    """Step 3d: Check drug interactions."""
    steps = state.get("steps", [])
    message = state["request"].message

    drugs = extract_drugs_from_text(message)
    result = drug_interaction_check(drugs)
    steps.append(AgentStep(
        stepType="DRUG_INTERACTION",
        summary=f"药物检查完成 ({len(drugs)} 种药物检测)",
        findings=result.findings,
        confidence=result.confidence,
        needsReview=result.needsReview,
    ))
    state["steps"] = steps
    state.setdefault("tool_results", []).append(result)
    return state


def _synthesize(state: AgentState) -> dict:
    """Step 4: Synthesize all tool results into a coherent response."""
    request = state["request"]
    steps = state.get("steps", [])
    tool_results = state.get("tool_results", [])

    all_findings = []
    any_needs_review = False
    for r in tool_results:
        all_findings.extend(r.findings)
        any_needs_review = any_needs_review or r.needsReview

    has_report_context = bool(state.get("reports_context"))
    has_tools = bool(tool_results)

    if has_tools and has_report_context:
        answer = f"综合分析：\n\n报告发现：{'; '.join(state['reports_context'][:2])}\n\n工具分析：{'；'.join(all_findings[:5])}\n\n请注意：本分析仅供健康辅助参考，不能替代医生诊断。如有不适请及时就医。"
    elif has_tools:
        answer = f"工具分析结果：{'；'.join(all_findings[:5])}\n\n未找到相关报告记录。建议上传相关检查报告以获得更准确的分析。\n\n本分析仅供健康辅助参考，不能替代医生诊断。"
    elif has_report_context:
        answer = f"报告发现：{'; '.join(state['reports_context'][:2])}\n\n本分析仅供健康辅助参考，不能替代医生诊断。"
    else:
        answer = "抱歉，我未能从您的报告和当前知识库中找到足够的信息来回答。建议您上传最新的检查报告，或向医生咨询。\n\n本分析仅供健康辅助参考，不能替代医生诊断。"

    risk = state.get("safety_risk", "LOW")
    state["response"] = AgentResponse(
        requestId=request.requestId,
        conversationId=request.conversationId or str(uuid.uuid4()),
        status="OK",
        riskLevel=risk,
        answer=answer,
        reasoningSteps=steps,
        citations=[],
        warnings=["本分析仅供健康辅助参考，不能替代医生诊断。"],
        followUpQuestions=_suggest_followups(request.message, all_findings),
        needsReview=any_needs_review or risk == "MEDIUM",
        traceId=request.traceId,
    )
    return state


def _suggest_followups(_message: str, findings: list[str]) -> list[str]:
    """Suggest follow-up questions based on findings (mock)."""
    suggestions = []
    for finding in findings[:3]:
        if "HIGH" in finding:
            suggestions.append("是否需要立即就医？")
        elif "MEDIUM" in finding:
            suggestions.append("建议什么时候复查？")
        elif "高于" in finding or "低于" in finding:
            suggestions.append("这个指标异常需要注意什么？")
    if not suggestions:
        suggestions = ["是否需要进一步检查？", "多久复查一次比较合适？"]
    return suggestions[:3]


def build_agent_graph():
    graph = StateGraph(AgentState)

    graph.add_node("safety_check", _safety_check)
    graph.add_node("safety_blocked_respond", _safety_blocked_respond)
    graph.add_node("report_search", _report_search)
    graph.add_node("symptom_checker", _symptom_check)
    graph.add_node("lab_lookup", _lab_lookup)
    graph.add_node("drug_check", _drug_check)
    graph.add_node("synthesize", _synthesize)

    graph.add_edge(START, "safety_check")
    graph.add_conditional_edges("safety_check", _tool_router, {
        "safety_blocked": "safety_blocked_respond",
        "symptom_checker": "symptom_checker",
        "lab_lookup": "lab_lookup",
        "drug_check": "drug_check",
        "report_search": "report_search",
    })
    graph.add_edge("safety_blocked_respond", END)
    graph.add_edge("report_search", "synthesize")
    graph.add_edge("symptom_checker", "synthesize")
    graph.add_edge("lab_lookup", "synthesize")
    graph.add_edge("drug_check", "synthesize")
    graph.add_edge("synthesize", END)

    return graph.compile()


agent_graph = build_agent_graph()
