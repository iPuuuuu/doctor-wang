package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.persistence.repository.ReportProcessingJobRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.ReportUploadIdempotencyRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.UserSessionRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

/**
 * 数据维护：过期会话/幂等键清理 + 终态处理任务剪枝（每日一次）。
 * 防止 user_session / report_upload_idempotency / report_processing_job 无限增长。
 * 默认开启；可通过 app.maintenance.enabled=false 关闭。
 */
@Service
class DataMaintenanceService(
    private val sessions: UserSessionRepository,
    private val idempotency: ReportUploadIdempotencyRepository,
    private val jobs: ReportProcessingJobRepository,
    @Value("\${app.maintenance.enabled:true}") private val enabled: Boolean,
    @Value("\${app.maintenance.session-retention-days:30}") private val sessionRetentionDays: Long = 30,
    @Value("\${app.maintenance.job-retention-days:30}") private val jobRetentionDays: Long = 30,
) {

    @Scheduled(fixedDelayString = "\${app.maintenance.interval-ms:86400000}")
    @Transactional
    fun scheduledMaintenance() {
        if (!enabled) return
        val now = OffsetDateTime.now()
        val expiredSessions = sessions.deleteExpired(now)
        val revokedSessions = sessions.deleteRevokedBefore(now.minusDays(sessionRetentionDays))
        val expiredIdempotency = idempotency.deleteExpired(now)
        val prunedJobs = jobs.pruneTerminalBefore(now.minusDays(jobRetentionDays))
        if (expiredSessions + revokedSessions + expiredIdempotency + prunedJobs > 0) {
            println("DataMaintenance: sessions=$expiredSessions+$revokedSessions idempotency=$expiredIdempotency jobs=$prunedJobs")
        }
    }
}
