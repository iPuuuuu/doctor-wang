# Emergency / high-risk terms — trigger immediate medical escalation
HIGH_RISK_TERMS = (
    # Chinese emergency symptoms
    "胸痛", "呼吸困难", "意识改变", "意识模糊", "严重出血", "大出血",
    "自伤", "自杀", "昏迷", "休克", "心脏骤停", "窒息",
    "急性心肌梗死", "脑出血", "中风", "卒中",
    "咳血", "呕血", "便血", "黑便",
    "严重过敏", "喉头水肿", "过敏性休克",
    "高热惊厥", "抽搐", "持续癫痫",
    "严重外伤", "骨折", "大面积烧伤",
    # English emergency terms
    "suicidal", "shortness of breath", "chest pain",
    "cardiac arrest", "seizure", "anaphylaxis",
    "unconscious", "cannot breathe", "severe bleeding",
    "stroke symptoms", "heart attack",
)

# Medium-risk terms — concerning but not immediately life-threatening
MEDIUM_RISK_TERMS = (
    "持续高烧", "持续呕吐", "持续腹泻", "剧烈腹痛",
    "视力突然下降", "听力突然下降", "突然眩晕",
    "尿血", "黄疸", "持续头痛",
    "体重急剧下降", "持续失眠",
    "persistent fever", "severe abdominal pain",
    "sudden vision loss", "jaundice",
)


def risk_triage(question: str) -> str:
    """Classify question risk level: HIGH, MEDIUM, or LOW."""
    lowered = question.lower()
    if any(term.lower() in lowered for term in HIGH_RISK_TERMS):
        return "HIGH"
    if any(term.lower() in lowered for term in MEDIUM_RISK_TERMS):
        return "MEDIUM"
    return "LOW"


def evaluate_medical_risk(question: str) -> tuple[str, list[str], bool]:
    """Evaluate medical risk and return (risk_level, warnings, blocked).

    HIGH:    blocked=True  — call must be escalated to emergency services
    MEDIUM:  blocked=False — needsReview, recommend prompt medical attention
    LOW:     blocked=False — general health disclaimer only
    """
    risk = risk_triage(question)
    if risk == "HIGH":
        return (
            "HIGH",
            [
                "检测到高风险症状，请立即联系急救服务（120）或尽快前往急诊。",
                "本系统不能替代医生诊断或急诊处理。",
            ],
            True,  # blocked
        )
    if risk == "MEDIUM":
        return (
            "MEDIUM",
            [
                "检测到需要关注的症状，建议近期就医评估。",
                "本内容仅供健康辅助参考，不构成诊断意见。",
            ],
            False,  # not blocked, but needsReview
        )
    return (
        "LOW",
        ["仅供健康辅助参考，不构成诊断意见。"],
        False,
    )
