package com.ipuuuuu.doctorwang.mock.service.ai

import com.ipuuuuu.doctorwang.mock.model.AiChatRequest
import com.ipuuuuu.doctorwang.mock.model.AiChatResult
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
@ConditionalOnProperty(name = ["doctor-wang.ai.provider"], havingValue = "claude")
class ClaudeAiProvider : AiProvider {
    override fun chat(request: AiChatRequest): AiChatResult {
        throw ResponseStatusException(
            HttpStatus.NOT_IMPLEMENTED,
            "Claude provider 尚未完成 SDK 接入，请先使用 mock provider。",
        )
    }
}
