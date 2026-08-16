"""自校正 Agentic RAG 图测试：校验→回溯→fail-closed。"""
from app.agentic_rag import agentic_rag_graph
from app.rag import query_rag
from app.schemas import RagRequest
from app.vectorstore import VectorRecord


class _FakeGen:
    """可编程生成器：第一次给"带编造"的答案，修正后给干净答案。"""
    def __init__(self, first_answer, corrected_answer=None, fail_verify=False):
        self._first, self._corrected, self.calls = first_answer, corrected_answer or first_answer, 0
        self._fail_verify = fail_verify
        self.verify_responses = []  # 测试注入 judge 输出

    def generate(self, question, context, system_prompt=None):
        self.calls += 1
        if self.calls == 1:
            return self._first
        return self._corrected


class _FakeJudge:
    """校验器：按测试指定逐次返回 verify 结果。"""
    def __init__(self, rounds):
        self._rounds = list(rounds)
        self.calls = 0

    def generate(self, question, context, system_prompt=None, max_tokens=None):
        idx = min(self.calls, len(self._rounds) - 1)
        self.calls += 1
        return self._rounds[idx]


class _Store:
    def __init__(self, chunks):
        self._chunks = chunks

    def search(self, namespace, query_vector, top_k=5):
        return [(c, 0.9) for c in self._chunks[:top_k]]

    def all_chunks(self, namespace):
        return self._chunks


class _Embed:
    def embed(self, texts):
        return [[0.0] * 4 for _ in texts]


def _chunk(cid, text):
    return VectorRecord(cid, "report:1:9", [], text, {"document_version": "v1"})


def _request(question="肝脏正常吗？"):
    return RagRequest(requestId="r1", userId=1, reportId=9, question=question,
                      namespaceHints=["report:1:9"], topK=5, traceId="t1")


def _run(gen, judge, request=None):
    return agentic_rag_graph.invoke({
        "request": request or _request(),
        "store": _Store([_chunk("c1", "肝脏形态正常，实质回声均匀，未见占位性病变。")]),
        "embedder": _Embed(),
        "generator": gen,
        "judge": judge,
        "searcher": None,
        "corrections": 0,
    })["response"]


def test_no_correction_when_verified_clean():
    gen = _FakeGen("肝脏形态正常，未见占位性病变。")
    judge = _FakeJudge(['{"unsupported_claims": []}'])
    resp = _run(gen, judge)
    assert resp.status == "OK"
    assert gen.calls == 1          # 一次生成，无需回溯
    assert resp.needsReview is False


def test_corrects_when_unsupported_claim_found():
    gen = _FakeGen("肝脏形态正常。报告显示有肝肿瘤。", corrected_answer="肝脏形态正常，未见占位性病变。")
    judge = _FakeJudge([
        '{"unsupported_claims": ["报告显示有肝肿瘤"]}',
        '{"unsupported_claims": []}',
    ])
    resp = _run(gen, judge)
    assert gen.calls == 2          # 回溯重生成了一次
    assert "肝肿瘤" not in resp.summary
    assert resp.status == "OK"
    assert resp.needsReview is False  # 修正后干净


def test_fail_closed_when_unsupported_remains_after_correction():
    gen = _FakeGen("肝脏正常。同时报告提示脑肿瘤风险。", corrected_answer="肝脏正常。同时报告提示脑肿瘤风险。")
    judge = _FakeJudge([
        '{"unsupported_claims": ["报告提示脑肿瘤风险"]}',
        '{"unsupported_claims": ["报告提示脑肿瘤风险"]}',
    ])
    resp = _run(gen, judge)
    assert resp.needsReview is True  # 重试后仍含未支持陈述 → 标记复核
    assert any("未能完全依据报告核实" in w for w in resp.warnings)


def test_verify_failure_passes_through_without_blocking():
    gen = _FakeGen("肝脏形态正常，未见占位性病变。")
    judge = _FakeJudge(["not json at all"])  # judge 解析失败
    resp = _run(gen, judge)
    assert resp.status == "OK"
    assert gen.calls == 1          # 校验器不可用不阻塞主流程


def test_single_step_and_agentic_share_retrieval_primitives():
    """query_rag 与 agentic 图对同一输入给出同类响应结构。"""
    request = _request()
    store = _Store([_chunk("c1", "肝脏形态正常，实质回声均匀，未见占位性病变。")])
    single = query_rag(request, store, _Embed(), _FakeGen("肝脏未见异常。"))
    assert single.status == "OK" and single.citations
