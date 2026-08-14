"""Tests for the multi-step health assistant agent."""
import pytest
from fastapi.testclient import TestClient

from app.agent_graph import agent_graph, AgentState
from app.agent_schemas import AgentRequest, AgentResponse
from app.agent_tools import (drug_interaction_check, extract_drugs_from_text,
                               extract_lab_values_from_text, lab_value_lookup,
                               symptom_checker)
from app.embeddings import FakeEmbeddingProvider
from app.main import app, store
from app.vectorstore import InMemoryVectorStore


# ── Tool Tests ────────────────────────────────────────────────────────

class TestSymptomChecker:
    def test_known_symptom_returns_conditions(self):
        result = symptom_checker("我头痛怎么办")
        assert len(result.findings) >= 2  # at least tension headache + migraine
        assert any("头痛" in f for f in result.findings)

    def test_high_risk_symptom_flagged(self):
        result = symptom_checker("我头晕心悸")
        assert result.needsReview
        assert any("HIGH" in f for f in result.findings)

    def test_unknown_symptom_generic_response(self):
        result = symptom_checker("我感觉很奇怪")
        assert len(result.findings) >= 1
        assert result.confidence <= 0.5

    def test_no_symptom_keywords(self):
        result = symptom_checker("我今天天气很好")
        assert "未在已知模式中匹配" in result.findings[0]


class TestLabValueLookup:
    def test_normal_value_in_range(self):
        result = lab_value_lookup("空腹血糖", 5.0)
        assert "在参考范围内" in result.findings[0]
        assert not result.needsReview

    def test_elevated_value_flagged(self):
        result = lab_value_lookup("ALT", 120)
        assert "高于参考范围" in result.findings[0]
        assert "偏高" in result.findings[0]

    def test_critical_value_needs_review(self):
        result = lab_value_lookup("ALT", 250)
        assert result.needsReview

    def test_unknown_lab_name(self):
        result = lab_value_lookup("XYZ", 10)
        assert "未找到" in result.findings[0]
        assert result.confidence == 0.0


class TestDrugInteraction:
    def test_known_interaction_detected(self):
        result = drug_interaction_check(["华法林", "阿司匹林"])
        assert any("出血" in f for f in result.findings)
        assert result.needsReview

    def test_no_interaction(self):
        result = drug_interaction_check(["对乙酰氨基酚"])
        assert "未发现" in result.findings[0]

    def test_high_risk_interaction(self):
        result = drug_interaction_check(["华法林", "布洛芬"])
        assert result.needsReview
        assert any("HIGH" in f for f in result.findings)


class TestLabValueExtraction:
    def test_extracts_alt_from_text(self):
        results = extract_lab_values_from_text("ALT 68 U/L，轻度升高")
        assert ("ALT", 68.0) in results

    def test_extracts_glucose(self):
        results = extract_lab_values_from_text("空腹血糖 7.2 mmol/L")
        assert ("空腹血糖", 7.2) in results

    def test_no_lab_values(self):
        results = extract_lab_values_from_text("我今天感觉很好")
        assert results == []


class TestDrugExtraction:
    def test_extracts_metformin(self):
        drugs = extract_drugs_from_text("继续服用二甲双胍500mg")
        assert "二甲双胍" in drugs

    def test_extracts_multiple(self):
        drugs = extract_drugs_from_text("同时服用华法林和阿司匹林需要注意什么")
        assert "华法林" in drugs
        assert "阿司匹林" in drugs


# ── Agent Graph Tests ─────────────────────────────────────────────────

class TestAgentGraph:
    def test_high_risk_blocked(self):
        req = AgentRequest(requestId="r", userId=1, message="我胸痛呼吸困难",
                           reportIds=[], traceId="t")
        state: AgentState = {
            "request": req, "store": InMemoryVectorStore(),
            "embedder": FakeEmbeddingProvider(), "generator": None,
        }
        result = agent_graph.invoke(state)
        resp = result["response"]
        assert isinstance(resp, AgentResponse)
        assert resp.status == "SAFETY_BLOCKED"
        assert resp.riskLevel == "HIGH"
        assert len(resp.reasoningSteps) >= 1
        assert resp.reasoningSteps[0].stepType == "SAFETY_CHECK"

    def test_symptom_query_routes_correctly(self):
        req = AgentRequest(requestId="r", userId=1, message="我头痛怎么办",
                           reportIds=[], traceId="t")
        state: AgentState = {
            "request": req, "store": InMemoryVectorStore(),
            "embedder": FakeEmbeddingProvider(), "generator": None,
        }
        result = agent_graph.invoke(state)
        resp = result["response"]
        assert resp.status == "OK"
        assert resp.riskLevel == "LOW"
        assert len(resp.reasoningSteps) >= 2  # safety + symptom checker + synthesize

    def test_lab_query_routes_correctly(self):
        req = AgentRequest(requestId="r", userId=1, message="ALT 68 U/L 是什么意思",
                           reportIds=[], traceId="t")
        state: AgentState = {
            "request": req, "store": InMemoryVectorStore(),
            "embedder": FakeEmbeddingProvider(), "generator": None,
        }
        result = agent_graph.invoke(state)
        resp = result["response"]
        assert resp.status == "OK"

    def test_drug_query_routes_correctly(self):
        req = AgentRequest(requestId="r", userId=1, message="二甲双胍和阿司匹林能一起吃吗",
                           reportIds=[], traceId="t")
        state: AgentState = {
            "request": req, "store": InMemoryVectorStore(),
            "embedder": FakeEmbeddingProvider(), "generator": None,
        }
        result = agent_graph.invoke(state)
        resp = result["response"]
        assert resp.status == "OK"

    def test_response_has_conversation_id(self):
        req = AgentRequest(requestId="r", userId=1, message="头痛",
                           reportIds=[], traceId="t")
        state: AgentState = {
            "request": req, "store": InMemoryVectorStore(),
            "embedder": FakeEmbeddingProvider(), "generator": None,
        }
        result = agent_graph.invoke(state)
        assert result["response"].conversationId

    def test_response_has_followup_questions(self):
        req = AgentRequest(requestId="r", userId=1, message="ALT 250 严重吗",
                           reportIds=[], traceId="t")
        state: AgentState = {
            "request": req, "store": InMemoryVectorStore(),
            "embedder": FakeEmbeddingProvider(), "generator": None,
        }
        result = agent_graph.invoke(state)
        assert len(result["response"].followUpQuestions) > 0

    def test_agent_endpoint_http(self):
        client = TestClient(app)
        resp = client.post("/internal/v1/agent/query", json={
            "requestId": "a1", "userId": 1,
            "message": "头痛怎么办", "reportIds": [],
            "traceId": "t",
        })
        assert resp.status_code == 200
        body = resp.json()
        assert body["status"] == "OK"
        assert "conversationId" in body
        assert len(body["reasoningSteps"]) >= 2

    def test_agent_endpoint_blocked(self):
        client = TestClient(app)
        resp = client.post("/internal/v1/agent/query", json={
            "requestId": "a2", "userId": 1,
            "message": "chest pain and shortness of breath",
            "reportIds": [], "traceId": "t",
        })
        assert resp.status_code == 200
        assert resp.json()["status"] == "SAFETY_BLOCKED"
