"""混合检索：字符级词法相似（近似 BM25，中文无需分词依赖）+ 向量 + RRF 融合。

医疗报告多为短文本且词汇专业，纯向量检索可能漏掉关键词精确匹配；
词法通道对检查项目名、术语（"未见占位""肋膈角锐利"）有强召回，
RRF 融合取两通道排序之并，兼顾语义与字面。
"""
from __future__ import annotations

import re

FUSION_K = 60


def char_bigrams(text: str) -> set[str]:
    """去空白后的字符二元组集合（中文检索常用，无需分词器）。"""
    compact = re.sub(r"\s+", "", text or "")
    return {compact[i:i + 2] for i in range(len(compact) - 1)} if compact else set()


def lexical_score(query: str, text: str) -> float:
    """查询在文档上的字符二元组命中率（0~1），近似字面相关度。"""
    q = char_bigrams(query)
    if not q:
        return 0.0
    t = char_bigrams(text)
    return len(q & t) / len(q)


def rrf_rank(scores: dict[str, float]) -> dict[str, int]:
    """按分数降序给 rank（0-based），分数并列时稳定。"""
    ordered = sorted(scores.keys(), key=lambda k: scores[k], reverse=True)
    return {cid: i for i, cid in enumerate(ordered)}


def hybrid_rank(vector_scores: dict[str, float], lexical_scores: dict[str, float], top_k: int, k: int = FUSION_K) -> list[str]:
    """RRF 融合两通道排名，返回 top_k 个 chunk_id（按融合分降序）。"""
    vector_ranks = rrf_rank(vector_scores)
    lexical_ranks = rrf_rank(lexical_scores)
    fused: dict[str, float] = {}
    for cid in vector_ranks:
        fused[cid] = fused.get(cid, 0.0) + 1.0 / (k + vector_ranks[cid])
    for cid in lexical_ranks:
        fused[cid] = fused.get(cid, 0.0) + 1.0 / (k + lexical_ranks[cid])
    return sorted(fused, key=fused.get, reverse=True)[:top_k]
