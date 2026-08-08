HIGH_RISK_TERMS = ("胸痛", "呼吸困难", "意识改变", "严重出血", "自伤", "suicidal", "shortness of breath", "chest pain")


def risk_triage(question: str) -> str:
    return "HIGH" if any(term.lower() in question.lower() for term in HIGH_RISK_TERMS) else "LOW"


def evaluate_medical_risk(question: str) -> tuple[str, list[str], bool]:
    if risk_triage(question) == "HIGH":
        return (
            "HIGH",
            ["检测到高风险症状，请尽快线下就医或联系急诊服务。", "本系统不能替代医生诊断。"],
            True,
        )
    return ("LOW", ["仅供健康辅助参考，不构成诊断意见"], False)
