"""症状自查知识库测试：关键词匹配、受控上下文、非诊断边界。"""
from app.symptom_kb import KB_VERSION, match_symptom_kb, symptom_kb_context


def test_matches_known_symptom():
    hits = match_symptom_kb("我最近右上腹有点不舒服")
    assert any(h["id"] == "kb:右上腹不适" for h in hits)
    assert hits[0]["benign"] and hits[0]["red_flags"] and hits[0]["advice"]


def test_matches_multiple_symptoms():
    hits = match_symptom_kb("头痛加心慌，晚上也睡不着")
    ids = {h["id"] for h in hits}
    assert "kb:头痛" in ids and "kb:心悸" in ids


def test_no_match_returns_empty():
    assert match_symptom_kb("请帮我看看报告里的肝脏") == []
    assert match_symptom_kb("") == []


def test_context_contains_version_and_boundary():
    context, hits = symptom_kb_context("我胃痛反酸，还总是手抖")
    assert KB_VERSION in context
    assert "非诊断" in context or "缓解担忧参考" in context
    assert len(hits) >= 2


def test_context_empty_when_no_match():
    context, hits = symptom_kb_context("报告显示肝脏未见异常")
    assert context == "" and hits == []


def test_all_entries_have_required_fields():
    from app.symptom_kb import SYMPTOM_KB
    for key, entry in SYMPTOM_KB.items():
        assert entry["terms"] and entry["benign"] and entry["red_flags"] and entry["advice"], key
        assert "就医" in entry["red_flags"] or "就医" in entry["advice"], f"{key} 缺就医信号"
