package com.ipuuuuu.doctorwang.mock.controller

import com.ipuuuuu.doctorwang.mock.config.AuthContext
import com.ipuuuuu.doctorwang.mock.config.AuthenticatedUser
import com.ipuuuuu.doctorwang.mock.model.ChatResponse
import com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity
import com.ipuuuuu.doctorwang.mock.service.ChatService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * 安心话聊端点契约：POST /api/v1/chat/anxiety（需认证；返回 message/warnings/citations/traceId）。
 */
class AnxietyChatControllerTest {

    private val chatService = mock(ChatService::class.java)
    private lateinit var mockMvc: MockMvc
    private val user = AppUserEntity(id = 3, userId = "u3", account = "a", passwordHash = "h", name = "n", age = 1, height = 1, weight = 1f)

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(AnxietyChatController(chatService)).build()
        AuthContext.set(AuthenticatedUser("tok", "sid", user))
    }

    @AfterEach
    fun tearDown() { AuthContext.clear() }

    @Test
    fun `anxiety chat returns reassurance response shape`() {
        doReturn(ChatResponse(
            message = "从你现有的检查结果看，没有支持该担忧的证据；如果焦虑影响生活，建议咨询心理科。",
            warnings = listOf("健康辅助不能替代医生诊断；如有紧急症状请立即就医。"),
            citations = emptyList(),
            traceId = "tr-1",
        )).`when`(chatService).anxietyMessage("我总担心自己得了重病")

        mockMvc.perform(
            post("/api/v1/chat/anxiety")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"message":"我总担心自己得了重病"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").isString)
            .andExpect(jsonPath("$.warnings[0]").exists())
            .andExpect(jsonPath("$.citations").isArray)
            .andExpect(jsonPath("$.traceId").value("tr-1"))
    }

}
