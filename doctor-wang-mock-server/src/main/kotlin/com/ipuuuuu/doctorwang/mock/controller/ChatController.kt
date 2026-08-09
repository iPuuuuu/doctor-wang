package com.ipuuuuu.doctorwang.mock.controller

import com.ipuuuuu.doctorwang.mock.model.ChatMessageResponse
import com.ipuuuuu.doctorwang.mock.model.ChatRequest
import com.ipuuuuu.doctorwang.mock.model.ChatResponse
import com.ipuuuuu.doctorwang.mock.service.BackendService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class ChatController(
    private val backendService: BackendService,
) {
    @GetMapping("/chat/history")
    fun getChatHistory(): List<ChatMessageResponse> {
        return backendService.getChatHistory()
    }

    @PostMapping("/chat")
    fun chat(@RequestBody request: ChatRequest): ChatResponse {
        return backendService.answerQuestion(request.message)
    }
}
