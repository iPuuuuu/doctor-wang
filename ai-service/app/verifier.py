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
    "请检查回答中是否存在【上下文不支持的陈述】（编造、与上下文矛盾、或上下文根本没提到的具体结论）。\n"
    "规则：共情/鼓励/情绪语句不算；明显的边界声明（如'不能替代医生诊断'）不算；\n"
    "基于上下文的合理推断不算。\n"
    f"上下文:\n{{context}}\n\n回答:\n{{answer}}\n\n"
    "输出 JSON: {{\"unsupported_claims\": [\"陈述1\", \"陈述2\"]}}，没有则输出空数组。最多列 3 条。"
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


def verify_claims(generator, answer: str, context: str, max_claims: int = 3) -> dict:
    """返回 {unsupported: [claim...], ok}；judge 解析失败 ok=False（调用方决定透传/标记）。"""
    if not answer or not context:
        return {"unsupported": [], "ok": False}
    try:
        raw = generator.generate("请校验", VERIFY_PROMPT.format(context=context[:4000], answer=answer[:2000]),
                                 system_prompt=VERIFY_SYSTEM, max_tokens=200)
    except Exception:
        return {"unsupported": [], "ok": False}
    data = _extract_json(raw)
    unsupported = data.get("unsupported_claims")
    if not isinstance(unsupported, list):
        return {"unsupported": [], "ok": False}
    cleaned = [str(u).strip() for u in unsupported if str(u).strip()][:max_claims]
    return {"unsupported": cleaned, "ok": True}


def correction_prompt(question: str, context: str, unsupported: list[str]) -> str:
    return CORRECT_PROMPT.format(
        unsupported="\n- ".join(unsupported[:8]) or "（无）",
        context=context[:4000],
        question=question,
    )
