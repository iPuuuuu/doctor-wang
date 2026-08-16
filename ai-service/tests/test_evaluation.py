"""评估模块单元测试：JSON 稳健提取、评分回退逻辑。"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "evaluation"))

from run_evaluation import _extract_json, score_coverage  # noqa: E402


class _FakeGenerator:
    def __init__(self, text: str): self.text = text
    def generate(self, question, context, system_prompt=None): return self.text


def test_extract_json_plain():
    assert _extract_json('{"a": 1}') == {"a": 1}


def test_extract_json_embedded_in_prose():
    assert _extract_json('评估结果如下 {"score": 0.85} 完毕') == {"score": 0.85}


def test_extract_json_with_chinese():
    assert _extract_json('{"y": "中文值"}') == {"y": "中文值"}


def test_extract_json_no_json_returns_empty():
    assert _extract_json("没有任何 JSON") == {}


def test_extract_json_malformed_returns_empty():
    assert _extract_json('{"a": }') == {}


def test_score_coverage_uses_points_list():
    gen = _FakeGenerator('{"covered_points": [true, false], "covered_count": 1}')
    result = score_coverage(gen, "回答", ["证据1", "证据2"])
    assert result["coverage"] == 0.5
    assert result["covered"] == 1


def test_score_coverage_falls_back_to_count():
    gen = _FakeGenerator('{"covered_count": 2}')
    result = score_coverage(gen, "回答", ["证据1", "证据2", "证据3"])
    assert result["coverage"] == round(2 / 3, 3)
    assert result["covered"] == 2


def test_score_coverage_empty_golden():
    gen = _FakeGenerator("{}")
    result = score_coverage(gen, "回答", [])
    assert result["coverage"] is None


def test_dataset_schema_valid():
    """评测集结构校验：每个用例含必填字段且引用存在的报告。"""
    import json
    from pathlib import Path
    ds = json.loads(Path("evaluation/dataset.json").read_text(encoding="utf-8"))
    assert len(ds) >= 30
    for case in ds:
        for key in ("id", "question", "reportId", "namespace", "golden", "expectDecline"):
            assert key in case, f"{case.get('id')} 缺 {key}"
        assert case["namespace"] == f"report:1:{case['reportId']}", case["id"]
        assert isinstance(case["expectDecline"], bool)
        assert isinstance(case["golden"], list)
    ids = [c["id"] for c in ds]
    assert len(ids) == len(set(ids)), "用例 ID 重复"
