package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.persistence.entity.ReportProcessingJobEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.MedicalReportFindingEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.MedicalReportSuggestionEntity
import com.ipuuuuu.doctorwang.mock.persistence.repository.ReportProcessingJobRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportFindingRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportSuggestionRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Component
class PostgreSqlPollingWorker(
    private val jobs: ReportProcessingJobRepository,
    private val executor: ReportJobExecutor,
    private val findingsRepo: MedicalReportFindingRepository,
    private val suggestionsRepo: MedicalReportSuggestionRepository,
    private val stateMachine: ReportJobStateMachine = ReportJobStateMachine(),
    @Value("\${app.report-processing.worker-enabled:false}") private val enabled: Boolean,
    @Value("\${app.report-processing.max-attempts:3}") private val maxAttempts: Int,
) {
    @Scheduled(fixedDelayString = "\${app.report-processing.poll-interval-ms:5000}")
    @Transactional
    fun poll() { if (enabled) processAvailable() }

    fun processAvailable() {
        val now = OffsetDateTime.now()
        jobs.recoverExpired(now)
        jobs.findClaimableForUpdate(now, PageRequest.of(0, 10)).forEach { job -> processClaimed(job, now) }
    }

    private fun processClaimed(job: ReportProcessingJobEntity, now: OffsetDateTime) {
        if (job.attemptCount >= maxAttempts) { fail(job, "RETRY_LIMIT_EXCEEDED"); return }
        stateMachine.transition(job.status, job.stage, "PROCESSING", "EXTRACTING")
        job.status = "PROCESSING"
        job.stage = "EXTRACTING"
        job.phase = "EXTRACTING"
        job.attemptCount += 1
        job.lockedAt = now
        job.lockedBy = UUID.randomUUID().toString()
        job.leaseExpiresAt = now.plusMinutes(10)
        job.startedAt = now
        job.updatedAt = now
        jobs.saveAndFlush(job)

        val result = executor.execute(job)
        if (result.status == "COMPLETED" && !result.reviewRequired && result.errorCode == null) {
            stateMachine.transition(job.status, job.stage, "COMPLETED", "FINISHED")
            job.status = "COMPLETED"
            job.stage = "FINISHED"
            job.phase = "FINISHED"
            job.retryable = false
            job.errorCode = null
            job.resultDigest = result.resultDigest
            job.resultVersion = result.processingVersion
            job.activeIndexVersion = result.processingVersion
            job.finishedAt = OffsetDateTime.now()
            job.lockedAt = null
            job.lockedBy = null
            job.leaseExpiresAt = null
            job.updatedAt = OffsetDateTime.now()
            job.report.status = "COMPLETED"
            // REPORT-INTERPRETATION-001：回写循证解读（摘要/重点发现/建议），供报告详情页展示。
            // 未生成（AI 不可用/无证据/格式异常）时保持兜底文案，不编造。
            result.summary?.takeIf { it.isNotBlank() }?.let { job.report.summary = it }
            result.findings.forEach { f -> findingsRepo.save(MedicalReportFindingEntity(report = job.report, findingText = f)) }
            result.suggestions.forEach { s -> suggestionsRepo.save(MedicalReportSuggestionEntity(report = job.report, suggestionText = s)) }
            jobs.save(job)
        } else {
            // A review-required or incomplete AI result must fail closed and never
            // be presented as a completed medical interpretation.
            fail(job, result.errorCode ?: if (result.reviewRequired) "REVIEW_REQUIRED" else "AI_RESULT_INVALID")
        }
    }

    private fun fail(job: ReportProcessingJobEntity, code: String) {
        stateMachine.transition(job.status, job.stage, "FAILED", job.stage)
        job.status = "FAILED"
        job.errorCode = code
        job.retryable = false
        job.lastErrorAt = OffsetDateTime.now()
        job.finishedAt = OffsetDateTime.now()
        job.lockedAt = null
        job.lockedBy = null
        job.leaseExpiresAt = null
        job.updatedAt = OffsetDateTime.now()
        job.report.status = "FAILED"
        jobs.save(job)
    }
}
