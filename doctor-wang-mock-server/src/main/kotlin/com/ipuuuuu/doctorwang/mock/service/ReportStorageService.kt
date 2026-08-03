package com.ipuuuuu.doctorwang.mock.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.ipuuuuu.doctorwang.mock.model.MedicalReportResponse
import com.ipuuuuu.doctorwang.mock.model.ReportListItemResponse
import com.ipuuuuu.doctorwang.mock.model.UploadResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException

@Service
class ReportStorageService(
    private val database: DatabaseService,
    private val objectMapper: ObjectMapper,
    private val textExtractor: ReportTextExtractorService,
    private val analysisService: ReportAnalysisService,
    @Value("\${DOCTOR_WANG_UPLOAD_DIR:/opt/doctor-wang/uploads}") private val uploadDir: String,
) {
    private val allowed = setOf("application/pdf", "image/jpeg", "image/png")

    fun upload(userId: String, file: MultipartFile): UploadResponse {
        if (file.isEmpty || file.size > 15L * 1024 * 1024) fail("文件不能为空且不能超过 15MB")
        val type = file.contentType ?: ""
        if (type !in allowed) fail("仅支持 PDF、JPG、PNG 文件")
        val original = file.originalFilename?.take(150)?.ifBlank { "medical-file" } ?: "medical-file"
        val dir = Path.of(uploadDir, userId)
        Files.createDirectories(dir)
        val stored = UUID.randomUUID().toString() + when (type) {
            "application/pdf" -> ".pdf"
            "image/png" -> ".png"
            else -> ".jpg"
        }
        val target = dir.resolve(stored)
        file.inputStream.use { Files.copy(it, target) }
        val now = Instant.now().toString()
        val id = database.connection { c ->
            c.prepareStatement(
                "INSERT INTO medical_reports(user_id,original_name,storage_path,media_type,size_bytes,status,extracted_text,analysis_summary,findings_json,suggestions_json,failed_reason,created_at,updated_at) VALUES(?,?,?,?,?,'uploaded','','','[]','[]','',?,?)",
                java.sql.Statement.RETURN_GENERATED_KEYS,
            ).use { s ->
                s.setString(1, userId)
                s.setString(2, original)
                s.setString(3, target.toString())
                s.setString(4, type)
                s.setLong(5, file.size)
                s.setString(6, now)
                s.setString(7, now)
                s.executeUpdate()
                s.generatedKeys.use {
                    it.next()
                    it.getLong(1)
                }
            }
        }

        analyzeReport(id, userId, original, target, type)
        return UploadResponse(id, "analyzed")
    }

    fun listReports(userId: String): List<ReportListItemResponse> = database.connection { c ->
        c.prepareStatement(
            "SELECT id, original_name, created_at, media_type, status FROM medical_reports WHERE user_id=? ORDER BY created_at DESC",
        ).use { s ->
            s.setString(1, userId)
            s.executeQuery().use { r ->
                buildList {
                    while (r.next()) {
                        add(
                            ReportListItemResponse(
                                reportId = r.getLong("id"),
                                title = r.getString("original_name"),
                                date = r.getString("created_at").take(10),
                                type = r.getString("media_type"),
                                status = r.getString("status"),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun report(userId: String, id: Long): MedicalReportResponse = database.connection { c ->
        c.prepareStatement(
            "SELECT original_name, created_at, media_type, status, analysis_summary, findings_json, suggestions_json, failed_reason FROM medical_reports WHERE id=? AND user_id=?",
        ).use { s ->
            s.setLong(1, id)
            s.setString(2, userId)
            s.executeQuery().use { r ->
                if (!r.next()) throw ResponseStatusException(HttpStatus.NOT_FOUND, "病历不存在")
                val status = r.getString("status")
                val failedReason = r.getString("failed_reason")
                MedicalReportResponse(
                    id,
                    r.getString("original_name"),
                    r.getString("created_at").take(10),
                    r.getString("media_type"),
                    r.getString("analysis_summary").ifBlank { fallbackSummary(status, failedReason) },
                    readStringList(r.getString("findings_json")),
                    readStringList(r.getString("suggestions_json")).ifEmpty { listOf(statusSuggestion(status, failedReason)) },
                )
            }
        }
    }

    fun delete(userId: String, id: Long) {
        val report = reportRow(userId, id)
        runCatching { Files.deleteIfExists(Path.of(report.storagePath)) }
        database.connection { c ->
            c.prepareStatement("DELETE FROM medical_reports WHERE id=? AND user_id=?").use { s ->
                s.setLong(1, id)
                s.setString(2, userId)
                s.executeUpdate()
            }
        }
    }

    fun download(userId: String, id: Long): StoredReportFile {
        val report = reportRow(userId, id)
        val path = Path.of(report.storagePath)
        if (!Files.exists(path)) throw ResponseStatusException(HttpStatus.NOT_FOUND, "病历文件不存在")
        return StoredReportFile(
            fileName = report.originalName,
            mediaType = report.mediaType,
            bytes = Files.readAllBytes(path),
        )
    }

    private fun analyzeReport(id: Long, userId: String, originalName: String, path: Path, mediaType: String) {
        updateStatus(id, userId, "processing")
        runCatching {
            val extractedText = textExtractor.extract(path, mediaType, originalName)
            val result = analysisService.analyze(extractedText, mediaType, originalName)
            persistAnalysis(id, userId, extractedText, result.summary, result.findings, result.suggestions)
        }.onFailure { error ->
            persistFailure(id, userId, error.message ?: "报告分析失败")
        }
    }

    private fun persistAnalysis(
        id: Long,
        userId: String,
        extractedText: String,
        summary: String,
        findings: List<String>,
        suggestions: List<String>,
    ) {
        val now = Instant.now().toString()
        database.connection { c ->
            c.prepareStatement(
                "UPDATE medical_reports SET status='analyzed', extracted_text=?, analysis_summary=?, findings_json=?, suggestions_json=?, failed_reason='', updated_at=? WHERE id=? AND user_id=?",
            ).use { s ->
                s.setString(1, extractedText)
                s.setString(2, summary)
                s.setString(3, objectMapper.writeValueAsString(findings))
                s.setString(4, objectMapper.writeValueAsString(suggestions))
                s.setString(5, now)
                s.setLong(6, id)
                s.setString(7, userId)
                s.executeUpdate()
            }
        }
    }

    private fun persistFailure(id: Long, userId: String, reason: String) {
        val now = Instant.now().toString()
        database.connection { c ->
            c.prepareStatement(
                "UPDATE medical_reports SET status='failed', failed_reason=?, updated_at=? WHERE id=? AND user_id=?",
            ).use { s ->
                s.setString(1, reason)
                s.setString(2, now)
                s.setLong(3, id)
                s.setString(4, userId)
                s.executeUpdate()
            }
        }
    }

    private fun updateStatus(id: Long, userId: String, status: String) {
        val now = Instant.now().toString()
        database.connection { c ->
            c.prepareStatement("UPDATE medical_reports SET status=?, updated_at=? WHERE id=? AND user_id=?").use { s ->
                s.setString(1, status)
                s.setString(2, now)
                s.setLong(3, id)
                s.setString(4, userId)
                s.executeUpdate()
            }
        }
    }

    private fun reportRow(userId: String, id: Long): StoredReportRow = database.connection { c ->
        c.prepareStatement(
            "SELECT original_name, storage_path, media_type, status FROM medical_reports WHERE id=? AND user_id=?",
        ).use { s ->
            s.setLong(1, id)
            s.setString(2, userId)
            s.executeQuery().use { r ->
                if (!r.next()) throw ResponseStatusException(HttpStatus.NOT_FOUND, "病历不存在")
                StoredReportRow(
                    originalName = r.getString("original_name"),
                    storagePath = r.getString("storage_path"),
                    mediaType = r.getString("media_type"),
                    status = r.getString("status"),
                )
            }
        }
    }

    private fun readStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { objectMapper.readValue(json, Array<String>::class.java).toList() }.getOrDefault(emptyList())
    }

    private fun fallbackSummary(status: String, failedReason: String): String {
        return when (status) {
            "processing" -> "报告正在分析中，请稍后刷新查看结果。"
            "failed" -> failedReason.ifBlank { "报告分析失败，请稍后重试。" }
            "uploaded" -> "文件已上传，等待分析。"
            else -> "文件已安全保存，等待 AI 分析。"
        }
    }

    private fun statusSuggestion(status: String, failedReason: String): String {
        return when (status) {
            "uploaded" -> "当前为待分析状态"
            "processing" -> "当前正在进行文本抽取与 AI 分析"
            "analyzed" -> "报告分析已完成，可查看重点发现与建议"
            "failed" -> failedReason.ifBlank { "报告分析失败，请稍后重试" }
            else -> "当前状态：$status"
        }
    }

    private fun fail(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)
}

data class StoredReportFile(
    val fileName: String,
    val mediaType: String,
    val bytes: ByteArray,
)

private data class StoredReportRow(
    val originalName: String,
    val storagePath: String,
    val mediaType: String,
    val status: String,
)
