"""声明级忠实度校验器：判断回答中的每个陈述是否被检索上下文支持。

用于自校正 RAG 图（graph.py）的回溯门与评估（evaluation/）的忠实度指标，
是医疗安全"零编造"的自动检查环节。LLM 作为 judge，输出 JSON；解析失败时
按"无法判定"处理（不阻塞主流程，由调用方决定策略）。
"""
from __future__ import annotations

import json
import re

VERIFY_SYSTEM = "你是严格的医疗 RAG 校验员。只输出合法 JSON，不要输出其他文字。"

VERIFY_PROMPT = (
    "请把下面的回答拆分为独立的陈述（事实性断言），并逐条判断是否被给出的上下文支持。\n"
    "规则：上下文没有提到的内容、或与上下文矛盾的内容，都算【不支持】；\n"
    "基于上下文的合理推断不算不支持；明显的边界声明（如'不能替代医生诊断'）不算陈述；\n共情、鼓励、安抚等情绪性语句（如'我理解你的担心'）也不算陈述。\n"
    f"上下文:\n{{context}}\n\n回答:\n{{answer}}\n\n"
    "输出 JSON: {{\"claims\": [{{\"claim\": \"陈述原文\", \"supported\": true/false}}, ...]}}"
)

CORRECT_PROMPT = (
    "你上一轮的回答中包含以下【未经上下文支持的陈述】：\n{unsupported}\n"
    "请重新回答同一个问题。要求：\n"
    "1. 严格只基于提供的来源；来源中没有的内容不要写。\n"
    "2. 保留上一轮回答中已被支持的正确陈述（包括共情、建议、边界声明），不要删减有用信息。\n"
    "3. 只修正或删除被指出的未支持陈述。\n"
    "来源:\n{context}\n问题:{question}"
)


def _extract_json(text: str) -> dict:
    m = re.search(r"\{.*\}", text or "", re.DOTALL)
    if not m:
        return {}
    try:
        return json.loads(m.group(0))
    except json.JSONDecodeError:
        return {}


def verify_claims(generator, answer: str, context: str, max_claims: int = 12) -> dict:
    """返回 {total, unsupported: [claim...], supported, score}；解析失败 total=0。"""
    if not answer or not context:
        return {"total": 0, "unsupported": [], "supported": 0, "score": None}
    try:
        raw = generator.generate("请校验", VERIFY_PROMPT.format(context=context[:4000], answer=answer[:2000]),
                                 system_prompt=VERIFY_SYSTEM)
    except Exception:
        return {"total": 0, "unsupported": [], "supported": 0, "score": None}
    data = _extract_json(raw)
    claims = data.get("claims")
    if not isinstance(claims, list) or not claims:
        return {"total": 0, "unsupported": [], "supported": 0, "score": None}
    claims = claims[:max_claims]
    unsupported = [c.get("claim", "") for c in claims if c.get("supported") is not True and c.get("claim")]
    supported = len(claims) - len(unsupported)
    return {"total": len(claims), "unsupported": unsupported, "supported": supported,
            "score": round(supported / len(claims), 3) if claims else None}


def correction_prompt(question: str, context: str, unsupported: list[str]) -> str:
    return CORRECT_PROMPT.format(
        unsupported="\n- ".join(unsupported[:8]) or "（无）",
        context=context[:4000],
        question=question,
    )
