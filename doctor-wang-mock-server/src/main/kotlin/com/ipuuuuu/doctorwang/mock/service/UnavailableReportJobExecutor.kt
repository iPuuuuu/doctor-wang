package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.persistence.entity.ReportProcessingJobEntity
import com.ipuuuuu.doctorwang.mock.config.AiServiceProperties
import com.ipuuuuu.doctorwang.mock.model.AiIngestionRequest
import com.ipuuuuu.doctorwang.mock.model.AiRagRequest
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportFileRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class UnavailableReportJobExecutor(private val client: AiReportClient, private val files: MedicalReportFileRepository, private val storage: FileStorageService, private val props: AiServiceProperties, private val interpretationService: ReportInterpretationService) : ReportJobExecutor {
    override fun execute(job: ReportProcessingJobEntity): ReportJobResult {
        if (!props.enabled) return ReportJobResult(job.id.toString(), job.report.id, job.attemptCount, job.traceId, "backend-v1", status = "FAILED", errorCode = "AI_SERVICE_NOT_CONFIGURED")
        return try {
            val file = files.findByReport(job.report) ?: return ReportJobResult(job.id.toString(), job.report.id, job.attemptCount, job.traceId, "backend-v1", status = "FAILED", errorCode = "SOURCE_FILE_MISSING")
            val namespace = "report:${job.userId ?: job.report.user.id}:${job.report.id}"
            val accepted = client.submit(AiIngestionRequest(job.id.toString(), job.report.id, job.userId ?: job.report.user.id, namespace, file.originalFileName, file.mimeType, file.storageKey, storage.signedReadUrl(file.storageKey), file.fileSize, props.sourceVersion, job.traceId), job.id.toString())
            job.aiJobId = accepted.jobId
            val state = client.status(accepted.jobId)
            val completed = state.status == "COMPLETED"
            if (!completed) {
                return ReportJobResult(job.id.toString(), job.report.id, job.attemptCount, job.traceId, props.sourceVersion, status = "FAILED", errorCode = state.errorCode ?: state.status, reviewRequired = state.requiresReview, resultDigest = job.payloadHash)
            }
            // 向量化完成 → 生成循证解读（摘要/重点发现/建议）。fail-closed：AI 不可用、
            // 无证据或格式异常时回退到诚实兜底文案，绝不编造检查结果。
            val interpretation = interpretationService.generate(job.report, namespace)
            ReportJobResult(job.id.toString(), job.report.id, job.attemptCount, job.traceId, props.sourceVersion,
                status = "COMPLETED", errorCode = null, reviewRequired = false, resultDigest = job.payloadHash,
                summary = interpretation.summary, findings = interpretation.findings, suggestions = interpretation.suggestions)
        } catch (_: AiServiceUnavailable) {
            ReportJobResult(job.id.toString(), job.report.id, job.attemptCount, job.traceId, props.sourceVersion, status = "FAILED", errorCode = "AI_SERVICE_UNAVAILABLE")
        }
    }

    /** 一次循证 RAG 查询生成解读；AI 不可用/无证据/校验不过/非 OK → 诚实兜底。 */
}
