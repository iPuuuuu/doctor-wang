package com.ipuuuuu.doctorwang.mock.service

/**
 * 报告解读（REPORT-INTERPRETATION-001）：
 * AI 解析（向量化）完成后，用一次循证 RAG 查询生成"摘要/重点发现/建议"，
 * 供报告详情页展示。纯函数、无 Spring 依赖，便于单测。
 *
 * 医疗安全：只允许基于报告原文的解读——AI 输出必须带可追溯引用（由
 * AiSafetyGate 在调用方校验）；解析失败或格式异常时回退到诚实兜底文案，
 * 绝不编造检查结果。
 */
object ReportInterpretation {

    /** 解读提示词：要求 LLM 严格按【摘要】【重点发现】【建议】分段输出，只基于报告原文。 */
    const val PROMPT: String =
        "请以健康辅助助手身份解读这份检查报告。严格只基于报告原文内容，报告中没有的内容一律不要写。\n" +
            "必须严格按以下格式输出，且只输出这三个部分，不要添加其他文字或标题：\n" +
            "【摘要】用一段话概括报告的主要内容\n" +
            "【重点发现】1. 具体检查结果一\n2. 具体检查结果二\n" +
            "【建议】1. 基于报告内容的就医或复查建议一\n2. 建议二\n" +
            "如果报告中信息不足以给出建议，【建议】只写：请将本报告交由医生解读。"
    

    /** 诚实兜底文案：解读生成失败（AI 不可用/无证据/格式异常）时使用，不编造内容。 */
    const val FALLBACK_SUMMARY: String =
        "报告已完成 AI 解析，可在 AI 问诊或安心话聊中查看基于本报告的解读。本内容仅用于健康辅助，不能替代医生诊断；如有紧急或高风险症状，请及时就医。"

    data class Interpretation(val summary: String, val findings: List<String>, val suggestions: List<String>) {
        companion object {
            fun fallback() = Interpretation(FALLBACK_SUMMARY, emptyList(), emptyList())
        }
    }

    private const val SUMMARY_MARK = "【摘要】"
    private const val FINDINGS_MARK = "【重点发现】"
    private const val SUGGESTIONS_MARK = "【建议】"
    private const val MAX_ITEMS = 10
    private const val MAX_ITEM_LENGTH = 200

    /**
     * 解析 AI 的解读文本。容错规则：
     * - 找不到【摘要】标记 → 整段文本作为摘要（不丢信息），发现/建议为空。
     * - 某段标记缺失 → 该段为空列表。
     * - 列表项支持 "1." / "1、" / "-" 前缀，自动去掉序号。
     * - 空文本 → 兜底文案。
     */
    fun parse(text: String?): Interpretation {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return Interpretation.fallback()

        val summary = section(raw, SUMMARY_MARK, FINDINGS_MARK, SUGGESTIONS_MARK)
        if (summary == null) {
            // 无【摘要】标记：整段作为摘要（截断防滥用），不强行拆解
            return Interpretation(raw.take(2000), emptyList(), emptyList())
        }
        val findings = items(section(raw, FINDINGS_MARK, SUGGESTIONS_MARK, null))
        val suggestions = items(section(raw, SUGGESTIONS_MARK, null, null))
        val cleaned = summary.trim().ifBlank { FALLBACK_SUMMARY }
        if (findings.isEmpty() && suggestions.isEmpty() && cleaned == FALLBACK_SUMMARY) return Interpretation.fallback()
        return Interpretation(cleaned.take(2000), findings, suggestions)
    }

    /** 取 start 标记之后、任一 end 标记之前（或文本末尾）的内容；无 start 返回 null。 */
    private fun section(text: String, start: String, vararg ends: String?): String? {
        val startIdx = text.indexOf(start)
        if (startIdx < 0) return null
        val from = startIdx + start.length
        var to = text.length
        for (end in ends) {
            if (end == null) continue
            val idx = text.indexOf(end, from)
            if (idx in 0 until to) to = idx
        }
        return text.substring(from, to).trim()
    }

    /** 把段落拆成列表项：按行切分，识别编号/项目符号前缀，去空白，限长限量。 */
    private fun items(section: String?): List<String> {
        if (section.isNullOrBlank()) return emptyList()
        val result = mutableListOf<String>()
        for (line in section.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val cleaned = trimmed
                .replaceFirst(Regex("^\\d+[.、．]\\s*"), "")
                .replaceFirst(Regex("^[-•·]\\s*"), "")
                .trim()
            if (cleaned.isNotEmpty() && result.size < MAX_ITEMS) {
                result.add(cleaned.take(MAX_ITEM_LENGTH))
            }
        }
        return result
    }
}
