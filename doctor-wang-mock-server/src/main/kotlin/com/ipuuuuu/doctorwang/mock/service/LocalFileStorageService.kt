package com.ipuuuuu.doctorwang.mock.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class LocalFileStorageService(
    @Value("\${app.file-storage.root:./data/report-files}") private val root: String,
    @Value("\${app.file-storage.signing-secret:dev-only-change-me}") private val signingSecret: String = "dev-only-change-me",
) : FileStorageService {
    override fun store(userId: String, reportId: Long, file: MultipartFile): StoredFile {
        val safeName = file.originalFilename?.substringAfterLast('/')?.substringAfterLast('\\')?.take(120) ?: "upload"
        val key = "$userId/$reportId/${UUID.randomUUID()}-$safeName"
        val target = Path.of(root).resolve(key).normalize()
        require(target.startsWith(Path.of(root).normalize())) { "非法文件路径" }
        Files.createDirectories(target.parent)
        file.inputStream.use { input -> Files.copy(input, target) }
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = Files.newInputStream(target).use { input ->
            val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        return StoredFile(key, hash)
    }

    override fun retrieve(storageKey: String): java.io.InputStream {
        val target = Path.of(root).resolve(storageKey).normalize()
        require(target.startsWith(Path.of(root).normalize())) { "非法文件路径" }
        require(Files.isRegularFile(target)) { "文件不存在" }
        return Files.newInputStream(target)
    }

    override fun delete(storageKey: String) {
        val target = Path.of(root).resolve(storageKey).normalize()
        if (target.startsWith(Path.of(root).normalize())) Files.deleteIfExists(target)
    }

    override fun signedReadUrl(storageKey: String, ttlSeconds: Long): String {
        require(ttlSeconds in 1..900)
        val expires = (System.currentTimeMillis() / 1000) + ttlSeconds
        val data = "$storageKey:$expires"
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(signingSecret.toByteArray(), "HmacSHA256")) }
        val signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.toByteArray()))
        return "file://$storageKey?expires=$expires&sig=$signature"
    }
}
