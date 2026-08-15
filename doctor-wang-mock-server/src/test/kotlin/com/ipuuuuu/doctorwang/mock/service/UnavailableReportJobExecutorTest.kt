package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.AiServiceProperties
import com.ipuuuuu.doctorwang.mock.model.AiCitation
import com.ipuuuuu.doctorwang.mock.model.AiRagRequest
import com.ipuuuuu.doctorwang.mock.model.AiRagResponse
import com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.MedicalReportEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.MedicalReportFileEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.ReportProcessingJobEntity
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportFileRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.UUID

/**
 * 报告处理 executor（REPORT-INTERPRETATION-001）：
 * 向量化完成后生成循证解读；AI 不可用/无证据时诚实兜底（fail-closed）。
 */
class UnavailableReportJobExecutorTest {

    private val client = mock(AiReportClient::class.java)
    private val files = mock(MedicalReportFileRepository::class.java)
    private val storage = mock(FileStorageService::class.java)
    private val props = AiServiceProperties(enabled = true, baseUrl = "http://ai:8000", internalToken = "t", sourceVersion = "v1")

    private val user = AppUserEntity(id = 7, userId = "u7", account = "r@test.local", passwordHash = "h", name = "测试", age = 30, height = 170, weight = 65f)
    private val report = MedicalReportEntity(id = 11, user = user, title = "腹部超声", reportDate = "2026-06-17", reportType = "腹部超声", summary = "报告正在处理，结果需经医生确认。", status = "PROCESSING")
    private val fileEntity = MedicalReportFileEntity(report = report, storageKey = "k/11", originalFileName = "r.pdf", mimeType = "application/pdf", fileSize = 100L, sha256 = "s")
    private val job = ReportProcessingJobEntity(id = UUID.randomUUID(), report = report, userId = 7, status = "STORED", stage = "STORED", traceId = "t1")

    private fun executor() = UnavailableReportJobExecutor(client, files, storage, props)

    // Kotlin 非空参数 + Mockito any() 需 elvis 回退（项目既有模式）
    private fun anySubmit() = any(com.ipuuuuu.doctorwang.mock.model.AiIngestionRequest::class.java)
        ?: com.ipuuuuu.doctorwang.mock.model.AiIngestionRequest(
            requestId = "r", reportId = 11, userId = 7, namespace = "report:7:11", fileName = "f",
            contentType = "application/pdf", objectKey = "k", fileSize = 1L, sourceVersion = "v1", traceId = "t",
        )
    private fun anyQuery() = any(AiRagRequest::class.java)
        ?: AiRagRequest(requestId = "r", userId = 7, reportId = 11, question = "q", namespaceHints = listOf("report:7:11"), traceId = "t")
    private fun anyIdem() = any(String::class.java) ?: "idem"

    @Test
    fun `completes with grounded interpretation when query returns structured summary`() {
        `when`(files.findByReport(report)).thenReturn(fileEntity)
        `when`(client.submit(anySubmit(), anyIdem())).thenReturn(com.ipuuuuu.doctorwang.mock.model.AiIngestionResponse("j1", "COMPLETED"))
        `when`(client.status("j1")).thenReturn(com.ipuuuuu.doctorwang.mock.model.AiIngestionStatus("j1", "COMPLETED"))
        val aiResponse = AiRagResponse(
            requestId = "r1", status = "OK", riskLevel = "LOW",
            summary = "【摘要】肝脏未见占位。\n【重点发现】1. 肝脏实质回声均匀\n【建议】1. 定期复查",
            citations = listOf(AiCitation("report", "11", "c1", 1, "肝脏实质回声均匀", 0.9, "v1")),
            traceId = "t1",
        )
        `when`(client.query(anyQuery())).thenReturn(aiResponse)

        val result = executor().execute(job)
        assertEquals("COMPLETED", result.status)
        assertTrue(result.summary.orEmpty().contains("肝脏未见占位"))
        assertEquals(listOf("肝脏实质回声均匀"), result.findings)
        assertEquals(listOf("定期复查"), result.suggestions)
        assertNull(result.errorCode)
    }

    @Test
    fun `falls back to honest summary when AI query unavailable`() {
        `when`(files.findByReport(report)).thenReturn(fileEntity)
        `when`(client.submit(anySubmit(), anyIdem())).thenReturn(com.ipuuuuu.doctorwang.mock.model.AiIngestionResponse("j2", "COMPLETED"))
        `when`(client.status("j2")).thenReturn(com.ipuuuuu.doctorwang.mock.model.AiIngestionStatus("j2", "COMPLETED"))
        `when`(client.query(anyQuery())).thenThrow(AiServiceUnavailable("down"))

        val result = executor().execute(job)
        assertEquals("COMPLETED", result.status)
        assertEquals(ReportInterpretation.FALLBACK_SUMMARY, result.summary)
        assertTrue(result.findings.isEmpty())
        assertTrue(result.suggestions.isEmpty())
    }

    @Test
    fun `falls back when AI returns insufficient evidence`() {
        `when`(files.findByReport(report)).thenReturn(fileEntity)
        `when`(client.submit(anySubmit(), anyIdem())).thenReturn(com.ipuuuuu.doctorwang.mock.model.AiIngestionResponse("j3", "COMPLETED"))
        `when`(client.status("j3")).thenReturn(com.ipuuuuu.doctorwang.mock.model.AiIngestionStatus("j3", "COMPLETED"))
        val insufficient = AiRagResponse("r3", "INSUFFICIENT_EVIDENCE", "LOW", "当前报告中没有足够的可检索资料回答这个问题。", citations = emptyList(), traceId = "t3")
        `when`(client.query(anyQuery())).thenReturn(insufficient)

        val result = executor().execute(job)
        assertEquals("COMPLETED", result.status)
        assertEquals(ReportInterpretation.FALLBACK_SUMMARY, result.summary)
    }

    @Test
    fun `fails when AI job not completed`() {
        `when`(files.findByReport(report)).thenReturn(fileEntity)
        `when`(client.submit(anySubmit(), anyIdem())).thenReturn(com.ipuuuuu.doctorwang.mock.model.AiIngestionResponse("j4", "COMPLETED"))
        `when`(client.status("j4")).thenReturn(com.ipuuuuu.doctorwang.mock.model.AiIngestionStatus("j4", "FAILED", errorCode = "PROCESSING_ERROR"))

        val result = executor().execute(job)
        assertEquals("FAILED", result.status)
        assertEquals("PROCESSING_ERROR", result.errorCode)
    }

    @Test
    fun `fails when AI service not configured`() {
        val disabled = AiServiceProperties(enabled = false, baseUrl = "", internalToken = "")
        val result = UnavailableReportJobExecutor(client, files, storage, disabled).execute(job)
        assertEquals("FAILED", result.status)
        assertEquals("AI_SERVICE_NOT_CONFIGURED", result.errorCode)
    }
}
