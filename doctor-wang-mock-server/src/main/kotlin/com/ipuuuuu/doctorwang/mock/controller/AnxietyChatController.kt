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

/**
 * 安心话聊（疑病症/大病焦虑缓解）——健康辅助功能，不替代心理治疗或就医。
 * 会话记录独立于普通 AI 问诊（conversation="anxiety"）。
 */
@RestController
@RequestMapping("/api")
class AnxietyChatController(
    private val chatService: ChatService,
) {
    @GetMapping("/v1/chat/anxiety/history")
    fun anxietyHistory(): List<ChatMessageResponse> = chatService.getHistory(conversation = "anxiety")

    @PostMapping("/v1/chat/anxiety")
    fun anxietyChat(@RequestBody request: ChatRequest): ChatResponse = chatService.anxietyMessage(request.message)
}
