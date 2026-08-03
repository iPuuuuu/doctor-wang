package com.ipuuuuu.doctorwang.mock.service.ai

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.ipuuuuu.doctorwang.mock.model.AiChatRequest
import com.ipuuuuu.doctorwang.mock.model.AiChatResult
import com.ipuuuuu.doctorwang.mock.model.ChatRule
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["doctor-wang.ai.provider"], havingValue = "mock", matchIfMissing = true)
class MockAiProvider(
    private val objectMapper: ObjectMapper,
    @Value("\${doctor-wang.ai.mock.default-answer:我已经记录你的情况。建议结合既往病史继续观察，如症状持续或加重，请及时线下就医。}")
    private val defaultAnswer: String,
) : AiProvider {
    private var chatRules: List<ChatRule> = emptyList()

    @PostConstruct
    fun load() {
        chatRules = objectMapper.readValue(
            ClassPathResource("mock-data/chat-rules.json").inputStream,
            object : TypeReference<List<ChatRule>>() {},
        )
    }

    override fun chat(request: AiChatRequest): AiChatResult {
        val rule = chatRules.firstOrNull { request.message.contains(it.keyword, ignoreCase = true) }
        return AiChatResult(
            answer = rule?.answer ?: defaultAnswer,
            provider = "mock",
        )
    }
}
