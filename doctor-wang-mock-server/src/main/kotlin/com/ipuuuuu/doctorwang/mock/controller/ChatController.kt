package com.ipuuuuu.doctorwang.mock.controller

import com.ipuuuuu.doctorwang.mock.model.ChatRequest
import com.ipuuuuu.doctorwang.mock.model.ChatResponse
import com.ipuuuuu.doctorwang.mock.service.AccountService
import com.ipuuuuu.doctorwang.mock.service.ai.AiChatService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class ChatController(
    private val accountService: AccountService,
    private val aiChatService: AiChatService,
) {
    @PostMapping("/chat")
    fun chat(
        @RequestHeader("Authorization", required = false) auth: String?,
        @RequestBody request: ChatRequest,
    ): ChatResponse {
        val result = aiChatService.answer(
            userId = accountService.requireUserId(auth),
            message = request.message,
        )
        return ChatResponse(
            answer = result.answer,
            provider = result.provider,
        )
    }
}
