import base64
from types import SimpleNamespace
import pytest
from fastapi.testclient import TestClient
from app.chunking import chunk_pages
from app.embeddings import FakeEmbeddingProvider
from app.main import app, store
from app.parsing import PageText
from app.schemas import RagRequest
from app.rag import query_rag
from app.vectorstore import VectorRecord
from app.jobs import JobRepository
from app.safety import evaluate_medical_risk


def test_chunk_preserves_page_and_hash():
    chunks = chunk_pages([PageText(2, "a" * 1000, 1.0, "text_pdf")], max_chars=100, overlap=10)
    assert len(chunks) > 1
    assert all(c.page_no == 2 and c.content_hash for c in chunks)


def test_rag_requires_authorized_namespace():
    req = RagRequest(requestId="r", userId=1, reportId=2, question="what?", namespaceHints=["report:9:2"], traceId="t")
    response = query_rag(req, store, FakeEmbeddingProvider())
    assert response.status == "SAFETY_BLOCKED"


def test_rag_high_risk_is_deterministic():
    req = RagRequest(requestId="r", userId=1, reportId=2, question="我有胸痛", namespaceHints=["report:1:2"], traceId="t")
    response = query_rag(req, store, FakeEmbeddingProvider())
    assert response.riskLevel == "HIGH"
    assert not response.citations


def test_rag_without_evidence_does_not_fabricate():
    req = RagRequest(requestId="r", userId=1, reportId=2, question="what?", namespaceHints=["report:1:2"], traceId="t")
    response = query_rag(req, store, FakeEmbeddingProvider())
    assert response.status == "INSUFFICIENT_EVIDENCE"
    assert response.citations == []


def test_health_endpoint():
    client = TestClient(app)
    assert client.get("/healthz").json() == {"status": "ok"}


def test_job_state_survives_repository_reopen(tmp_path):
    path = str(tmp_path / "jobs.sqlite3")
    first = JobRepository(path)
    first.save("job-1", {"status": "COMPLETED", "reportId": 7})
    second = JobRepository(path)
    assert second.get("job-1")["status"] == "COMPLETED"


def test_pgvector_contract_rejects_unsafe_table_name():
    from app.vectorstore import PgVectorStore
    with pytest.raises(ValueError):
        PgVectorStore(object(), "vectors;drop table users")


def test_object_key_ingests_text_pdf(tmp_path, monkeypatch):
    from reportlab.pdfgen import canvas
    from app.config import settings
    object_root = tmp_path / "objects"
    object_root.mkdir()
    pdf = object_root / "report.pdf"
    writer = canvas.Canvas(str(pdf))
    writer.drawString(72, 720, "Hemoglobin value is included in this report.")
    writer.save()
    monkeypatch.setattr(settings, "object_store_root", str(object_root))
    client = TestClient(app)
    payload = {"requestId": "object-key", "reportId": 22, "userId": 11, "namespace": "report:11:22", "fileName": "report.pdf", "contentType": "application/pdf", "objectKey": "report.pdf", "traceId": "trace-object"}
    response = client.post("/internal/v1/ingestions", json=payload)
    assert response.status_code == 202
    assert response.json()["status"] == "COMPLETED"
    status = client.get(f"/internal/v1/ingestions/{response.json()['jobId']}")
    assert status.json()["chunkCount"] >= 1


def test_high_risk_question_returns_triage_warning():
    risk_level, warnings, blocked = evaluate_medical_risk("我胸痛并且呼吸困难怎么办")
    assert risk_level == "HIGH"
    assert blocked is True
    assert any("急诊" in warning or "就医" in warning for warning in warnings)
