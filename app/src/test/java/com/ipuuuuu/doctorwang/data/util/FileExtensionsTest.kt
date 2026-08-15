package com.ipuuuuu.doctorwang.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FileExtensionsTest {

    @Test
    fun `derives ext from mime subtype first`() {
        assertEquals("jpg", resolveDownloadExtension("image/jpeg".substringAfterLast('/'), "腹部超声报告"))
        assertEquals("png", resolveDownloadExtension("image/png".substringAfterLast('/'), "胸部X线报告"))
        assertEquals("pdf", resolveDownloadExtension("application/pdf".substringAfterLast('/'), "体检报告"))
    }

    @Test
    fun `falls back to title extension when mime unknown`() {
        assertEquals("jpg", resolveDownloadExtension(null, "IMG_20260702_1234.jpg"))
        assertEquals("pdf", resolveDownloadExtension("application/octet-stream".substringAfterLast('/'), "sample.pdf"))
    }

    @Test
    fun `defaults to pdf when neither mime nor title has usable ext`() {
        assertEquals("pdf", resolveDownloadExtension(null, "腹部超声报告"))
        assertEquals("pdf", resolveDownloadExtension(null, null))
        assertEquals("pdf", resolveDownloadExtension("".substringAfterLast('/'), ""))
    }

    @Test
    fun `ignores unusable title extensions`() {
        assertEquals("pdf", resolveDownloadExtension(null, "报告.很长的扩展名"))
        assertEquals("pdf", resolveDownloadExtension(null, "报告.123456"))
    }
}
