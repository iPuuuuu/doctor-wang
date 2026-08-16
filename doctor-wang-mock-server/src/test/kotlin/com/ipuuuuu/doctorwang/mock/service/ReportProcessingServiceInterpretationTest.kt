package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.ApiException
import com.ipuuuuu.doctorwang.mock.config.AuthContext
import com.ipuuuuu.doctorwang.mock.config.AuthenticatedUser
import com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.MedicalReportEntity
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportFindingRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportSuggestionRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.ReportProcessingJobRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.ReportUploadIdempotencyRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportFileRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.web.multipart.MultipartFile
import java.util.Optional

/**
 * 报告解读重新生成（ReportProcessingService.regenerateInterpretation）：
 * 调用共享服务并写回摘要/发现/建议。
 */
class ReportProcessingServiceInterpretationTest {

    private val reports = mock(MedicalReportRepository::class.java)
    private val files = mock(MedicalReportFileRepository::class.java)
    private val jobs = mock(ReportProcessingJobRepository::class.java)
    private val findings = mock(MedicalReportFindingRepository::class.java)
    private val suggestions = mock(MedicalReportSuggestionRepository::class.java)
    private val storage = mock(FileStorageService::class.java)
    private val idempotency = mock(ReportUploadIdempotencyRepository::class.java)
    private val interpretationService = mock(ReportInterpretationService::class.java)

    private val user = AppUserEntity(id = 7, userId = "u7", account = "r@test.local", passwordHash = "h", name = "测试", age = 30, height = 170, weight = 65f)
    private val report = MedicalReportEntity(id = 11, user = user, title = "体检", reportDate = "", reportType = "",
        summary = "旧摘要", status = "COMPLETED")

    private fun service() = ReportProcessingService(
        reports, files, jobs, findings, suggestions, storage, idempotency,
        com.fasterxml.jackson.databind.ObjectMapper(), ReportJobStateMachine(), mock(VirusScanner::class.java),
        mock(CompensationCleanupService::class.java), interpretationService,
    )

    @BeforeEach
    fun setUp() { AuthContext.set(AuthenticatedUser("tok", "sid", user)) }
    @AfterEach
    fun tearDown() { AuthContext.clear() }

    @Test
    fun `regenerate writes back summary findings and suggestions`() {
        `when`(reports.findById(11L)).thenReturn(Optional.of(report))
        `when`(interpretationService.generate(any(MedicalReportEntity::class.java) ?: report, any(String::class.java) ?: "report:7:11"))
            .thenReturn(ReportInterpretation.Interpretation("新摘要", listOf("发现一"), listOf("建议一")))
        `when`(findings.findAllByReportOrderByIdAsc(report)).thenReturn(emptyList())
        `when`(suggestions.findAllByReportOrderByIdAsc(report)).thenReturn(emptyList())

        service().regenerateInterpretation(11L)

        assertEquals("新摘要", report.summary)
        verify(findings).save(any())
        verify(suggestions).save(any())
        verify(reports).save(report)
    }

    @Test
    fun `regenerate surfaces friendly error when interpretation service fails`() {
        `when`(reports.findById(11L)).thenReturn(Optional.of(report))
        `when`(interpretationService.generate(any(MedicalReportEntity::class.java) ?: report, any(String::class.java) ?: "report:7:11"))
            .thenThrow(RuntimeException("ai down"))

        val error = assertThrows(ApiException::class.java) {
            service().regenerateInterpretation(11L)
        }
        assertEquals("INTERPRETATION_FAILED", error.code)
    }
}
