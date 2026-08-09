package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.persistence.entity.ReportProcessingJobEntity
import com.ipuuuuu.doctorwang.mock.persistence.repository.ReportProcessingJobRepository
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
    private val stateMachine: ReportJobStateMachine = ReportJobStateMachine(),
    @Value("\${app.report-processing.worker-enabled:false}") private val enabled: Boolean,
    @Value("\${app.report-processing.max-attempts:3}") private val maxAttempts: Int,
) {
    @Scheduled(fixedDelayString = "\${app.report-processing.poll-interval-ms:5000}")
    fun poll() { if (enabled) processAvailable() }

    @Transactional
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
        // P0 deliberately rejects completion from the generic executor. AI acceptance is P3.
        fail(job, result.errorCode ?: "AI_RESULT_INVALID")
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
