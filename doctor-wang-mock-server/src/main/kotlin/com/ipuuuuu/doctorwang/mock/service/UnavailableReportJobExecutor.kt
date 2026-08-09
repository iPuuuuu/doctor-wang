package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.persistence.entity.ReportProcessingJobEntity
import com.ipuuuuu.doctorwang.mock.config.AiServiceProperties
import com.ipuuuuu.doctorwang.mock.model.AiIngestionRequest
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportFileRepository
import org.springframework.stereotype.Component

@Component
class UnavailableReportJobExecutor(private val client: AiReportClient, private val files: MedicalReportFileRepository, private val storage: FileStorageService, private val props: AiServiceProperties) : ReportJobExecutor {
    override fun execute(job: ReportProcessingJobEntity): ReportJobResult {
        if (!props.enabled) return ReportJobResult(job.id.toString(), job.report.id, job.attemptCount, job.traceId, "backend-v1", status = "FAILED", errorCode = "AI_SERVICE_NOT_CONFIGURED")
        return try {
            val file = files.findByReport(job.report) ?: return ReportJobResult(job.id.toString(), job.report.id, job.attemptCount, job.traceId, "backend-v1", status = "FAILED", errorCode = "SOURCE_FILE_MISSING")
            val namespace = "report:${job.userId ?: job.report.user.id}:${job.report.id}"
            val accepted = client.submit(AiIngestionRequest(job.id.toString(), job.report.id, job.userId ?: job.report.user.id, namespace, file.originalFileName, file.mimeType, file.storageKey, storage.signedReadUrl(file.storageKey), file.fileSize, props.sourceVersion, job.traceId), job.id.toString())
            job.aiJobId = accepted.jobId
            val state = client.status(accepted.jobId)
            val completed = state.status == "COMPLETED"
            ReportJobResult(job.id.toString(), job.report.id, job.attemptCount, job.traceId, props.sourceVersion, status = if (completed) "COMPLETED" else "FAILED", errorCode = if (completed) null else (state.errorCode ?: state.status), reviewRequired = state.requiresReview, resultDigest = job.payloadHash)
        } catch (_: AiServiceUnavailable) {
            ReportJobResult(job.id.toString(), job.report.id, job.attemptCount, job.traceId, props.sourceVersion, status = "FAILED", errorCode = "AI_SERVICE_UNAVAILABLE")
        }
    }
}
