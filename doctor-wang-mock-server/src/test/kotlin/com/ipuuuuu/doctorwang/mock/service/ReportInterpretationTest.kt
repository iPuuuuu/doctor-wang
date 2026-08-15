package com.ipuuuuu.doctorwang.mock.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 报告解读解析（REPORT-INTERPRETATION-001）：
 * 结构化输出解析、容错、诚实兜底（医疗安全：不编造）。
 */
class ReportInterpretationTest {

    @Test
    fun `parses structured summary findings and suggestions`() {
        val text = """
            【摘要】这份腹部超声报告显示肝脏、胆囊、胰腺、脾脏、双肾未见明显异常。
            【重点发现】1. 肝脏形态大小正常，包膜光整，实质回声均匀，未见占位性病变
            2. 胆囊壁光滑，腔内未见结石
            【建议】1. 建议定期复查
            2. 请将报告交由医生解读
        """.trimIndent()
        val result = ReportInterpretation.parse(text)
        assertTrue(result.summary.contains("腹部超声报告显示肝脏"))
        assertEquals(2, result.findings.size)
        assertEquals("肝脏形态大小正常，包膜光整，实质回声均匀，未见占位性病变", result.findings[0])
        assertEquals("胆囊壁光滑，腔内未见结石", result.findings[1])
        assertEquals(2, result.suggestions.size)
        assertEquals("建议定期复查", result.suggestions[0])
    }

    @Test
    fun `handles missing findings section`() {
        val text = "【摘要】只有摘要没有发现和建议。"
        val result = ReportInterpretation.parse(text)
        assertTrue(result.summary.contains("只有摘要"))
        assertTrue(result.findings.isEmpty())
        assertTrue(result.suggestions.isEmpty())
    }

    @Test
    fun `no summary marker falls back to whole text as summary`() {
        val text = "模型没有按格式输出，只是一段普通文本说明。"
        val result = ReportInterpretation.parse(text)
        assertEquals(text, result.summary)
        assertTrue(result.findings.isEmpty())
        assertTrue(result.suggestions.isEmpty())
    }

    @Test
    fun `blank text falls back to honest summary`() {
        val result = ReportInterpretation.parse("   ")
        assertEquals(ReportInterpretation.FALLBACK_SUMMARY, result.summary)
        assertTrue(result.findings.isEmpty())
    }

    @Test
    fun `null text falls back to honest summary`() {
        val result = ReportInterpretation.parse(null)
        assertEquals(ReportInterpretation.FALLBACK_SUMMARY, result.summary)
    }

    @Test
    fun `supports dash bullets and strips numbering variants`() {
        val text = """
            【摘要】摘要内容。
            【重点发现】- 发现甲
            • 发现乙
            【建议】一、建议内容保留（不匹配数字编号则不剥除）
        """.trimIndent()
        val result = ReportInterpretation.parse(text)
        assertEquals(listOf("发现甲", "发现乙"), result.findings)
        assertEquals(listOf("一、建议内容保留（不匹配数字编号则不剥除）"), result.suggestions)
    }

    @Test
    fun `caps item count and length`() {
        val longText = "x".repeat(500)
        val text = "【摘要】摘要。\n【重点发现】" + (1..15).joinToString("\n") { "$it. 发现$it" } +
            "\n【建议】1. $longText"
        val result = ReportInterpretation.parse(text)
        assertTrue(result.findings.size <= 10)
        assertTrue(result.suggestions.all { it.length <= 200 })
    }
}
