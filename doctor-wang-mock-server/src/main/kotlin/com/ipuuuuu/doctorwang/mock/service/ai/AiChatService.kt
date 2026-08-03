package com.ipuuuuu.doctorwang.mock.service.ai

import com.ipuuuuu.doctorwang.mock.model.AiChatRequest
import com.ipuuuuu.doctorwang.mock.model.AiChatResult
import org.springframework.stereotype.Service

@Service
class AiChatService(
    private val aiProvider: AiProvider,
) {
    fun answer(userId: String, message: String): AiChatResult {
        return aiProvider.chat(
            AiChatRequest(
                userId = userId,
                message = message.trim(),
            ),
        )
    }
}
