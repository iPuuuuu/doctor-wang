package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.ApiException
import com.ipuuuuu.doctorwang.mock.config.AuthContext
import com.ipuuuuu.doctorwang.mock.config.AuthenticatedUser
import com.ipuuuuu.doctorwang.mock.model.ReportUploadResponse
import com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.MedicalReportEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.ReportProcessingJobEntity
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportFileRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportFindingRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportSuggestionRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.ReportProcessingJobRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.ReportUploadIdempotencyRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/**
 * SEC-001：上传流程的扫描决策映射——
 * 检出恶意文件 → MALWARE_DETECTED；扫描器不可用 → SCAN_UNAVAILABLE（fail-closed）；干净 → 继续持久化。
 *
 * 注：Kotlin 接口非空参数 + Mockito 需用 `any(Class) ?: 回退值` 模式
 * （平台类型 null 经 elvis 转非空，避免 Kotlin 空检查），与 BackendServiceTest 一致。
 */
class ReportUploadScanBehaviorTest {

    private val reports = mock(MedicalReportRepository::class.java)
    private val files = mock(MedicalReportFileRepository::class.java)
    private val jobs = mock(ReportProcessingJobRepository::class.java)
    private val findings = mock(MedicalReportFindingRepository::class.java)
    private val suggestions = mock(MedicalReportSuggestionRepository::class.java)
    private val storage = mock(FileStorageService::class.java)
    private val idempotency = mock(ReportUploadIdempotencyRepository::class.java)
    private val stateMachine = mock(ReportJobStateMachine::class.java)
    private val cleanup = mock(CompensationCleanupService::class.java)
    private val scanner = mock(VirusScanner::class.java)

    private val user = AppUserEntity(
        id = 1, userId = "u1", account = "alice", passwordHash = "h",
        name = "Alice", age = 30, height = 170, weight = 60f,
    )

    private val pdf = MockMultipartFile(
        "file", "report.pdf", "application/pdf",
        byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x20, 0x00), // %PDF-1.4
    )

    private fun service() = ReportProcessingService(
        reports, files, jobs, findings, suggestions, storage, idempotency,
        ObjectMapper(), stateMachine, scanner, cleanup, mock(ReportInterpretationService::class.java),
    )

    @BeforeEach
    fun setUp() {
        AuthContext.set(AuthenticatedUser(sessionToken = "t", sessionId = "sid", user = user))
    }

    @AfterEach
    fun tearDown() {
        AuthContext.clear()
    }

    @Test
    fun `malware scan result rejects upload with MALWARE_DETECTED`() {
        doReturn(ScanResult(clean = false, reason = ScanReasons.MALWARE)).`when`(scanner).scan(org.mockito.ArgumentMatchers.any(MultipartFile::class.java) ?: pdf)
        val e = assertThrows(ApiException::class.java) { service().upload(pdf, null, null, null) }
        assertEquals("MALWARE_DETECTED", e.code)
        verifyNoInteractions(reports, files, jobs, storage)
    }

    @Test
    fun `scanner unavailable fails closed with SCAN_UNAVAILABLE`() {
        doReturn(ScanResult(clean = false, reason = ScanReasons.SCANNER_UNAVAILABLE)).`when`(scanner).scan(org.mockito.ArgumentMatchers.any(MultipartFile::class.java) ?: pdf)
        val e = assertThrows(ApiException::class.java) { service().upload(pdf, null, null, null) }
        assertEquals("SCAN_UNAVAILABLE", e.code)
        verifyNoInteractions(reports, files, jobs, storage)
    }

    @Test
    fun `clean scan proceeds to persistence`() {
        doReturn(ScanResult(clean = true)).`when`(scanner).scan(org.mockito.ArgumentMatchers.any(MultipartFile::class.java) ?: pdf)
        val savedReport = MedicalReportEntity(
            id = 1, user = user, title = "report.pdf", reportDate = "", reportType = "",
            summary = "报告正在处理，结果需经医生确认。", status = "PROCESSING",
        )
        doReturn(savedReport).`when`(reports).save(org.mockito.ArgumentMatchers.any(MedicalReportEntity::class.java))
        doReturn(StoredFile(storageKey = "key-1", sha256 = "a".repeat(64))).`when`(storage).store(org.mockito.ArgumentMatchers.any(String::class.java) ?: "u1", anyLong(), org.mockito.ArgumentMatchers.any(MultipartFile::class.java) ?: pdf)
        doReturn(ReportProcessingJobEntity(
            id = UUID.randomUUID(), report = savedReport, userId = 1L, status = "STORED", stage = "STORED",
            phase = "STORED", traceId = "trace-1",
        )).`when`(jobs).save(org.mockito.ArgumentMatchers.any(ReportProcessingJobEntity::class.java))

        val response = service().upload(pdf, null, null, null)

        assertEquals(1L, response.reportId)
        assertEquals("STORED", response.status)
        verify(reports).save(org.mockito.ArgumentMatchers.any(MedicalReportEntity::class.java))
        verify(storage).store(org.mockito.ArgumentMatchers.any(String::class.java) ?: "u1", anyLong(), org.mockito.ArgumentMatchers.any(MultipartFile::class.java) ?: pdf)
        verify(jobs).save(org.mockito.ArgumentMatchers.any(ReportProcessingJobEntity::class.java))
    }

    @Test
    fun `scan failure happens before any persistence`() {
        doReturn(ScanResult(clean = false, reason = ScanReasons.MALWARE)).`when`(scanner).scan(org.mockito.ArgumentMatchers.any(MultipartFile::class.java) ?: pdf)
        assertThrows(ApiException::class.java) { service().upload(pdf, "2026-06-17", "影像报告", "key-1") }
        verifyNoInteractions(reports, files, jobs, storage, idempotency)
    }
}
