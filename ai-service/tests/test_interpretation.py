"""报告解读解析（Python 版）测试：三段提取、容错、兜底。"""
from app.interpretation import FALLBACK_SUMMARY, parse_interpretation


def test_parses_structured():
    r = parse_interpretation("【摘要】肝脏未见异常。\n【重点发现】1. 实质回声均匀\n2. 未见占位\n【建议】1. 定期复查")
    assert "肝脏未见异常" in r.summary
    assert r.findings == ["实质回声均匀", "未见占位"]
    assert r.suggestions == ["定期复查"]


def test_missing_sections_tolerated():
    r = parse_interpretation("【摘要】只有摘要")
    assert r.findings == [] and r.suggestions == []


def test_no_summary_marker_whole_text_as_summary():
    r = parse_interpretation("模型没按格式输出")
    assert r.summary == "模型没按格式输出"


def test_blank_falls_back():
    r = parse_interpretation("   ")
    assert r.summary == FALLBACK_SUMMARY
