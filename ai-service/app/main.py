import base64
import os
import uuid
from fastapi import FastAPI, Header, HTTPException
from .config import settings
from .schemas import IngestionRequest, IngestionResponse, RagRequest, RagResponse
from .parsing import DocumentTypeRouter
from .chunking import chunk_pages
from .embeddings import FakeEmbeddingProvider
from .vectorstore import InMemoryVectorStore, VectorRecord, create_vector_store
from .graph import build_rag_graph
from .jobs import JobRepository
from .providers import DashScopeOcrProvider, OpenAICompatibleEmbeddingProvider, OpenAICompatibleGenerationProvider
from .safety import evaluate_medical_risk
from .agent_schemas import AgentRequest, AgentResponse
from .agent_graph import agent_graph, AgentState

app = FastAPI(title="Doctor-Wang AI Service", version="0.2.0")
store = create_vector_store(settings.database_url)
embedder = OpenAICompatibleEmbeddingProvider(settings.embedding_endpoint, settings.embedding_api_key, settings.embedding_model, settings.embedding_dimensions, settings.provider_timeout_seconds) if settings.embedding_endpoint else FakeEmbeddingProvider(settings.embedding_dimensions, settings.embedding_model)
generator = OpenAICompatibleGenerationProvider(settings.llm_endpoint, settings.llm_api_key, settings.llm_model, settings.provider_timeout_seconds) if settings.llm_endpoint else None
ocr = DashScopeOcrProvider(settings.ocr_endpoint, settings.ocr_api_key, settings.ocr_model, settings.provider_timeout_seconds) if settings.ocr_endpoint else None

# 面向用户的服务不允许静默降级：缺真实 provider 时醒目告警（生产必须配置 DW_AI_*）
if settings.embedding_endpoint is None or settings.llm_endpoint is None:
    import logging as _logging
    _logging.getLogger("uvicorn.error").warning(
        "AI SERVICE RUNNING WITHOUT REAL PROVIDERS: set DW_AI_EMBEDDING_* / DW_AI_LLM_* "
        "(and DW_AI_OCR_* for image reports) in production."
    )
router = DocumentTypeRouter(image=ocr) if ocr else DocumentTypeRouter()
rag_graph = build_rag_graph()
job_repo = JobRepository(settings.job_state_path)


def authenticate(token: str | None):
    if settings.internal_token and token != settings.internal_token:
        raise HTTPException(status_code=401, detail="unauthorized")


@app.get("/healthz")
def healthz(): return {"status": "ok"}


@app.post("/internal/v1/ingestions", response_model=IngestionResponse, status_code=202)
def ingest(request: IngestionRequest, x_internal_token: str | None = Header(default=None)):
    authenticate(x_internal_token)
    if request.fileBase64:
        try: data = base64.b64decode(request.fileBase64, validate=True)
        except ValueError as exc: raise HTTPException(status_code=422, detail="invalid fileBase64") from exc
    elif request.objectKey:
        root, requested = os.path.realpath(settings.object_store_root), os.path.realpath(os.path.join(settings.object_store_root, request.objectKey))
        if not requested.startswith(root + os.sep): raise HTTPException(status_code=422, detail="invalid objectKey")
        try:
            with open(requested, "rb") as source: data = source.read()
        except OSError as exc: raise HTTPException(status_code=404, detail="object not found") from exc
    else:
        raise HTTPException(status_code=422, detail="fileBase64 or objectKey is required")
    try:
        pages = router.extract(data, request.contentType)
        if sum(len(page.text) for page in pages) < settings.extraction_min_chars:
            job_id = str(uuid.uuid4()); payload = {"status": "REVIEW_REQUIRED", "reportId": request.reportId, "traceId": request.traceId, "requiresReview": True}
        else:
            chunks = chunk_pages(pages)
            vectors = embedder.embed([chunk.text for chunk in chunks])
            store.delete_namespace(request.namespace)
            store.upsert([VectorRecord(chunk.chunk_id, request.namespace, vector, chunk.text, {"page_no": chunk.page_no, "confidence": chunk.confidence, "document_version": chunk.document_version if hasattr(chunk, "document_version") else request.sourceVersion}) for chunk, vector in zip(chunks, vectors)])
            job_id = str(uuid.uuid4()); payload = {"status": "COMPLETED", "reportId": request.reportId, "traceId": request.traceId, "requiresReview": False, "chunkCount": len(chunks)}
    except NotImplementedError:
        job_id = str(uuid.uuid4()); payload = {"status": "REVIEW_REQUIRED", "reportId": request.reportId, "traceId": request.traceId, "requiresReview": True}
    except Exception as exc:
        raise HTTPException(status_code=422, detail=f"document processing failed: {type(exc).__name__}") from exc
    # Persist replay metadata only; never persist report bytes, OCR text,
    # prompts, tokens, or credentials in job state.
    payload["request"] = request.model_dump(exclude={"fileBase64"})
    job_repo.save(job_id, payload)
    return IngestionResponse(jobId=job_id, status=payload["status"])


@app.get("/internal/v1/ingestions/{job_id}")
def ingestion_status(job_id: str, x_internal_token: str | None = Header(default=None)):
    authenticate(x_internal_token)
    payload = job_repo.get(job_id)
    if not payload: raise HTTPException(status_code=404, detail="job not found")
    return {"jobId": job_id, **payload}


@app.post("/internal/v1/ingestions/{job_id}/retry", response_model=IngestionResponse, status_code=202)
def ingestion_retry(job_id: str, x_internal_token: str | None = Header(default=None)):
    authenticate(x_internal_token)
    payload = job_repo.get(job_id)
    if not payload:
        raise HTTPException(status_code=404, detail="job not found")
    if payload.get("status") not in {"FAILED", "REVIEW_REQUIRED"}:
        raise HTTPException(status_code=409, detail="job is not retryable")
    request_data = payload.get("request")
    if not request_data:
        raise HTTPException(status_code=409, detail="retry metadata is unavailable")
    return ingest(IngestionRequest.model_validate(request_data), x_internal_token)


@app.post("/internal/v1/rag/query", response_model=RagResponse)
def rag_query(request: RagRequest, x_internal_token: str | None = Header(default=None)):
    authenticate(x_internal_token)
    risk_level, warnings, blocked = evaluate_medical_risk(request.question)
    if blocked:
        return RagResponse(requestId=request.requestId, status="SAFETY_BLOCKED", riskLevel=risk_level, summary="当前问题可能涉及紧急危险信号，请立即联系急救服务或尽快就医。", citations=[], warnings=warnings, needsReview=True, traceId=request.traceId)
    return rag_graph.invoke({"request": request, "store": store, "embedder": embedder, "generator": generator})["response"]


@app.post("/internal/v1/agent/query", response_model=AgentResponse)
def agent_query(request: AgentRequest, x_internal_token: str | None = Header(default=None)):
    authenticate(x_internal_token)
    state: AgentState = {"request": request, "store": store, "embedder": embedder, "generator": generator}
    result = agent_graph.invoke(state)
    return result["response"]


@app.delete("/internal/v1/indexes/{namespace}")
def delete_index(namespace: str, x_internal_token: str | None = Header(default=None)):
    authenticate(x_internal_token); store.delete_namespace(namespace); return {"status": "DELETED", "namespace": namespace}
