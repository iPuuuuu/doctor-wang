from .embeddings import EmbeddingProvider
from .hybrid import hybrid_rank, lexical_score
from .schemas import Citation, RagResponse
from .safety import risk_triage
from .symptom_kb import symptom_kb_context

# 安心话聊（疑病症/大病焦虑）专用系统提示词：
# 结合用户真实健康档案与检查报告，缓解对重大疾病的过度担忧，
# 同时严守健康辅助边界，不否认真实症状、不替代就医。
ANXIETY_SYSTEM_PROMPT = (
    "你是 Doctor-Wang 的健康安心助手，帮助用户缓解对重大疾病的过度担忧（疑病症/健康焦虑）。\n"
    "回答规则：\n"
    "1. 先共情：承认担心是正常的，不指责、不敷衍。\n"
    "2. 严格基于用户真实的健康档案与检查报告说话：明确区分\"实际检查结果支持什么\"与\"用户担心的疾病\"，"
    "如果资料中没有任何支持该担忧的证据，可以温和说明这一点（例如：从现有检查看，没有支持 X 的依据），但绝不能断言\"你没病\"。\n"
    "3. 只引用提供的资料，不编造检查结果、概率或药物建议。\n"
    "4. 对常见良性解释可作有依据的说明（如紧张性头痛很常见，不等于脑肿瘤），但保持克制。\n"
    "5. 明确边界：这是健康辅助，不是诊断；若症状真实存在、持续或加重，应就医检查；"
    "若焦虑明显影响生活，建议咨询心理科/精神科。\n"
    "6. 语气温和、简短、可读，使用中文。"
)


def search(store, namespace: str, query_vector: list[float], query_text: str, top_k: int) -> list[tuple]:
    """向量 + 词法（字符二元组）RRF 融合检索；store 不支持词法通道时回退纯向量。"""
    if not hasattr(store, "all_chunks"):
        return store.search(namespace, query_vector, top_k)
    chunks = store.all_chunks(namespace)
    if not chunks:
        return store.search(namespace, query_vector, top_k)
    vector_hits = store.search(namespace, query_vector, top_k=max(top_k * 4, 10))
    vector_scores = {r.chunk_id: score for r, score in vector_hits}
    lexical_scores = {c.chunk_id: lexical_score(query_text, c.text) for c in chunks}
    ordered = hybrid_rank(vector_scores, lexical_scores, top_k)
    by_id = {c.chunk_id: c for c in chunks}
    return [(by_id[cid], vector_scores.get(cid, 0.0)) for cid in ordered if cid in by_id]


def retrieve(request, store, embedder: EmbeddingProvider, searcher=None) -> tuple[str, str, list[tuple]]:
    """检索阶段：命名空间校验 + 风险分流 + 检索。返回 (status, risk, hits)。
    status ∈ {"ok", "namespace_blocked", "safety_blocked", "insufficient"}。"""
    namespace = f"report:{request.userId}:{request.reportId}"
    if namespace not in request.namespaceHints:
        return "namespace_blocked", "HIGH", []
    risk = risk_triage(request.question)
    if risk == "HIGH":
        return "safety_blocked", risk, []
    retrieve_fn = searcher or (lambda ns, qv, qt, k: search(store, ns, qv, qt, k))
    hits = retrieve_fn(namespace, embedder.embed([request.question])[0], request.question, request.topK)
    if not hits:
        return "insufficient", risk, []
    return "ok", risk, hits


def build_citations(request, hits) -> list[Citation]:
    return [Citation(sourceId=str(request.reportId), chunkId=r.chunk_id,
                     pageNo=r.metadata.get("page_no"), excerpt=r.text,
                     confidence=r.metadata.get("confidence", 0.0),
                     documentVersion=r.metadata.get("document_version", "unknown"))
            for r, _ in hits]


def build_context(request, hits) -> str:
    """生成阶段的上下文：报告片段（+ 安心话聊：健康档案、症状知识库）。"""
    citations = build_citations(request, hits)
    context = "\n".join(c.excerpt for c in citations)
    if request.mode == "anxiety":
        kb_context, _ = symptom_kb_context(request.question)
        context = f"用户健康档案:\n{request.healthProfile or '（未提供）'}\n\n报告片段:\n{context}"
        if kb_context:
            # 受控症状知识库：常见良性解释 + 就医信号（非诊断、有版本、可追溯）
            context = f"{context}\n\n{kb_context}"
    return context


def generate_summary(request, context, generator) -> str:
    """生成阶段：LLM 生成（anxiety 用专用提示词）。失败时诚实兜底。"""
    try:
        if request.mode == "anxiety":
            return generator.generate(request.question, context, system_prompt=ANXIETY_SYSTEM_PROMPT)
        return generator.generate(request.question, context)
    except Exception:
        return "模型服务暂时不可用；请根据报告原文向医生确认。"


def query_rag(request, store, embedder: EmbeddingProvider, generator=None, searcher=None) -> RagResponse:
    """单步 RAG（与自校正图共用检索/生成原语，行为向后兼容）。"""
    status, risk, hits = retrieve(request, store, embedder, searcher)
    trace = request.traceId
    if status == "namespace_blocked":
        return RagResponse(requestId=request.requestId, status="SAFETY_BLOCKED", riskLevel="HIGH", summary="未授权的报告空间。", citations=[], warnings=["请求未通过报告归属校验。"], needsReview=True, traceId=trace)
    if status == "safety_blocked":
        return RagResponse(requestId=request.requestId, status="SAFETY_BLOCKED", riskLevel="HIGH",
                           summary="当前问题可能涉及紧急危险信号，请立即联系急救服务（120）或尽快就医。",
                           citations=[], warnings=["AI 健康辅助不能替代急诊或医生判断。"],
                           needsReview=True, traceId=trace)
    if status == "insufficient":
        return RagResponse(requestId=request.requestId, status="INSUFFICIENT_EVIDENCE", riskLevel=risk,
                           summary="当前报告中没有足够的可检索资料回答这个问题。",
                           citations=[], warnings=["请补充资料并向医生确认。"],
                           needsReview=True, traceId=trace)
    citations = build_citations(request, hits)
    context = build_context(request, hits)
    summary = generate_summary(request, context, generator) if generator else \
        "以下内容是基于报告片段的健康辅助解释，不能替代医生诊断。"
    return RagResponse(requestId=request.requestId, status="OK", riskLevel=risk,
                       summary=summary, citations=citations,
                       warnings=["请将报告原文和本解释一并交由医生确认。"],
                       needsReview=(risk == "MEDIUM"), traceId=trace)
