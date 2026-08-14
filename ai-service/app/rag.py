from .embeddings import EmbeddingProvider
from .schemas import Citation, RagResponse
from .safety import risk_triage


def query_rag(request, store, embedder: EmbeddingProvider, generator=None) -> RagResponse:
    namespace = f"report:{request.userId}:{request.reportId}"
    if namespace not in request.namespaceHints:
        return RagResponse(requestId=request.requestId, status="SAFETY_BLOCKED", riskLevel="HIGH", summary="未授权的报告空间。", citations=[], warnings=["请求未通过报告归属校验。"], needsReview=True, traceId=request.traceId)
    risk = risk_triage(request.question)
    if risk == "HIGH":
        return RagResponse(requestId=request.requestId, status="SAFETY_BLOCKED", riskLevel="HIGH",
                           summary="当前问题可能涉及紧急危险信号，请立即联系急救服务（120）或尽快就医。",
                           citations=[], warnings=["AI 健康辅助不能替代急诊或医生判断。"],
                           needsReview=True, traceId=request.traceId)
    hits = store.search(namespace, embedder.embed([request.question])[0], request.topK)
    if not hits:
        return RagResponse(requestId=request.requestId, status="INSUFFICIENT_EVIDENCE", riskLevel=risk,
                           summary="当前报告中没有足够的可检索资料回答这个问题。",
                           citations=[], warnings=["请补充资料并向医生确认。"],
                           needsReview=True, traceId=request.traceId)
    citations = [Citation(sourceId=str(request.reportId), chunkId=r.chunk_id,
                          pageNo=r.metadata.get("page_no"), excerpt=r.text,
                          confidence=r.metadata.get("confidence", 0.0),
                          documentVersion=r.metadata.get("document_version", "unknown"))
                 for r, _ in hits]
    summary = "以下内容是基于报告片段的健康辅助解释，不能替代医生诊断。"
    if generator:
        try:
            summary = generator.generate(request.question, "\n".join(c.excerpt for c in citations))
        except Exception:
            summary = "模型服务暂时不可用；请根据报告原文向医生确认。"
    return RagResponse(requestId=request.requestId, status="OK", riskLevel=risk,
                       summary=summary, citations=citations,
                       warnings=["请将报告原文和本解释一并交由医生确认。"],
                       needsReview=(risk == "MEDIUM"), traceId=request.traceId)
