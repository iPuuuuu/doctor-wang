"""Golden evaluation runner — tests the RAG pipeline against known scenarios.

Uses synthetic/de-identified test data only. No real patient data.
"""
import base64
from io import BytesIO

import pytest
from fastapi.testclient import TestClient

from app.embeddings import FakeEmbeddingProvider
from app.main import app, store
from app.rag import query_rag
from app.schemas import RagRequest

from .golden_dataset import ALL_SCENARIOS, GoldenScenario


def _ingest_scenario(client: TestClient, scenario: GoldenScenario, monkeypatch, tmp_path):
    """Ingest a scenario's report text into the vector store."""
    if not scenario.report_text.strip():
        return  # skip empty reports

    from reportlab.pdfgen import canvas
    from app.config import settings

    object_root = tmp_path / "objects"
    object_root.mkdir()
    pdf_path = object_root / f"{scenario.scenario_id}.pdf"
    writer = canvas.Canvas(str(pdf_path))
    y = 720
    for line in scenario.report_text.strip().split("\n"):
        line = line.strip()
        if line:
            writer.drawString(72, y, line[:120])  # truncate long lines for PDF
            y -= 14
    writer.save()

    monkeypatch.setattr(settings, "object_store_root", str(object_root))

    pdf_data = pdf_path.read_bytes()
    # Use a fixed user/report ID per scenario
    rid = abs(hash(scenario.scenario_id)) % 10000
    uid = rid

    payload = {
        "requestId": f"eval-{scenario.scenario_id}",
        "reportId": rid,
        "userId": uid,
        "namespace": f"report:{uid}:{rid}",
        "fileName": f"{scenario.scenario_id}.pdf",
        "contentType": "application/pdf",
        "objectKey": f"{scenario.scenario_id}.pdf",
        "fileBase64": base64.b64encode(pdf_data).decode(),
        "traceId": f"eval-trace-{scenario.scenario_id}",
    }
    response = client.post("/internal/v1/ingestions", json=payload)
    assert response.status_code == 202, f"Ingestion failed: {response.text}"
    return uid, rid


class TestGoldenEvaluation:
    """End-to-end evaluation using golden scenarios.

    Each scenario is ingested, then each query is evaluated against expected outcomes.
    """

    @pytest.mark.parametrize("scenario", ALL_SCENARIOS, ids=lambda s: s.scenario_id)
    def test_scenario_ingestion_and_query(self, scenario, monkeypatch, tmp_path):
        client = TestClient(app)
        result = _ingest_scenario(client, scenario, monkeypatch, tmp_path)

        if result is None:
            # Empty report — skip query tests
            return

        uid, rid = result

        for i, query in enumerate(scenario.queries):
            req = RagRequest(
                requestId=f"q-{scenario.scenario_id}-{i}",
                userId=uid,
                reportId=rid,
                question=query.question,
                namespaceHints=[f"report:{uid}:{rid}"],
                traceId=f"qt-{scenario.scenario_id}-{i}",
            )
            response = query_rag(req, store, FakeEmbeddingProvider(dimensions=32))

            # Check status
            assert response.status == query.expected_status, (
                f"[{scenario.scenario_id}] Q: '{query.question}' — "
                f"expected status={query.expected_status}, got {response.status}"
            )

            # Check risk level
            assert response.riskLevel == query.expected_risk_level, (
                f"[{scenario.scenario_id}] Q: '{query.question}' — "
                f"expected risk={query.expected_risk_level}, got {response.riskLevel}"
            )

            # Check citations
            if query.should_have_citations:
                assert len(response.citations) >= query.min_citation_count, (
                    f"[{scenario.scenario_id}] Q: '{query.question}' — "
                    f"expected >= {query.min_citation_count} citations, got {len(response.citations)}"
                )
            else:
                assert len(response.citations) == 0, (
                    f"[{scenario.scenario_id}] Q: '{query.question}' — "
                    f"expected no citations, got {len(response.citations)}"
                )

            # Check warnings
            if query.should_have_warnings:
                assert len(response.warnings) > 0, (
                    f"[{scenario.scenario_id}] Q: '{query.question}' — expected warnings"
                )

            # Key terms in citations: skip with fake embeddings (vectors are not semantic)
            # Real embeddings needed for meaningful term-level assertion
