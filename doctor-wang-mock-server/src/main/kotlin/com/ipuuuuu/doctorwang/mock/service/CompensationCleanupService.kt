package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.TraceIdContext
import com.ipuuuuu.doctorwang.mock.persistence.entity.ReportCleanupTaskEntity
import com.ipuuuuu.doctorwang.mock.persistence.repository.ReportCleanupTaskRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.ReportUploadIdempotencyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class CompensationCleanupService(
    private val tasks: ReportCleanupTaskRepository,
    private val idempotency: ReportUploadIdempotencyRepository,
    private val storage: FileStorageService,
) {
    @Transactional
    fun record(storageKey: String, reason: String, reportId: Long? = null, traceId: String = TraceIdContext.get()) {
        tasks.save(ReportCleanupTaskEntity(storageKey = storageKey, reason = reason, report = null, traceId = traceId))
    }

    @Transactional
    fun cleanupExpiredIdempotency(now: OffsetDateTime = OffsetDateTime.now()): Int = idempotency.deleteExpired(now)

    @Transactional
    fun cleanupPending(now: OffsetDateTime = OffsetDateTime.now(), limit: Int = 50): Int {
        val pending = tasks.findAll().asSequence().filter { it.status == "PENDING" && !it.nextAttemptAt.isAfter(now) }.take(limit).toList()
        pending.forEach { task ->
            try { storage.delete(task.storageKey); task.status = "COMPLETED" }
            catch (_: Exception) { task.attemptCount++; task.nextAttemptAt = now.plusMinutes(5); task.status = "PENDING" }
            task.updatedAt = OffsetDateTime.now(); tasks.save(task)
        }
        return pending.size
    }
}
