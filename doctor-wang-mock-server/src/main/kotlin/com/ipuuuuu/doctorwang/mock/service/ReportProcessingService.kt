package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.AuthContext
import com.ipuuuuu.doctorwang.mock.config.ApiException
import com.ipuuuuu.doctorwang.mock.config.TraceIdContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.ipuuuuu.doctorwang.mock.model.ReportDetailV1Response
import com.ipuuuuu.doctorwang.mock.model.ReportListItemResponse
import com.ipuuuuu.doctorwang.mock.model.ReportListResponse
import com.ipuuuuu.doctorwang.mock.model.ReportStatusResponse
import com.ipuuuuu.doctorwang.mock.model.ReportUploadResponse
import com.ipuuuuu.doctorwang.mock.persistence.entity.*
import com.ipuuuuu.doctorwang.mock.persistence.repository.*
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.time.OffsetDateTime
import java.util.UUID

@Service
class ReportProcessingService(
    private val reports: MedicalReportRepository,
    private val files: MedicalReportFileRepository,
    private val jobs: ReportProcessingJobRepository,
    private val findings: MedicalReportFindingRepository,
    private val suggestions: MedicalReportSuggestionRepository,
    private val storage: FileStorageService,
    private val idempotency: ReportUploadIdempotencyRepository,
    private val objectMapper: ObjectMapper,
    private val stateMachine: ReportJobStateMachine,
    private val virusScanner: VirusScanner,
    private val cleanup: CompensationCleanupService,
) {
    companion object { const val MAX_SIZE = 20L * 1024 * 1024; val TYPES = setOf("application/pdf", "image/jpeg", "image/png") }

    @Transactional
    fun upload(file: MultipartFile, reportDate: String?, reportType: String?, idempotencyKey: String?): ReportUploadResponse {
        val user = AuthContext.get()?.user ?: throw SecurityException("未登录或登录已失效")
        validate(file)
        val scan = virusScanner.scan(file)
        if (!scan.clean) {
            // SEC-001 fail-closed：扫描服务不可用同样拒绝上传，绝不默认放行。
            throw if (scan.reason == ScanReasons.SCANNER_UNAVAILABLE) {
                ApiException("SCAN_UNAVAILABLE", "安全扫描服务不可用，文件上传已被拒绝，请稍后重试")
            } else {
                ApiException("MALWARE_DETECTED", "文件未通过安全扫描")
            }
        }
        val digest = requestDigest(file.bytes.sha256(), reportDate, reportType)
        if (!idempotencyKey.isNullOrBlank()) {
            require(idempotencyKey.length <= 128) { "Idempotency-Key 长度不能超过 128" }
            idempotency.findByUserAndIdempotencyKey(user, idempotencyKey)?.takeIf { !it.expiresAt.isAfter(OffsetDateTime.now()) }?.let { idempotency.delete(it) }
            idempotency.findByUserAndIdempotencyKeyAndExpiresAtAfter(user, idempotencyKey, OffsetDateTime.now())?.let {
                if (it.requestDigest != digest) throw ApiException("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key 已用于其他请求")
                return objectMapper.readValue(it.responseSnapshot, ReportUploadResponse::class.java)
            }
        }
        val report = reports.save(MedicalReportEntity(user = user, title = if (!reportType.isNullOrBlank()) "${reportType.trim()}报告" else (file.originalFilename ?: "检查报告"), reportDate = reportDate ?: "", reportType = reportType ?: "检查报告", summary = "报告正在处理，结果需经医生确认。", status = "PROCESSING"))
        val stored = try { storage.store(user.userId, report.id, file) } catch (e: Exception) { throw ApiException("STORAGE_FAILED", "文件保存失败") }
        files.save(MedicalReportFileEntity(report = report, storageKey = stored.storageKey, originalFileName = file.originalFilename ?: "upload", mimeType = file.contentType ?: "application/octet-stream", fileSize = file.size, sha256 = stored.sha256))
        val job = jobs.save(ReportProcessingJobEntity(id = UUID.randomUUID(), report = report, userId = user.id, status = "STORED", stage = "STORED", phase = "STORED", payloadHash = digest, traceId = TraceIdContext.get(), nextAttemptAt = OffsetDateTime.now()))
        val response = ReportUploadResponse(report.id, job.id, job.status, "/api/v1/reports/${report.id}/status")
        if (!idempotencyKey.isNullOrBlank()) idempotency.save(ReportUploadIdempotencyEntity(user = user, idempotencyKey = idempotencyKey, requestDigest = digest, report = report, processingJobId = job.id, responseSnapshot = objectMapper.writeValueAsString(response), expiresAt = OffsetDateTime.now().plusHours(24)))
        return response
    }

    @Transactional(readOnly = true)
    fun status(id: Long): ReportStatusResponse {
        val job = ownedJob(id)
        return ReportStatusResponse(id, job.id, job.status, job.stage, job.retryable, job.errorCode, job.updatedAt, job.traceId)
    }

    @Transactional(readOnly = true)
    fun detail(id: Long): ReportDetailV1Response {
        val report = ownedReport(id); val job = jobs.findTopByReportOrderByCreatedAtDesc(report)
        return ReportDetailV1Response(report.id, report.title, report.reportDate, report.reportType, job?.status ?: report.status, if (job?.status == "COMPLETED") report.summary else null, findings.findAllByReportOrderByIdAsc(report).map { it.findingText }, suggestions.findAllByReportOrderByIdAsc(report).map { it.suggestionText }, "本内容仅用于健康辅助，不能替代医生诊断；如有紧急或高风险症状，请及时就医。")
    }

    @Transactional
    fun retry(id: Long): ReportStatusResponse {
        val job = ownedJob(id)
        if (job.status != "FAILED" || !job.retryable) throw ApiException("REPORT_NOT_RETRYABLE", "当前任务不可重试")
        if (job.attemptCount >= 3) throw ApiException("RETRY_LIMIT_EXCEEDED", "已超过最大重试次数")
        stateMachine.transition(job.status, job.stage, "RETRYING", "STORED")
        job.status = "RETRYING"; job.stage = "STORED"; job.errorCode = null; job.retryable = false; job.nextAttemptAt = OffsetDateTime.now().plusSeconds(backoffSeconds(job.attemptCount)); job.updatedAt = OffsetDateTime.now()
        jobs.save(job); return ReportStatusResponse(id, job.id, job.status, job.stage, job.retryable, job.errorCode, job.updatedAt, job.traceId)
    }

    @Transactional
    fun delete(id: Long) {
        val report = ownedReport(id)
        // 先删 job 行，与 worker 的锁顺序一致（job → report），避免并发处理中删除触发 PostgreSQL 死锁。
        jobs.deleteAllByReport(report)
        files.findByReport(report)?.let { storage.delete(it.storageKey) }
        reports.delete(report)
    }

    @Transactional(readOnly = true)
    fun listByUser(page: Int, size: Int): ReportListResponse {
        val user = AuthContext.get()?.user ?: throw SecurityException("未登录或登录已失效")
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100))
        val pageResult = reports.findAllByUser(user, pageable)
        return ReportListResponse(
            items = pageResult.content.map { report ->
                ReportListItemResponse(
                    reportId = report.id,
                    title = report.title,
                    reportDate = report.reportDate,
                    reportType = report.reportType,
                    status = report.status,
                    summary = if (report.status == "COMPLETED") report.summary else null,
                    createdAt = report.createdAt,
                )
            },
            page = pageResult.number,
            size = pageResult.size,
            totalPages = pageResult.totalPages,
            totalElements = pageResult.totalElements,
        )
    }

    @Transactional(readOnly = true)
    fun getFile(reportId: Long): Pair<InputStream, MedicalReportFileEntity> {
        val report = ownedReport(reportId)
        val file = files.findByReport(report) ?: throw NoSuchElementException("报告文件不存在")
        val stream = try { storage.retrieve(file.storageKey) } catch (e: Exception) { throw ApiException("FILE_RETRIEVAL_FAILED", "文件读取失败") }
        return stream to file
    }

    private fun validate(file: MultipartFile) {
        if (file.isEmpty) throw ApiException("FILE_EMPTY", "文件不能为空")
        if (file.size > MAX_SIZE) throw ApiException("FILE_TOO_LARGE", "文件大小不能超过 20MB")
        if (file.contentType?.let(TYPES::contains) != true) throw ApiException("UNSUPPORTED_MEDIA_TYPE", "仅支持 PDF、JPG、PNG 文件")
        val bytes = file.inputStream.use { it.readNBytes(8) }
        val valid = bytes.size >= 4 && (
            (bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()) ||
                (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) ||
                (bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte())
            )
        if (!valid) throw ApiException("FILE_SIGNATURE_MISMATCH", "文件内容与类型不匹配")
    }
    private fun backoffSeconds(attempt: Int) = when (attempt) { 0 -> 5L; 1 -> 30L; else -> 300L }
    private fun requestDigest(fileSha256: String, reportDate: String?, reportType: String?) = "$fileSha256\n${reportDate.orEmpty().trim()}\n${reportType.orEmpty().trim()}\n".toByteArray().sha256()
    private fun ByteArray.sha256() = java.security.MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
    private fun ownedReport(id: Long): MedicalReportEntity { val user = AuthContext.get()?.user ?: throw SecurityException("未登录或登录已失效"); return reports.findById(id).filter { it.user.id == user.id }.orElseThrow { NoSuchElementException("报告不存在") } }
    private fun ownedJob(id: Long): ReportProcessingJobEntity { val report = ownedReport(id); return jobs.findTopByReportOrderByCreatedAtDesc(report) ?: throw NoSuchElementException("处理任务不存在") }
}
