"""Comprehensive RAG pipeline tests: embeddings, chunking, vector store, safety gate, ingestion."""
import base64
import hashlib
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

from app.chunking import chunk_pages, Chunk
from app.embeddings import FakeEmbeddingProvider
from app.main import app, store
from app.parsing import PageText
from app.schemas import RagRequest, IngestionRequest
from app.rag import query_rag
from app.safety import evaluate_medical_risk, risk_triage
from app.vectorstore import InMemoryVectorStore, VectorRecord, _vector_literal


# ── Embedding Provider ──────────────────────────────────────────────

class TestEmbeddingProvider:
    def test_deterministic_same_input_same_vector(self):
        provider = FakeEmbeddingProvider(dimensions=32)
        v1 = provider.embed(["hello world"])[0]
        v2 = provider.embed(["hello world"])[0]
        assert v1 == v2

    def test_different_inputs_different_vectors(self):
        provider = FakeEmbeddingProvider(dimensions=32)
        v1 = provider.embed(["chest pain"])[0]
        v2 = provider.embed(["headache"])[0]
        assert v1 != v2

    def test_batch_embedding_returns_correct_count(self):
        provider = FakeEmbeddingProvider(dimensions=64)
        texts = ["a", "b", "c", "d", "e"]
        vectors = provider.embed(texts)
        assert len(vectors) == 5
        assert all(len(v) == 64 for v in vectors)

    def test_dimensions_match_config(self):
        for dims in [8, 32, 128, 1536]:
            provider = FakeEmbeddingProvider(dimensions=dims)
            vec = provider.embed(["test"])[0]
            assert len(vec) == dims

    def test_unit_norm_vectors(self):
        provider = FakeEmbeddingProvider(dimensions=32)
        vec = provider.embed(["any text"])[0]
        norm_sq = sum(v * v for v in vec)
        assert abs(norm_sq - 1.0) < 1e-9

    def test_empty_string_handled(self):
        provider = FakeEmbeddingProvider(dimensions=32)
        vec = provider.embed([""])[0]
        assert len(vec) == 32

    def test_very_long_text(self):
        provider = FakeEmbeddingProvider(dimensions=32)
        long_text = "x" * 10000
        vec = provider.embed([long_text])[0]
        assert len(vec) == 32


# ── Chunking ─────────────────────────────────────────────────────────

class TestChunking:
    def test_single_page_within_max_chars(self):
        chunks = chunk_pages([PageText(1, "short text", 0.95, "text_pdf")])
        assert len(chunks) == 1
        assert chunks[0].text == "short text"
        assert chunks[0].page_no == 1

    def test_large_page_splits_into_multiple_chunks(self):
        chunks = chunk_pages([PageText(1, "a" * 2500, 1.0, "text_pdf")], max_chars=500, overlap=50)
        assert len(chunks) > 3
        for chunk in chunks:
            assert len(chunk.text) <= 500

    def test_chunk_ids_are_unique_with_varied_content(self):
        # Use varied content so sha256-based chunk IDs differ
        text = " ".join(f"word{i}" for i in range(500))
        chunks = chunk_pages([PageText(1, text, 1.0, "text_pdf")], max_chars=50, overlap=10)
        ids = [c.chunk_id for c in chunks]
        assert len(ids) == len(set(ids))

    def test_empty_page_produces_no_chunks(self):
        chunks = chunk_pages([PageText(1, "", 0.0, "text_pdf")])
        assert len(chunks) == 0

    def test_content_hash_is_sha256(self):
        chunks = chunk_pages([PageText(1, "test content", 1.0, "text_pdf")])
        assert len(chunks[0].content_hash) == 64
        assert chunks[0].content_hash == hashlib.sha256("test content".encode()).hexdigest()

    def test_multiple_pages_preserve_page_order(self):
        pages = [
            PageText(1, "page one content here", 0.9, "text_pdf"),
            PageText(2, "page two content here", 0.85, "text_pdf"),
            PageText(3, "page three content", 0.95, "text_pdf"),
        ]
        chunks = chunk_pages(pages, max_chars=10, overlap=1)
        page_nos = [c.page_no for c in chunks]
        assert page_nos == sorted(page_nos)

    def test_overlap_preserves_context(self):
        text = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        chunks = chunk_pages([PageText(1, text, 1.0, "text_pdf")], max_chars=10, overlap=3)
        if len(chunks) > 1:
            # Last few chars of chunk N should appear at start of chunk N+1
            last_chars = chunks[0].text[-3:]
            assert last_chars in chunks[1].text


# ── Vector Store ─────────────────────────────────────────────────────

class TestInMemoryVectorStore:
    def test_upsert_and_search(self):
        store = InMemoryVectorStore()
        vec = [1.0, 0.0, 0.0]
        store.upsert([VectorRecord("c1", "ns", vec, "text1", {"page_no": 1})])
        results = store.search("ns", [0.9, 0.0, 0.1], top_k=1)
        assert len(results) == 1
        assert results[0][0].chunk_id == "c1"

    def test_namespace_isolation(self):
        store = InMemoryVectorStore()
        store.upsert([VectorRecord("c1", "ns-a", [1.0, 0.0], "a", {})])
        store.upsert([VectorRecord("c2", "ns-b", [0.0, 1.0], "b", {})])
        results_a = store.search("ns-a", [1.0, 0.0], top_k=5)
        results_b = store.search("ns-b", [0.0, 1.0], top_k=5)
        assert all(r[0].namespace == "ns-a" for r in results_a)
        assert all(r[0].namespace == "ns-b" for r in results_b)

    def test_delete_namespace(self):
        store = InMemoryVectorStore()
        store.upsert([VectorRecord("c1", "ns", [1.0], "text", {})])
        assert len(store.search("ns", [1.0], 1)) == 1
        store.delete_namespace("ns")
        assert len(store.search("ns", [1.0], 1)) == 0

    def test_search_returns_ordered_by_similarity(self):
        store = InMemoryVectorStore()
        store.upsert([
            VectorRecord("c1", "ns", [1.0, 0.0], "exact", {}),
            VectorRecord("c2", "ns", [0.5, 0.5], "partial", {}),
            VectorRecord("c3", "ns", [-1.0, 0.0], "opposite", {}),
        ])
        results = store.search("ns", [1.0, 0.0], top_k=3)
        scores = [s for _, s in results]
        assert scores == sorted(scores, reverse=True)
        assert results[0][0].chunk_id == "c1"

    def test_top_k_truncation(self):
        store = InMemoryVectorStore()
        for i in range(10):
            store.upsert([VectorRecord(f"c{i}", "ns", [float(i), 0.0], f"text{i}", {})])
        results = store.search("ns", [5.0, 0.0], top_k=3)
        assert len(results) == 3


# ── Vector Literal Formatting ────────────────────────────────────────

class TestVectorLiteral:
    def test_format_3d_vector(self):
        lit = _vector_literal([1.0, -0.5, 0.0])
        assert lit.startswith("[")
        assert lit.endswith("]")
        parts = lit[1:-1].split(",")
        assert len(parts) == 3

    def test_empty_vector(self):
        lit = _vector_literal([])
        assert lit == "[]"


# ── Medical Safety Gate ──────────────────────────────────────────────

class TestMedicalSafetyGate:
    def test_detects_chest_pain_cn(self):
        risk, warnings, blocked = evaluate_medical_risk("我胸痛")
        assert risk == "HIGH"
        assert blocked is True

    def test_detects_breathing_difficulty_cn(self):
        risk, warnings, blocked = evaluate_medical_risk("我呼吸困难")
        assert risk == "HIGH"
        assert blocked is True

    def test_detects_unconsciousness(self):
        risk, warnings, blocked = evaluate_medical_risk("病人意识改变")
        assert risk == "HIGH"
        assert blocked is True

    def test_detects_severe_bleeding(self):
        risk, warnings, blocked = evaluate_medical_risk("严重出血怎么处理")
        assert risk == "HIGH"
        assert blocked is True

    def test_detects_self_harm(self):
        risk, warnings, blocked = evaluate_medical_risk("自伤行为")
        assert risk == "HIGH"
        assert blocked is True

    def test_detects_chest_pain_en(self):
        risk, _, blocked = evaluate_medical_risk("I have chest pain")
        assert risk == "HIGH"
        assert blocked is True

    def test_detects_suicidal_en(self):
        risk, _, blocked = evaluate_medical_risk("feeling suicidal")
        assert risk == "HIGH"
        assert blocked is True

    def test_low_risk_general_question(self):
        risk, warnings, blocked = evaluate_medical_risk("我头疼怎么办")
        assert risk == "LOW"
        assert blocked is False
        assert len(warnings) > 0
        assert any("不构成诊断" in w or "参考" in w for w in warnings)

    def test_risk_triage_case_insensitive(self):
        assert risk_triage("胸痛") == "HIGH"
        assert risk_triage("CHEST PAIN") == "HIGH"
        assert risk_triage("头疼") == "LOW"

    def test_normal_health_question_not_flagged(self):
        risk, _, blocked = evaluate_medical_risk("我今天吃了什么会过敏")
        assert risk == "LOW"
        assert blocked is False

    def test_medium_risk_detected(self):
        risk, warnings, blocked = evaluate_medical_risk("我持续高烧三天了")
        assert risk == "MEDIUM"
        assert blocked is False
        assert any("就医" in w for w in warnings)

    def test_medium_risk_not_blocked(self):
        risk, warnings, blocked = evaluate_medical_risk("持续头痛怎么办")
        assert risk == "MEDIUM"
        assert blocked is False

    def test_jaundice_is_medium_risk(self):
        risk, _, _ = evaluate_medical_risk("我皮肤发黄，是不是黄疸")
        assert risk == "MEDIUM"

    def test_unconscious_is_high_risk(self):
        risk, _, blocked = evaluate_medical_risk("病人昏迷不醒")
        assert risk == "HIGH"
        assert blocked is True

    def test_seizure_is_high_risk(self):
        risk, _, blocked = evaluate_medical_risk("seizure symptoms")
        assert risk == "HIGH"
        assert blocked is True


# ── RAG Query Edge Cases ─────────────────────────────────────────────

class TestRagQueryEdgeCases:
    def test_empty_question(self):
        # Empty question passes through rag query (schema validation is at HTTP layer)
        req = RagRequest(requestId="r", userId=1, reportId=2, question=" ",
                         namespaceHints=["report:1:2"], traceId="t")
        response = query_rag(req, store, FakeEmbeddingProvider())
        assert response.status in ("INSUFFICIENT_EVIDENCE", "SAFETY_BLOCKED", "OK")

    def test_very_long_question(self):
        req = RagRequest(requestId="r", userId=1, reportId=2, question="a" * 3900,
                         namespaceHints=["report:1:2"], traceId="t")
        response = query_rag(req, store, FakeEmbeddingProvider())
        assert response.traceId == "t"

    def test_single_namespace_hint(self):
        req = RagRequest(requestId="r", userId=1, reportId=2, question="test",
                         namespaceHints=["report:1:2"], traceId="t")
        response = query_rag(req, store, FakeEmbeddingProvider())
        assert response.status in ("INSUFFICIENT_EVIDENCE", "SAFETY_BLOCKED")

    def test_response_contains_disclaimer(self):
        # Pre-populate store with evidence
        store.upsert([VectorRecord("evidence-1", "report:99:99", [1.0] * 32,
                                    "The patient has mild fatty liver.",
                                    {"page_no": 1, "confidence": 0.9})])
        req = RagRequest(requestId="r", userId=99, reportId=99, question="fatty liver",
                         namespaceHints=["report:99:99"], traceId="t")
        response = query_rag(req, store, FakeEmbeddingProvider(dimensions=32))
        assert any("不能替代" in c.excerpt or "不能替代" in response.summary for c in response.citations) or response.needsReview


# ── Ingestion HTTP Endpoints ─────────────────────────────────────────

class TestIngestionEndpoints:
    def test_ingest_with_base64_pdf(self, monkeypatch, tmp_path):
        from reportlab.pdfgen import canvas
        from app.config import settings
        object_root = tmp_path / "objects"
        object_root.mkdir()
        pdf = object_root / "b64.pdf"
        writer = canvas.Canvas(str(pdf))
        writer.drawString(72, 720, "Blood test results show normal values.")
        writer.save()
        monkeypatch.setattr(settings, "object_store_root", str(object_root))
        pdf_data = pdf.read_bytes()

        client = TestClient(app)
        payload = {
            "requestId": "b64-test", "reportId": 1, "userId": 1,
            "namespace": "report:1:1", "fileName": "test.pdf",
            "contentType": "application/pdf", "objectKey": "b64.pdf",
            "fileBase64": base64.b64encode(pdf_data).decode(),
            "traceId": "trace-1",
        }
        response = client.post("/internal/v1/ingestions", json=payload)
        assert response.status_code == 202
        assert "jobId" in response.json()

    def test_ingest_missing_file_returns_error(self):
        client = TestClient(app)
        payload = {"requestId": "no-file", "reportId": 1, "userId": 1,
                   "namespace": "report:1:1", "fileName": "x.pdf",
                   "contentType": "application/pdf", "traceId": "t"}
        response = client.post("/internal/v1/ingestions", json=payload)
        assert response.status_code == 422

    def test_ingest_invalid_base64(self):
        client = TestClient(app)
        payload = {"requestId": "bad-b64", "reportId": 1, "userId": 1,
                   "namespace": "report:1:1", "fileName": "x.pdf",
                   "contentType": "application/pdf",
                   "fileBase64": "!!!not-valid-base64!!!", "traceId": "t"}
        response = client.post("/internal/v1/ingestions", json=payload)
        assert response.status_code == 422

    def test_job_status_nonexistent(self):
        client = TestClient(app)
        response = client.get("/internal/v1/ingestions/nonexistent-job-id")
        assert response.status_code == 404

    def test_delete_index_and_verify_empty(self):
        store.upsert([VectorRecord("c-del", "report:1:99", [1.0] * 32, "del", {})])
        client = TestClient(app)
        response = client.delete("/internal/v1/indexes/report:1:99")
        assert response.status_code == 200
        assert response.json()["status"] == "DELETED"
        assert store.search("report:1:99", [1.0] * 32, 1) == []

    def test_retry_non_retryable_job(self, monkeypatch, tmp_path):
        from reportlab.pdfgen import canvas
        from app.config import settings
        object_root = tmp_path / "objects"
        object_root.mkdir()
        pdf = object_root / "retry.pdf"
        writer = canvas.Canvas(str(pdf))
        writer.drawString(72, 720, "Test report with sufficient text content for ingestion.")
        writer.save()
        monkeypatch.setattr(settings, "object_store_root", str(object_root))

        client = TestClient(app)
        payload = {
            "requestId": "retry-test", "reportId": 55, "userId": 55,
            "namespace": "report:55:55", "fileName": "r.pdf",
            "contentType": "application/pdf", "objectKey": "retry.pdf",
            "fileBase64": base64.b64encode(pdf.read_bytes()).decode(),
            "traceId": "tr",
        }
        ingest_resp = client.post("/internal/v1/ingestions", json=payload)
        assert ingest_resp.status_code == 202
        job_id = ingest_resp.json()["jobId"]
        retry_resp = client.post(f"/internal/v1/ingestions/{job_id}/retry")
        assert retry_resp.status_code == 409

    def test_authenticated_endpoint_requires_valid_token(self, monkeypatch):
        from app.config import settings
        monkeypatch.setattr(settings, "internal_token", "secret-token")
        client = TestClient(app)
        # Minimal valid body to pass pydantic validation before auth check
        body = {"requestId": "x", "reportId": 1, "userId": 1,
                "namespace": "report:1:1", "fileName": "x.pdf",
                "contentType": "application/pdf", "objectKey": "x.pdf",
                "traceId": "t"}
        # No token → should be rejected
        response = client.post("/internal/v1/ingestions", json=body)
        assert response.status_code == 401
        # Wrong token
        response = client.post("/internal/v1/ingestions", json=body,
                               headers={"X-Internal-Token": "wrong"})
        assert response.status_code == 401


# ── End-to-End: Upload → Ingest → Query ──────────────────────────────

class TestEndToEndRag:
    def test_ingest_pdf_then_query(self, monkeypatch, tmp_path):
        from reportlab.pdfgen import canvas
        from app.config import settings

        object_root = tmp_path / "objects"
        object_root.mkdir()
        pdf_path = object_root / "e2e.pdf"
        writer = canvas.Canvas(str(pdf_path))
        writer.drawString(72, 720, "Patient shows elevated ALT and AST levels. Fatty liver grade I.")
        writer.save()
        monkeypatch.setattr(settings, "object_store_root", str(object_root))

        client = TestClient(app)
        payload = {
            "requestId": "e2e", "reportId": 100, "userId": 100,
            "namespace": "report:100:100", "fileName": "e2e.pdf",
            "contentType": "application/pdf",
            "objectKey": "e2e.pdf", "traceId": "e2e-trace",
        }
        ingest_resp = client.post("/internal/v1/ingestions", json=payload)
        assert ingest_resp.status_code == 202
        job_id = ingest_resp.json()["jobId"]
        status = client.get(f"/internal/v1/ingestions/{job_id}")
        assert status.json()["status"] == "COMPLETED"

        # Now query
        from app.schemas import RagRequest as RagReq
        query_resp = client.post("/internal/v1/rag/query", json={
            "requestId": "q1", "userId": 100, "reportId": 100,
            "question": "What about the liver?", "namespaceHints": ["report:100:100"],
            "traceId": "q-trace",
        })
        assert query_resp.status_code == 200
        body = query_resp.json()
        assert body["status"] in ("OK", "INSUFFICIENT_EVIDENCE")
        if body["status"] == "OK":
            assert len(body["citations"]) > 0
