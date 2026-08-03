package com.ipuuuuu.doctorwang.mock.service.ai

import com.ipuuuuu.doctorwang.mock.model.AiChatRequest
import com.ipuuuuu.doctorwang.mock.model.AiChatResult

interface AiProvider {
    fun chat(request: AiChatRequest): AiChatResult
}
