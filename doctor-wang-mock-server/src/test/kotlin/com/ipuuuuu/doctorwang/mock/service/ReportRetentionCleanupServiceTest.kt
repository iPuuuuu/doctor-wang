package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.MedicalReportEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.MedicalReportFileEntity
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportFileRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.ReportProcessingJobRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doReturn
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.PageRequest
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.data.domain.PageImpl
import java.time.OffsetDateTime
import java.util.Collections

/**
 * 对象生命周期（ADR-0008 后续）：保留期清理服务的单元测试。
 */
class ReportRetentionCleanupServiceTest {

    private val reports = mock(MedicalReportRepository::class.java)
    private val files = mock(MedicalReportFileRepository::class.java)
    private val jobs = mock(ReportProcessingJobRepository::class.java)
    private val storage = mock(FileStorageService::class.java)

    private val user = AppUserEntity(id = 1, userId = "u1", account = "a", passwordHash = "h", name = "n", age = 1, height = 1, weight = 1f)

    private fun report(id: Long, updatedDaysAgo: Long) = MedicalReportEntity(
        id = id, user = user, title = "r$id", reportDate = "", reportType = "",
        summary = "s", status = "COMPLETED",
        updatedAt = OffsetDateTime.now().minusDays(updatedDaysAgo),
    )

    private fun service(retentionDays: Long) =
        ReportRetentionCleanupService(reports, files, jobs, storage, retentionDays)

    @Test
    fun `disabled retention deletes nothing`() {
        val summary = service(retentionDays = 0).cleanupExpired()
        assertEquals(0, summary.deleted)
        assertEquals("retention-disabled", summary.status)
        verifyNoInteractions(reports, files, jobs, storage)
    }

    @Test
    fun `expired reports are deleted with files and jobs`() {
        val expired = report(id = 1, updatedDaysAgo = 400)
        doReturn(PageImpl(listOf(expired))).`when`(reports).findAllByUpdatedAtBefore(any(OffsetDateTime::class.java) ?: OffsetDateTime.MIN, any(Pageable::class.java) ?: PageRequest.of(0, 100))
        doReturn(MedicalReportFileEntity(report = expired, storageKey = "key-1", originalFileName = "r1", mimeType = "application/pdf", fileSize = 10, sha256 = "a".repeat(64)))
            .`when`(files).findByReport(any(MedicalReportEntity::class.java) ?: expired)

        val summary = service(retentionDays = 180).cleanupExpired()

        assertEquals(1, summary.deleted)
        assertEquals("ok", summary.status)
        verify(storage).delete("key-1")
        verify(jobs).deleteAllByReport(expired)
        verify(files).deleteAllByReport(expired)
        verify(reports).delete(expired)
    }

    @Test
    fun `recent reports are untouched`() {
        doReturn(PageImpl(Collections.emptyList<MedicalReportEntity>())).`when`(reports).findAllByUpdatedAtBefore(any(OffsetDateTime::class.java) ?: OffsetDateTime.MIN, any(Pageable::class.java) ?: PageRequest.of(0, 100))

        val summary = service(retentionDays = 180).cleanupExpired()

        assertEquals(0, summary.deleted)
        verify(reports, never()).delete(any())
        verifyNoInteractions(storage)
    }

    @Test
    fun `report without stored file still cleaned`() {
        val expired = report(id = 2, updatedDaysAgo = 300)
        doReturn(PageImpl(listOf(expired))).`when`(reports).findAllByUpdatedAtBefore(any(OffsetDateTime::class.java) ?: OffsetDateTime.MIN, any(Pageable::class.java) ?: PageRequest.of(0, 100))
        doReturn(null).`when`(files).findByReport(any(MedicalReportEntity::class.java) ?: expired)

        val summary = service(retentionDays = 30).cleanupExpired()

        assertEquals(1, summary.deleted)
        verify(storage, never()).delete(any(String::class.java) ?: "k")
        verify(reports).delete(expired)
    }
}
