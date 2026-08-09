package com.ipuuuuu.doctorwang.mock.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.web.MockMultipartFile
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertTrue

class StorageSigningTest {
    @TempDir lateinit var temp: Path
    @Test fun `signed URL is short lived and contains a signature`() {
        val storage = LocalFileStorageService(temp.toString(), "test-secret")
        val stored = storage.store("u", 1, MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF".toByteArray()))
        val url = storage.signedReadUrl(stored.storageKey, 60)
        assertTrue(url.contains("expires=")); assertTrue(url.contains("sig="))
    }
}
