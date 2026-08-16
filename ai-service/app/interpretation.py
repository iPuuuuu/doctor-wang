"""报告解读解析（Python 版，镜像后端 Kotlin ReportInterpretation.parse，供评估使用）。

输入：LLM 按提示词生成的"【摘要】…【重点发现】…【建议】…"文本；
输出：{summary, findings, suggestions}。容错规则与后端一致：无【摘要】标记时整段作摘要。
"""
from __future__ import annotations

from dataclasses import dataclass, field

SUMMARY_MARK = "【摘要】"
FINDINGS_MARK = "【重点发现】"
SUGGESTIONS_MARK = "【建议】"
FALLBACK_SUMMARY = "报告已完成 AI 解析，可在 AI 问诊或安心话聊中查看基于本报告的解读。本内容仅用于健康辅助，不能替代医生诊断；如有紧急或高风险症状，请及时就医。"


@dataclass
class Interpretation:
    summary: str
    findings: list[str] = field(default_factory=list)
    suggestions: list[str] = field(default_factory=list)


def _section(text: str, start: str, *ends: str | None) -> str | None:
    idx = text.find(start)
    if idx < 0:
        return None
    from_idx = idx + len(start)
    to_idx = len(text)
    for end in ends:
        if end is None:
            continue
        e = text.find(end, from_idx)
        if 0 <= e < to_idx:
            to_idx = e
    return text[from_idx:to_idx].strip()


def _items(sec: str | None, limit: int = 10) -> list[str]:
    if not sec:
        return []
    out = []
    for line in sec.splitlines():
        t = line.strip()
        if not t:
            continue
        cleaned = t
        for pattern in (r"^\d+[.、．]\s*", r"^[-•·]\s*"):
            import re
            cleaned = re.sub(pattern, "", cleaned)
        cleaned = cleaned.strip()
        if cleaned and len(out) < limit:
            out.append(cleaned[:200])
    return out


def parse_interpretation(text: str | None) -> Interpretation:
    raw = (text or "").strip()
    if not raw:
        return Interpretation(FALLBACK_SUMMARY)
    summary = _section(raw, SUMMARY_MARK, FINDINGS_MARK, SUGGESTIONS_MARK)
    if summary is None:
        return Interpretation(raw[:2000])
    findings = _items(_section(raw, FINDINGS_MARK, SUGGESTIONS_MARK, None))
    suggestions = _items(_section(raw, SUGGESTIONS_MARK, None, None))
    cleaned = summary.strip()
    if not cleaned:
        cleaned = FALLBACK_SUMMARY
    if not findings and not suggestions and cleaned == FALLBACK_SUMMARY:
        return Interpretation(FALLBACK_SUMMARY)
    return Interpretation(cleaned[:2000], findings, suggestions)
