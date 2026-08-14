package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.AiServiceProperties
import com.ipuuuuu.doctorwang.mock.config.AuthContext
import com.ipuuuuu.doctorwang.mock.config.TraceIdContext
import com.ipuuuuu.doctorwang.mock.model.AiRagRequest
import com.ipuuuuu.doctorwang.mock.model.ChatMessageResponse
import com.ipuuuuu.doctorwang.mock.model.ChatResponse
import com.ipuuuuu.doctorwang.mock.persistence.entity.ChatMessageEntity
import com.ipuuuuu.doctorwang.mock.persistence.repository.ChatMessageRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ChatService(
    private val chatMessageRepository: ChatMessageRepository,
    private val medicalReportRepository: MedicalReportRepository,
    private val aiClient: AiReportClient,
    private val aiProps: AiServiceProperties,
) {
    @Transactional(readOnly = true)
    fun getHistory(): List<ChatMessageResponse> {
        val user = AuthContext.get()?.user ?: throw SecurityException("未登录或登录已失效")
        return chatMessageRepository.findAllByUserOrderByCreatedAtAscIdAsc(user).map {
            ChatMessageResponse(
                id = it.id.toString(),
                content = it.content,
                role = it.role,
                timestamp = it.createdAt.toInstant().toEpochMilli(),
            )
        }
    }

    @Transactional
    fun sendMessage(message: String): ChatResponse {
        val user = AuthContext.get()?.user ?: throw SecurityException("未登录或登录已失效")
        chatMessageRepository.save(
            ChatMessageEntity(user = user, role = "user", content = message),
        )

        val reply = if (aiProps.enabled) tryAiReply(message, user) else mockReply(message, user)
        chatMessageRepository.save(
            ChatMessageEntity(user = user, role = "assistant", content = reply.message),
        )
        return reply
    }

    private fun tryAiReply(message: String, user: com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity): ChatResponse {
        val latestReport = try { medicalReportRepository.findAllByUserOrderByCreatedAtDescIdDesc(user).firstOrNull() } catch (_: Exception) { null }
        if (latestReport == null) return mockReply(message, user)

        return try {
            val request = AiRagRequest(
                requestId = UUID.randomUUID().toString(),
                userId = user.id,
                reportId = latestReport.id,
                question = message,
                namespaceHints = listOf("report:${user.userId}:${latestReport.id}"),
                traceId = TraceIdContext.get(),
            )
            val aiResponse = aiClient.query(request)
            val validated = AiSafetyGate.validate(aiResponse, latestReport.id)

            ChatResponse(
                message = validated.summary,
                warnings = validated.warnings,
                citations = validated.citations.map { c ->
                    com.ipuuuuu.doctorwang.mock.model.ChatCitationResponse(
                        sourceType = c.sourceType,
                        sourceId = c.sourceId,
                        pageNo = c.pageNo,
                        excerpt = c.excerpt,
                        confidence = c.confidence,
                    )
                },
                traceId = validated.traceId,
            )
        } catch (_: AiServiceUnavailable) {
            logger.warn("AI service unavailable, falling back to mock reply")
            mockReply(message, user)
        } catch (_: Exception) {
            logger.warn("AI query failed, falling back to mock reply")
            mockReply(message, user)
        }
    }

    private fun mockReply(message: String, user: com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity): ChatResponse {
        val normalized = message.lowercase()
        val reply = when {
            normalized.contains("胃") -> "根据你的健康档案，你之前有胃炎记录。建议近期保持清淡饮食，避免辛辣刺激；如果疼痛持续或加重，请及时线下就医。"
            normalized.contains("肝") || normalized.contains("脂肪肝") -> "结合你既往的轻度脂肪肝记录，建议近期控制油脂摄入、规律运动，并复查肝功能。"
            normalized.contains("头晕") || normalized.contains("乏力") -> "头晕或乏力可能与睡眠、饮食、贫血或近期压力有关。建议先记录持续时间与伴随症状，必要时完善血常规等检查。"
            else -> "我已经记录你的情况。结合${user.name}当前档案信息，建议持续观察症状变化；如症状持续或明显加重，请及时线下就医。"
        }
        return ChatResponse(message = reply, warnings = emptyList(), citations = emptyList(), traceId = TraceIdContext.get())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ChatService::class.java)
    }
}
