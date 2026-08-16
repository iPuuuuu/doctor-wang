package com.ipuuuuu.doctorwang.mock.persistence.repository

import com.ipuuuuu.doctorwang.mock.persistence.entity.MedicalReportEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.ReportProcessingJobEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.domain.Pageable
import java.util.UUID

interface ReportProcessingJobRepository : JpaRepository<ReportProcessingJobEntity, UUID> {
    fun findTopByReportOrderByCreatedAtDesc(report: MedicalReportEntity): ReportProcessingJobEntity?
    fun deleteAllByReport(report: MedicalReportEntity)
    @Query(value = "select * from report_processing_job j where j.status in ('STORED','RETRYING') and coalesce(j.next_attempt_at, now()) <= :at and (j.lease_expires_at is null or j.lease_expires_at < :at) order by j.created_at for update skip locked", nativeQuery = true)
    fun findClaimableForUpdate(at: java.time.OffsetDateTime, pageable: Pageable): List<ReportProcessingJobEntity>
    @Modifying
    @Query(value = "update report_processing_job set status = 'RETRYING', next_attempt_at = :at, locked_at = null, locked_by = null, lease_expires_at = null, updated_at = :at where status = 'PROCESSING' and lease_expires_at < :at", nativeQuery = true)
    fun recoverExpired(at: java.time.OffsetDateTime): Int

    @Modifying
    @Query(value = "delete from report_processing_job where status in ('COMPLETED','FAILED') and finished_at is not null and finished_at < :cutoff", nativeQuery = true)
    fun pruneTerminalBefore(cutoff: java.time.OffsetDateTime): Int
}
