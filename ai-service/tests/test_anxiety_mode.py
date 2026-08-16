"""安心话聊（anxiety 模式）测试：专用提示词、健康档案注入、安全门拦截。"""
from app.rag import ANXIETY_SYSTEM_PROMPT, query_rag
from app.embeddings import FakeEmbeddingProvider
from app.schemas import RagRequest, Citation
from app.vectorstore import InMemoryVectorStore, VectorRecord


def _store_with_report():
    store = InMemoryVectorStore()
    store.upsert([
        VectorRecord(chunk_id="c1", namespace="report:1:2", vector=[1.0, 0.0],
                     text="体检：血压 150/95mmHg，其余未见异常（脱敏）。", metadata={"page_no": 1, "confidence": 0.9}),
    ])
    return store


def _request(**kw):
    base = dict(requestId="r1", userId=1, reportId=2, question="我最近总觉得头晕，是不是脑子里长了东西？",
                namespaceHints=["report:1:2"], topK=5, traceId="t1", mode="anxiety",
                healthProfile="年龄 30 岁，身高 170cm，体重 65kg，既往史：无。")
    base.update(kw)
    return RagRequest(**base)


class CapturingGenerator:
    def __init__(self): self.calls = []
    def generate(self, question, context, system_prompt=None):
        self.calls.append((question, context, system_prompt))
        return "从你现有的检查结果看，目前没有支持脑部占位的证据；头晕常见于睡眠不足或紧张。若持续加重请就医。"


def test_anxiety_mode_uses_specialized_prompt_and_health_profile():
    gen = CapturingGenerator()
    resp = query_rag(_request(), _store_with_report(), FakeEmbeddingProvider(), gen)
    assert resp.status == "OK"
    assert len(gen.calls) == 1
    _, ctx, sys_prompt = gen.calls[0]
    assert sys_prompt == ANXIETY_SYSTEM_PROMPT
    assert "健康档案" in ctx and "30 岁" in ctx
    assert "报告片段" in ctx


def test_anxiety_mode_default_prompt_for_general_mode():
    gen = CapturingGenerator()
    req = _request(mode="general", healthProfile="")
    resp = query_rag(req, _store_with_report(), FakeEmbeddingProvider(), gen)
    assert resp.status == "OK"
    _, ctx, sys_prompt = gen.calls[0]
    # general 模式：不注入健康档案，使用默认提示词
    assert sys_prompt is None
    assert "健康档案" not in ctx


def test_anxiety_mode_still_blocks_high_risk():
    gen = CapturingGenerator()
    resp = query_rag(_request(question="我突然胸痛呼吸困难，怎么办？"), _store_with_report(), FakeEmbeddingProvider(), gen)
    assert resp.status == "SAFETY_BLOCKED"
    assert resp.riskLevel == "HIGH"
    assert len(gen.calls) == 0  # 拦截时不调用生成器


def test_anxiety_mode_without_report_hits_insufficient_evidence():
    gen = CapturingGenerator()
    empty = InMemoryVectorStore()
    resp = query_rag(_request(), empty, FakeEmbeddingProvider(), gen)
    assert resp.status == "INSUFFICIENT_EVIDENCE"
    assert len(gen.calls) == 0


def test_conversation_history_included_in_context():
    """多轮历史应并入生成上下文（帮助省略式追问）。"""
    seen = {}

    class Gen:
        def generate(self, question, context, system_prompt=None):
            seen["context"] = context
            return "好的。"

    class Req:
        requestId, userId, reportId, question = "r", 1, 2, "那胆红素呢？"
        namespaceHints, topK, traceId, mode, healthProfile = ["report:1:2"], 5, "t", "general", ""
        conversationHistory = "用户: 我的肝功能正常吗？\n助手: 转氨酶正常。\n用户: 那胆红素呢？"

    store = _store_with_report()
    resp = query_rag(Req(), store, FakeEmbeddingProvider(), Gen())
    assert resp.status == "OK"
    assert "对话历史" in seen["context"]
    assert "胆红素" in seen["context"]
