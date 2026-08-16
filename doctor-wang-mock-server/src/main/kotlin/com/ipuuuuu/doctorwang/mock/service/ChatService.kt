package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.AiServiceProperties
import com.ipuuuuu.doctorwang.mock.config.AuthContext
import com.ipuuuuu.doctorwang.mock.config.TraceIdContext
import com.ipuuuuu.doctorwang.mock.model.AiRagRequest
import com.ipuuuuu.doctorwang.mock.model.ChatMessageResponse
import com.ipuuuuu.doctorwang.mock.model.ChatResponse
import com.ipuuuuu.doctorwang.mock.persistence.entity.ChatMessageEntity
import com.ipuuuuu.doctorwang.mock.persistence.repository.ChatMessageRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.HealthProfileDiseaseRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ChatService(
    private val chatMessageRepository: ChatMessageRepository,
    private val medicalReportRepository: MedicalReportRepository,
    private val healthProfileDiseaseRepository: HealthProfileDiseaseRepository,
    private val aiClient: AiReportClient,
    private val aiProps: AiServiceProperties,
    private val audit: AiAuditService,
) {
    @Transactional(readOnly = true)
    fun getHistory(conversation: String = "general"): List<ChatMessageResponse> {
        val user = AuthContext.get()?.user ?: throw SecurityException("未登录或登录已失效")
        return chatMessageRepository.findAllByUserAndConversationOrderByCreatedAtAscIdAsc(user, conversation).map {
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
            ChatMessageEntity(user = user, role = "user", content = message, conversation = "general"),
        )

        val reply = if (aiProps.enabled) tryAiReply(message, user) else mockReply(message, user)
        chatMessageRepository.save(
            ChatMessageEntity(user = user, role = "assistant", content = reply.message, conversation = "general"),
        )
        audit.record(user = user, eventType = "AI_CHAT", traceId = reply.traceId ?: "none",
            modelVersion = aiProps.sourceVersion,
            metadata = mapOf("conversation" to "general", "mode" to "general",
                "warnings" to reply.warnings.size, "citations" to reply.citations.size,
                "aiEnabled" to aiProps.enabled))
        return reply
    }

    /**
     * 安心话聊（疑病症/大病焦虑缓解）：结合用户真实健康档案与最近报告，
     * 由 AI 以专门的安全提示词回应，帮助降低对重大疾病的过度担忧。
     * 仍需报告作为检索依据；无报告时给出温和引导而非凭空安慰。
     */
    @Transactional
    fun anxietyMessage(message: String): ChatResponse {
        val user = AuthContext.get()?.user ?: throw SecurityException("未登录或登录已失效")
        chatMessageRepository.save(
            ChatMessageEntity(user = user, role = "user", content = message, conversation = "anxiety"),
        )
        val reply = if (aiProps.enabled) tryAnxietyReply(message, user) else mockAnxietyReply(message)
        chatMessageRepository.save(
            ChatMessageEntity(user = user, role = "assistant", content = reply.message, conversation = "anxiety"),
        )
        audit.record(user = user, eventType = "AI_CHAT", traceId = reply.traceId ?: "none",
            modelVersion = aiProps.sourceVersion,
            metadata = mapOf("conversation" to "anxiety", "mode" to "anxiety",
                "warnings" to reply.warnings.size, "citations" to reply.citations.size,
                "aiEnabled" to aiProps.enabled))
        return reply
    }

    private fun tryAnxietyReply(message: String, user: com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity): ChatResponse {
        val latestReport = try { medicalReportRepository.findAllByUserOrderByCreatedAtDescIdDesc(user).firstOrNull() } catch (_: Exception) { null }
        if (latestReport == null) {
            return ChatResponse(
                message = "我理解你很担心。要帮你安心地分析，我需要结合你的实际检查报告——请先上传最近一次的报告（或让医生录入检查结果），我就能从你的真实结果出发和你聊，而不是凭空猜测。",
                warnings = listOf("健康辅助不能替代就医；如有紧急症状请立即就医。"),
                citations = emptyList(),
                traceId = TraceIdContext.get(),
            )
        }
        val diseases = try { healthProfileDiseaseRepository.findAllByUserOrderByIdAsc(user).map { it.diseaseName } } catch (_: Exception) { emptyList() }
        val healthProfile = buildString {
            append("年龄 ${user.age} 岁，身高 ${user.height}cm，体重 ${user.weight}kg")
            if (diseases.isNotEmpty()) append("，既往史：${diseases.joinToString("、")}")
        }
        return try {
            val request = AiRagRequest(
                requestId = UUID.randomUUID().toString(),
                userId = user.id,
                reportId = latestReport.id,
                question = message,
                namespaceHints = listOf("report:${user.id}:${latestReport.id}"),
                mode = "anxiety",
                healthProfile = healthProfile,
                conversationHistory = recentConversation(user, "anxiety"),
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
            logger.warn("AI service unavailable, falling back to anxiety mock reply")
            mockAnxietyReply(message)
        } catch (_: Exception) {
            logger.warn("AI anxiety query failed, falling back to anxiety mock reply")
            mockAnxietyReply(message)
        }
    }

    private fun mockAnxietyReply(message: String): ChatResponse {
        return ChatResponse(
            message = "我听到了你的担心。在 AI 助手暂时不可用时，请记住：很多让身体不适的原因都是常见且良性的（睡眠、压力、饮食、情绪都可能引起）。请先对照自己的真实检查结果，如果症状持续或加重，及时就医才是最安心的做法；如果这种担心经常出现且影响生活，也可以和心理科医生聊聊。",
            warnings = listOf("健康辅助不能替代医生诊断；如有紧急症状请立即就医（120）。"),
            citations = emptyList(),
            traceId = TraceIdContext.get(),
        )
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
                // namespace 契约 report:{数字 userId}:{reportId}（与 ingest 一致）；user.userId 是字符串标识，不可用于 namespace
                namespaceHints = listOf("report:${user.id}:${latestReport.id}"),
                conversationHistory = recentConversation(user, "general"),
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

    // 面向用户：AI 不可用时给出诚实提示，不做关键词"假装回答"。
    /** 取最近 6 条消息（不含本次）作为多轮上下文，格式化为可读文本。 */
    private fun recentConversation(user: com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity, conversation: String): String {
        return try {
            chatMessageRepository.findAllByUserAndConversationOrderByCreatedAtAscIdAsc(user, conversation)
                .asReversed().take(6).asReversed()
                .joinToString("\n") { "${if (it.role == "user") "用户" else "助手"}: ${it.content.take(200)}" }
        } catch (_: Exception) { "" }
    }

    private fun mockReply(message: String, user: com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity): ChatResponse {
        return ChatResponse(
            message = "AI 健康助手暂时不可用，请稍后再试。如有不适请及时线下就医；如遇紧急症状请立即拨打 120。",
            warnings = listOf("健康辅助不能替代医生诊断。"),
            citations = emptyList(),
            traceId = TraceIdContext.get(),
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ChatService::class.java)
    }
}
