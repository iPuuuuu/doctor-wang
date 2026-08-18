package com.ipuuuuu.doctorwang.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicalMarkdownTest {
    @Test
    fun `parses headings lists paragraphs and tables`() {
        val blocks = parseMedicalMarkdown(
            """
            ## 分析摘要
            结果仅供参考。

            - 建议复查
            - 如有不适及时就医

            | 指标 | 结果 |
            | --- | --- |
            | ALT | 正常 |
            """.trimIndent(),
        )

        assertEquals(4, blocks.size)
        assertEquals(MarkdownBlock.Heading("分析摘要"), blocks[0])
        assertEquals(MarkdownBlock.Paragraph("结果仅供参考。"), blocks[1])
        assertEquals(
            MarkdownBlock.BulletList(listOf("建议复查", "如有不适及时就医")),
            blocks[2],
        )
        assertTrue(blocks[3] is MarkdownBlock.Table)
        assertEquals(2, (blocks[3] as MarkdownBlock.Table).rows.size)
    }

    @Test
    fun `html tags are stripped and content remains plain text`() {
        val blocks = parseMedicalMarkdown("<script>alert('x')</script><b>健康建议</b>")
        assertEquals(listOf(MarkdownBlock.Paragraph("alert('x')健康建议")), blocks)
    }
}
