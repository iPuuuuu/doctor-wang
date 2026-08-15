package com.ipuuuuu.doctorwang.mock.controller

import com.ipuuuuu.doctorwang.mock.model.ChatMessageResponse
import com.ipuuuuu.doctorwang.mock.model.ChatRequest
import com.ipuuuuu.doctorwang.mock.model.ChatResponse
import com.ipuuuuu.doctorwang.mock.service.ChatService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class ChatController(
    private val chatService: ChatService,
) {
    @GetMapping("/chat/history")
    fun getChatHistory(): List<ChatMessageResponse> = chatService.getHistory(conversation = "general")

    @PostMapping("/chat")
    fun chat(@RequestBody request: ChatRequest): ChatResponse = chatService.sendMessage(request.message)
}
