package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportFileRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.ReportProcessingJobRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

data class CleanupSummary(val deleted: Int, val status: String)

/**
 * 对象生命周期（ADR-0008 后续、SEC-001 follow-up）：
 * 按报告更新时间清理超过保留期的报告及其文件/任务记录。
 *
 * 医疗数据默认不自动删除：`app.file-storage.retention-days=0`（默认）时清理完全禁用；
 * 仅当运维显式设置保留期后才启用。清理只删除过期对象，绝不触碰未过期数据。
 */
@Service
class ReportRetentionCleanupService(
    private val reports: MedicalReportRepository,
    private val files: MedicalReportFileRepository,
    private val jobs: ReportProcessingJobRepository,
    private val storage: FileStorageService,
    @Value("\${app.file-storage.retention-days:0}") private val retentionDays: Long = 0,
) {

    @Scheduled(fixedDelayString = "\${app.file-storage.cleanup-interval-ms:86400000}")
    fun scheduledCleanup() {
        if (retentionDays <= 0) return
        cleanupExpired()
    }

    fun cleanupExpired(): CleanupSummary {
        if (retentionDays <= 0) return CleanupSummary(deleted = 0, status = "retention-disabled")
        val cutoff = OffsetDateTime.now().minusDays(retentionDays)
        var deleted = 0
        var page = 0
        while (true) {
            val batch = reports.findAllByUpdatedAtBefore(cutoff, PageRequest.of(page, CLEANUP_BATCH_SIZE))
            if (batch.isEmpty) break
            batch.forEach { report ->
                files.findByReport(report)?.let { storage.delete(it.storageKey) }
                jobs.deleteAllByReport(report)
                files.deleteAllByReport(report)
                reports.delete(report)
                deleted++
            }
            if (!batch.hasNext()) break
            page++
        }
        return CleanupSummary(deleted = deleted, status = "ok")
    }

    private companion object {
        const val CLEANUP_BATCH_SIZE = 100
    }
}
