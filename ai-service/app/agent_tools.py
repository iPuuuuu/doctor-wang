"""Agent tools: symptom checker, lab value lookup, drug interaction check.

All tools return structured results. Mock implementations for development;
production implementations require real medical knowledge bases.
"""
from dataclasses import dataclass, field

from .agent_schemas import AgentStep, Citation
from .safety import risk_triage


@dataclass
class ToolResult:
    toolName: str
    findings: list[str] = field(default_factory=list)
    citations: list[Citation] = field(default_factory=list)
    confidence: float = 0.0
    needsReview: bool = False


# ── Symptom Checker ──────────────────────────────────────────────────

# Mock knowledge base: symptom → conditions with urgency levels
_SYMPTOM_KB = {
    "头痛": [("紧张性头痛", "LOW"), ("偏头痛", "LOW"), ("高血压相关", "MEDIUM")],
    "头晕": [("良性位置性眩晕", "LOW"), ("贫血", "LOW"), ("颈椎病", "LOW"), ("脑血管问题", "HIGH")],
    "咳嗽": [("上呼吸道感染", "LOW"), ("支气管炎", "LOW"), ("肺炎", "MEDIUM")],
    "腹痛": [("消化不良", "LOW"), ("胃炎", "LOW"), ("阑尾炎", "HIGH"), ("胆囊炎", "MEDIUM")],
    "发热": [("普通感冒", "LOW"), ("流感", "LOW"), ("细菌感染", "MEDIUM"), ("败血症", "HIGH")],
    "关节痛": [("骨关节炎", "LOW"), ("类风湿", "MEDIUM"), ("痛风", "LOW")],
    "心悸": [("焦虑", "LOW"), ("心律失常", "MEDIUM"), ("心肌缺血", "HIGH")],
    "水肿": [("久坐久站", "LOW"), ("肾功能异常", "MEDIUM"), ("心功能不全", "HIGH")],
    "黄疸": [("肝炎", "MEDIUM"), ("胆道梗阻", "MEDIUM"), ("肝硬化", "HIGH")],
}

# Mock lab reference ranges
_LAB_RANGES = {
    "ALT": {"low": 9, "high": 50, "unit": "U/L", "high_risk": 200},
    "AST": {"low": 15, "high": 40, "unit": "U/L", "high_risk": 150},
    "GGT": {"low": 10, "high": 60, "unit": "U/L", "high_risk": 200},
    "总胆红素": {"low": 3.4, "high": 17.1, "unit": "umol/L", "high_risk": 34},
    "空腹血糖": {"low": 3.9, "high": 6.1, "unit": "mmol/L", "high_risk": 11.1},
    "HbA1c": {"low": 4.0, "high": 6.5, "unit": "%", "high_risk": 9.0},
    "肌酐": {"low": 44, "high": 133, "unit": "umol/L", "high_risk": 265},
    "白细胞": {"low": 4.0, "high": 10.0, "unit": "x10^9/L", "high_risk": 20},
    "血小板": {"low": 100, "high": 300, "unit": "x10^9/L", "high_risk": 50},
}

# Mock drug interactions
_DRUG_INTERACTIONS = {
    ("二甲双胍", "酒精"): ("增加乳酸酸中毒风险", "MEDIUM"),
    ("华法林", "阿司匹林"): ("增加出血风险", "HIGH"),
    ("他汀类", "柚子"): ("增加横纹肌溶解风险", "MEDIUM"),
    ("ACEI", "钾补充剂"): ("可能导致高钾血症", "MEDIUM"),
    ("布洛芬", "华法林"): ("增加出血风险", "HIGH"),
}


def symptom_checker(question: str, _report_context: list[str] | None = None) -> ToolResult:
    """Check symptoms against mock knowledge base. Returns possible conditions with urgency."""
    findings = []
    highest_risk = "LOW"
    lowered = question.lower()

    for symptom, conditions in _SYMPTOM_KB.items():
        if symptom in lowered:
            for condition, risk in conditions:
                findings.append(f"{condition} ({risk})")
                if risk == "HIGH":
                    highest_risk = "HIGH"
                elif risk == "MEDIUM" and highest_risk != "HIGH":
                    highest_risk = "MEDIUM"

    if not findings:
        findings.append("症状未在已知模式中匹配，建议线下医生评估")

    return ToolResult(
        toolName="symptom_checker",
        findings=findings,
        confidence=0.5 if findings else 0.1,
        needsReview=highest_risk != "LOW",
    )


def lab_value_lookup(lab_name: str, value: float) -> ToolResult:
    """Look up a lab value against reference ranges. Returns interpretation."""
    ref = _LAB_RANGES.get(lab_name)
    if not ref:
        return ToolResult(
            toolName="lab_value_lookup",
            findings=[f"未找到 {lab_name} 的参考范围"],
            confidence=0.0,
        )

    if value < ref["low"]:
        severity = "严重" if value < ref["low"] * 0.5 else ""
        findings = [f"{lab_name} {value} {ref['unit']} — 低于参考范围 ({ref['low']}-{ref['high']} {ref['unit']}){('，' + severity) if severity else ''}"]
        needs_review = value < ref["low"] * 0.5
    elif value > ref["high"]:
        severity = "严重" if value > ref["high_risk"] else ("偏高" if value < ref.get("high_risk", ref["high"] * 2) else "显著升高")
        findings = [f"{lab_name} {value} {ref['unit']} — 高于参考范围 ({ref['low']}-{ref['high']} {ref['unit']})，{severity}"]
        needs_review = value > ref.get("high_risk", ref["high"] * 2)
    else:
        findings = [f"{lab_name} {value} {ref['unit']} — 在参考范围内 ({ref['low']}-{ref['high']} {ref['unit']})"]
        needs_review = False

    return ToolResult(
        toolName="lab_value_lookup",
        findings=findings,
        confidence=0.8,
        needsReview=needs_review,
    )


def drug_interaction_check(drugs: list[str]) -> ToolResult:
    """Check for known drug interactions (mock)."""
    findings = []
    highest_risk = "LOW"

    for i in range(len(drugs)):
        for j in range(i + 1, len(drugs)):
            key = (drugs[i], drugs[j])
            rev_key = (drugs[j], drugs[i])
            interaction = _DRUG_INTERACTIONS.get(key) or _DRUG_INTERACTIONS.get(rev_key)
            if interaction:
                desc, risk = interaction
                findings.append(f"{drugs[i]} + {drugs[j]}: {desc} ({risk})")
                if risk == "HIGH":
                    highest_risk = "HIGH"
                elif risk == "MEDIUM" and highest_risk != "HIGH":
                    highest_risk = "MEDIUM"

    if not findings:
        findings.append("未发现已知药物相互作用")

    return ToolResult(
        toolName="drug_interaction_check",
        findings=findings,
        confidence=0.7 if findings else 0.3,
        needsReview=highest_risk != "LOW",
    )


def extract_lab_values_from_text(text: str) -> list[tuple[str, float]]:
    """Extract lab name-value pairs from text using simple pattern matching (mock)."""
    results = []
    for lab_name in _LAB_RANGES:
        if lab_name in text:
            # Look for numeric values near the lab name
            import re
            pattern = rf"{lab_name}[^\d]*(\d+\.?\d*)"
            match = re.search(pattern, text)
            if match:
                try:
                    value = float(match.group(1))
                    results.append((lab_name, value))
                except ValueError:
                    pass
    return results


def extract_drugs_from_text(text: str) -> list[str]:
    """Extract drug names from text (mock)."""
    known_drugs = {"二甲双胍", "华法林", "阿司匹林", "布洛芬",
                    "他汀类", "ACEI", "钾补充剂", "阿托伐他汀",
                    "氯吡格雷", "胰岛素", "对乙酰氨基酚"}
    lowered = text.lower()
    return [drug for drug in known_drugs if drug.lower() in lowered]
