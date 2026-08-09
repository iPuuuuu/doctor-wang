package com.ipuuuuu.doctorwang.mock.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.web.MockMultipartFile
import java.nio.file.Files
import java.nio.file.Path

class LocalFileStorageServiceTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `stores file below configured root and deletes it`() {
        val service = LocalFileStorageService(tempDir.toString())
        val file = MockMultipartFile("file", "report.pdf", "application/pdf", "%PDF-test".toByteArray())

        val stored = service.store("user-1", 42, file)
        val target = tempDir.resolve(stored.storageKey)
        assertTrue(target.startsWith(tempDir))
        assertTrue(Files.exists(target))
        assertEquals(64, stored.sha256.length)

        service.delete(stored.storageKey)
        assertFalse(Files.exists(target))
    }
}
