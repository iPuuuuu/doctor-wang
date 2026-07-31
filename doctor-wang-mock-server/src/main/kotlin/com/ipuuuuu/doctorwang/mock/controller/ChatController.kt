package com.ipuuuuu.doctorwang.mock.controller

import com.ipuuuuu.doctorwang.mock.model.ChatRequest
import com.ipuuuuu.doctorwang.mock.model.ChatResponse
import com.ipuuuuu.doctorwang.mock.service.MockDataService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class ChatController(
    private val mockDataService: MockDataService,
) {
    @PostMapping("/chat")
    fun chat(@RequestBody request: ChatRequest): ChatResponse {
        return mockDataService.answerQuestion(request.message)
    }
}
