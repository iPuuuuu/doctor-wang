package com.ipuuuuu.doctorwang.mock.service

import org.springframework.web.multipart.MultipartFile
import java.io.InputStream

interface FileStorageService {
    fun store(userId: String, reportId: Long, file: MultipartFile): StoredFile
    fun retrieve(storageKey: String): InputStream
    fun delete(storageKey: String)
    fun signedReadUrl(storageKey: String, ttlSeconds: Long = 300): String
}

data class StoredFile(val storageKey: String, val sha256: String)
