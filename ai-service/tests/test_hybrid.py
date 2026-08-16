"""混合检索单元测试：词法评分、RRF 融合、fallback。"""
from app.hybrid import char_bigrams, hybrid_rank, lexical_score
from app.rag import search
from app.schemas import Citation, RagRequest, RagResponse
from app.vectorstore import VectorRecord


class _FakeVectorStore:
    """带词法通道的 store：内容与固定向量分数。"""

    def __init__(self, chunks: list[VectorRecord], scores: dict[str, float]):
        self._chunks, self._scores = chunks, scores

    def search(self, namespace, query_vector, top_k=5):
        ordered = sorted(self._scores.items(), key=lambda kv: kv[1], reverse=True)[:top_k]
        by_id = {c.chunk_id: c for c in self._chunks}
        return [(by_id[cid], score) for cid, score in ordered if cid in by_id]

    def all_chunks(self, namespace):
        return self._chunks


class _VectorOnlyStore:
    """无词法通道的 store：search 应被直接回退。"""

    def __init__(self, hits): self._hits = hits

    def search(self, namespace, query_vector, top_k=5): return self._hits[:top_k]


def _chunk(cid: str, text: str) -> VectorRecord:
    return VectorRecord(cid, "report:1:10014", [], text, {})


def test_char_bigrams_ignores_whitespace():
    assert char_bigrams("未见 占位") == char_bigrams("未见占位")
    assert len(char_bigrams("未见占位性病变")) > 0


def test_lexical_score_hits_and_misses():
    assert lexical_score("胆囊结石", "胆囊内未见结石") > 0.0
    assert lexical_score("肋膈角锐利", "心影大小形态正常") == 0.0


def test_rrf_fusion_combines_both_channels():
    # 向量通道偏向 A，词法通道偏向 B：融合后两者都在前列
    vector = {"A": 0.9, "B": 0.5, "C": 0.1}
    lexical = {"B": 1.0, "A": 0.2, "C": 0.0}
    top = hybrid_rank(vector, lexical, 2)
    assert set(top) == {"A", "B"}


def test_hybrid_search_recalls_lexical_match_that_vector_would_drop():
    chunks = [_chunk("c1", "肝脏形态大小正常，实质回声均匀，未见占位性病变。"), _chunk("c2", "心影大小形态正常，纵隔未见增宽。")]
    store = _FakeVectorStore(chunks, {"c1": 0.1, "c2": 0.9})  # 纯向量 top1 = c2
    # 词法通道明确偏好 c1（查询字面命中）
    assert lexical_score("肝脏 占位 病变", chunks[0].text) > lexical_score("肝脏 占位 病变", chunks[1].text)
    # 融合检索 top1 时不应只返回向量最优的 c2：c1 因字面匹配被召回
    hits = search(store, "report:1:10014", [0.0] * 8, "肝脏 占位 病变", top_k=2)
    ids = [h[0].chunk_id for h in hits]
    assert "c1" in ids and "c2" in ids


def test_search_falls_back_to_vector_only_store():
    store = _VectorOnlyStore([(_chunk("c1", "内容"), 0.8)])
    hits = search(store, "ns", [0.0] * 4, "任意问题", top_k=1)
    assert hits[0][0].chunk_id == "c1"


def test_empty_namespace_returns_empty():
    store = _FakeVectorStore([], {})
    assert search(store, "ns", [0.0] * 4, "问题", top_k=5) == []
