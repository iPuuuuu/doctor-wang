package com.ipuuuuu.doctorwang.data.api

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ANDROID-001 A5 — Android ↔ Backend API 契约测试。
 *
 * 验证客户端 DTO 与后端 JSON 契约一致：字段名、@SerialName 映射、
 * 默认值（缺省字段）以及 NetworkProvider 中配置的
 * ignoreUnknownKeys=true 行为。
 *
 * 注意：本文件仅使用已有依赖（JUnit + kotlinx-serialization），
 * 不修改任何业务代码。
 */
class DoctorWangApiContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun loginRequest_serializesWithContractFieldNames() {
        val payload = LoginRequest(account = "alice", password = "s3cret")
        assertEquals(
            """{"account":"alice","password":"s3cret"}""",
            json.encodeToString(payload),
        )
    }

    @Test
    fun loginResponse_decodesTokenAndUserId() {
        val decoded = json.decodeFromString<LoginResponse>(
            """{"token":"jwt-token","userId":"u1001"}""",
        )
        assertEquals("jwt-token", decoded.token)
        assertEquals("u1001", decoded.userId)
    }

    @Test
    fun registerRequest_serializesWithAllFields() {
        val payload = RegisterRequest(
            account = "alice",
            password = "s3cret-pass",
            name = "Alice",
            age = 30,
            height = 170,
            weight = 60.5f,
        )
        assertEquals(
            """{"account":"alice","password":"s3cret-pass","name":"Alice","age":30,"height":170,"weight":60.5}""",
            json.encodeToString(payload),
        )
    }

    @Test
    fun profileResponse_decodesSerialNameHistoryDiseasesAndRecords() {
        val decoded = json.decodeFromString<UserProfileResponse>(
            """
            {
              "userId": "u1001",
              "name": "王先生",
              "age": 25,
              "height": 170,
              "weight": 73,
              "historyDiseases": ["胃炎"],
              "records": [{"date": "2026-06", "type": "腹部超声"}]
            }
            """.trimIndent(),
        )
        assertEquals("u1001", decoded.userId)
        assertEquals("王先生", decoded.name)
        assertEquals(25, decoded.age)
        assertEquals(170, decoded.height)
        assertEquals(73f, decoded.weight)
        assertEquals(listOf("胃炎"), decoded.historyDiseases)
        assertEquals(1, decoded.records.size)
        assertEquals("2026-06", decoded.records[0].date)
        assertEquals("腹部超声", decoded.records[0].type)
    }

    @Test
    fun chatResponse_appliesDefaultsWhenOptionalFieldsMissing() {
        val decoded = json.decodeFromString<ChatResponse>(
            """{"message":"请尽快就医","warnings":[],"citations":[]}""",
        )
        assertEquals("请尽快就医", decoded.message)
        assertTrue(decoded.warnings.isEmpty())
        assertTrue(decoded.citations.isEmpty())
        assertEquals(null, decoded.traceId)
    }

    @Test
    fun chatResponse_decodesWarningsCitationsAndTraceId() {
        val decoded = json.decodeFromString<ChatResponse>(
            """
            {
              "message": "建议复查肝功能",
              "warnings": ["非诊断结论"],
              "citations": [
                {
                  "sourceType": "report",
                  "sourceId": "10001",
                  "pageNo": 2,
                  "excerpt": "轻度脂肪肝",
                  "confidence": 0.92
                }
              ],
              "traceId": "trace-abc"
            }
            """.trimIndent(),
        )
        assertEquals("建议复查肝功能", decoded.message)
        assertEquals(listOf("非诊断结论"), decoded.warnings)
        assertEquals("trace-abc", decoded.traceId)
        assertEquals(1, decoded.citations.size)
        assertEquals("report", decoded.citations[0].sourceType)
        assertEquals("10001", decoded.citations[0].sourceId)
        assertEquals(2, decoded.citations[0].pageNo)
        assertEquals(0.92, decoded.citations[0].confidence!!, 0.0001)
    }

    @Test
    fun chatMessageResponse_decodesTimestampAndRole() {
        val decoded = json.decodeFromString<ChatMessageResponse>(
            """{"id":"m1","content":"你好","role":"user","timestamp":1700000000000}""",
        )
        assertEquals("m1", decoded.id)
        assertEquals("你好", decoded.content)
        assertEquals("user", decoded.role)
        assertEquals(1700000000000, decoded.timestamp)
    }

    @Test
    fun medicalReportResponse_decodesSerialNameReportDateAndReportType() {
        val decoded = json.decodeFromString<MedicalReportResponse>(
            """
            {
              "reportId": 10001,
              "title": "腹部超声",
              "reportDate": "2026-06-17",
              "reportType": "影像报告",
              "summary": "轻度脂肪肝倾向",
              "findings": ["轻度脂肪肝"],
              "suggestions": ["控制体重"],
              "disclaimer": "本内容仅用于健康辅助，不能替代医生诊断；如有紧急或高风险症状，请及时就医。"
            }
            """.trimIndent(),
        )
        assertEquals(10001, decoded.reportId)
        assertEquals("腹部超声", decoded.title)
        assertEquals("2026-06-17", decoded.date)
        assertEquals("影像报告", decoded.type)
        assertEquals("轻度脂肪肝倾向", decoded.summary)
        assertEquals(listOf("轻度脂肪肝"), decoded.findings)
        assertEquals(listOf("控制体重"), decoded.suggestions)
        assertEquals("本内容仅用于健康辅助，不能替代医生诊断；如有紧急或高风险症状，请及时就医。", decoded.disclaimer)
    }

    @Test
    fun reportListResponse_decodesPaginationFields() {
        val decoded = json.decodeFromString<ReportListResponse>(
            """
            {
              "items": [
                {
                  "reportId": 10001,
                  "title": "腹部超声",
                  "reportDate": "2026-06-17",
                  "reportType": "影像报告",
                  "status": "PROCESSED",
                  "summary": "轻度脂肪肝",
                  "createdAt": "2026-06-17T10:00:00"
                }
              ],
              "page": 0,
              "size": 20,
              "totalPages": 1,
              "totalElements": 1
            }
            """.trimIndent(),
        )
        assertEquals(1, decoded.items.size)
        assertEquals(10001, decoded.items[0].reportId)
        assertEquals("腹部超声", decoded.items[0].title)
        assertEquals("2026-06-17", decoded.items[0].reportDate)
        assertEquals("影像报告", decoded.items[0].reportType)
        assertEquals("PROCESSED", decoded.items[0].status)
        assertEquals("2026-06-17T10:00:00", decoded.items[0].createdAt)
        assertEquals(0, decoded.page)
        assertEquals(20, decoded.size)
        assertEquals(1, decoded.totalPages)
        assertEquals(1L, decoded.totalElements)
    }

    @Test
    fun uploadResponse_decodesNullableFields() {
        val decoded = json.decodeFromString<UploadResponse>(
            """{"reportId":42,"status":"PROCESSING","processingJobId":"job-1","statusUrl":"/api/v1/reports/42/status"}""",
        )
        assertEquals(42, decoded.reportId)
        assertEquals("PROCESSING", decoded.status)
        assertEquals("job-1", decoded.processingJobId)
        assertEquals("/api/v1/reports/42/status", decoded.statusUrl)
    }

    @Test
    fun reportStatusResponse_decodesBackendContractShape() {
        // 后端真实形状：需复核的 AI 结果 fail closed → status=FAILED + errorCode=REVIEW_REQUIRED
        // （PostgreSqlPollingWorker.fail()），status 从不等于 "REVIEW_REQUIRED"。
        val decoded = json.decodeFromString<ReportStatusResponse>(
            """
            {
              "reportId": 42,
              "processingJobId": "a9f6e1c2-1111-2222-3333-444455556666",
              "status": "FAILED",
              "stage": "ocr",
              "retryable": false,
              "errorCode": "REVIEW_REQUIRED",
              "updatedAt": "2026-08-12T10:00:00Z",
              "traceId": "trace-1"
            }
            """.trimIndent(),
        )
        assertEquals(42, decoded.reportId)
        assertEquals("FAILED", decoded.status)
        assertEquals("ocr", decoded.phase)
        assertEquals(false, decoded.canRetry)
        assertEquals("REVIEW_REQUIRED", decoded.failureCategory)
        assertEquals("trace-1", decoded.traceId)
        // requiresReview 无后端字段：DTO 默认 false，由
        // ReportStatusResponse.toDomain() 依 failureCategory=="REVIEW_REQUIRED" 派生为 true。
        assertEquals(false, decoded.requiresReview)
    }

    @Test
    fun reportStatusResponse_appliesDefaultsWhenOptionalFieldsMissing() {
        val decoded = json.decodeFromString<ReportStatusResponse>(
            """{"reportId":42,"status":"PROCESSING","stage":"upload"}""",
        )
        assertEquals(42, decoded.reportId)
        assertEquals("PROCESSING", decoded.status)
        assertEquals("upload", decoded.phase)
        assertEquals(false, decoded.canRetry)
        assertEquals(null, decoded.failureCategory)
        assertEquals(null, decoded.traceId)
    }

    @Test
    fun passwordChangeRequest_serializesWithContractFieldNames() {
        val payload = PasswordChangeRequest(oldPassword = "old-pass", newPassword = "new-pass")
        assertEquals(
            """{"oldPassword":"old-pass","newPassword":"new-pass"}""",
            json.encodeToString(payload),
        )
    }

    @Test
    fun profileUpdateRequest_omitsNullFields() {
        val payload = ProfileUpdateRequest(name = null, age = 31, height = null, weight = null)
        assertEquals(
            """{"age":31}""",
            json.encodeToString(payload),
        )
    }

    @Test
    fun unknownResponseFieldsAreIgnored_likeNetworkProviderConfig() {
        val decoded = json.decodeFromString<LoginResponse>(
            """{"token":"t","userId":"u","extraField":"should-be-ignored"}""",
        )
        assertEquals("t", decoded.token)
        assertEquals("u", decoded.userId)
    }
}
