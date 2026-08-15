package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.AiServiceProperties
import com.ipuuuuu.doctorwang.mock.config.AuthContext
import com.ipuuuuu.doctorwang.mock.config.AuthenticatedUser
import com.ipuuuuu.doctorwang.mock.model.AiRagRequest
import com.ipuuuuu.doctorwang.mock.model.AiRagResponse
import com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.ChatMessageEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.HealthProfileDiseaseEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.MedicalReportEntity
import com.ipuuuuu.doctorwang.mock.persistence.repository.ChatMessageRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.HealthProfileDiseaseRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.data.domain.PageImpl
import java.time.OffsetDateTime

/**
 * 安心话聊（anxiety 模式）：请求构造（mode/healthProfile）、无报告引导、安全兜底。
 */
class ChatServiceTest {

    private val chatRepo = mock(ChatMessageRepository::class.java)
    private val reportRepo = mock(MedicalReportRepository::class.java)
    private val diseaseRepo = mock(HealthProfileDiseaseRepository::class.java)
    private val aiClient = mock(AiReportClient::class.java)
    private val props = AiServiceProperties(enabled = true, baseUrl = "http://ai:8000", internalToken = "t")

    private val user = AppUserEntity(id = 7, userId = "u7", account = "anxiety@test.local", passwordHash = "h",
        name = "安心", age = 32, height = 170, weight = 65f)

    private fun service() = ChatService(chatRepo, reportRepo, diseaseRepo, aiClient, props)

    @BeforeEach
    fun setUp() { AuthContext.set(AuthenticatedUser("tok", "sid", user)) }
    @AfterEach
    fun tearDown() { AuthContext.clear() }

    @Test
    fun `anxiety request carries mode and health profile with history diseases`() {
        val report = MedicalReportEntity(id = 9, user = user, title = "体检", reportDate = "", reportType = "",
            summary = "s", status = "COMPLETED")
        doReturn(listOf(report)).`when`(reportRepo).findAllByUserOrderByCreatedAtDescIdDesc(any(AppUserEntity::class.java) ?: user)
        doReturn(listOf(HealthProfileDiseaseEntity(user = user, diseaseName = "胃炎")))
            .`when`(diseaseRepo).findAllByUserOrderByIdAsc(any(AppUserEntity::class.java) ?: user)
        doReturn(AiRagResponse("r", "OK", "LOW", "不要过度担心，从你的检查看没有支持肿瘤的证据。",
            citations = emptyList(), warnings = listOf("w"), needsReview = false, traceId = "tr"))
            .`when`(aiClient).query(any(AiRagRequest::class.java) ?: AiRagRequest("", 0, 0, "", listOf(), traceId = "t"))

        service().anxietyMessage("我总担心胃里长了东西")

        // 捕获请求参数校验 mode/healthProfile
        val captor = org.mockito.ArgumentCaptor.forClass(AiRagRequest::class.java)
        org.mockito.Mockito.verify(aiClient).query(captor.capture() ?: AiRagRequest("", 0, 0, "", listOf(), traceId = "t"))
        val req = captor.value
        assertEquals("anxiety", req.mode)
        assertTrue(req.healthProfile.contains("32 岁"))
        assertTrue(req.healthProfile.contains("胃炎"))
        assertTrue(req.namespaceHints.contains("report:7:9"))
        // 消息持久化（user + assistant 各一条）
        verify(chatRepo, org.mockito.Mockito.times(2)).save(any(ChatMessageEntity::class.java) ?: ChatMessageEntity(user = user, role = "user", content = "x"))
    }

    @Test
    fun `anxiety without reports returns gentle guidance`() {
        doReturn(emptyList<MedicalReportEntity>()).`when`(reportRepo).findAllByUserOrderByCreatedAtDescIdDesc(any(AppUserEntity::class.java) ?: user)
        val resp = service().anxietyMessage("我总担心生病")
        assertTrue(resp.message.contains("上传最近一次的报告"))
        assertEquals(0, resp.citations.size)
        org.mockito.Mockito.verifyNoInteractions(aiClient)
    }

    @Test
    fun `anxiety falls back to safe mock when ai unavailable`() {
        val report = MedicalReportEntity(id = 9, user = user, title = "体检", reportDate = "", reportType = "",
            summary = "s", status = "COMPLETED")
        doReturn(listOf(report)).`when`(reportRepo).findAllByUserOrderByCreatedAtDescIdDesc(any(AppUserEntity::class.java) ?: user)
        doReturn(emptyList<HealthProfileDiseaseEntity>()).`when`(diseaseRepo).findAllByUserOrderByIdAsc(any(AppUserEntity::class.java) ?: user)
        org.mockito.Mockito.doThrow(AiServiceUnavailable("test")).`when`(aiClient).query(any(AiRagRequest::class.java) ?: AiRagRequest("", 0, 0, "", listOf(), traceId = "t"))

        val resp = service().anxietyMessage("我总担心生病")

        assertTrue(resp.message.contains("良性的"))
        assertTrue(resp.warnings.isNotEmpty())
    }
}
