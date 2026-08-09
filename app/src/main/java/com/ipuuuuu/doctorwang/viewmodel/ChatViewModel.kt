package com.ipuuuuu.doctorwang.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ipuuuuu.doctorwang.data.model.ConversationSummary
import com.ipuuuuu.doctorwang.data.model.Message
import com.ipuuuuu.doctorwang.data.model.MessageStatus
import com.ipuuuuu.doctorwang.data.repository.DoctorWangRepository
import com.ipuuuuu.doctorwang.data.repository.SessionExpiredException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val conversationSummary: ConversationSummary? = null,
    val input: String = "",
    val isSending: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: DoctorWangRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.getHistoryMessages() }
                .onSuccess { messages ->
                    _uiState.value = _uiState.value.copy(
                        messages = messages,
                        conversationSummary = buildConversationSummary(messages),
                        isLoading = false,
                    )
                }
                .onFailure { error ->
                    if (error is SessionExpiredException) {
                        repository.clearSession()
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "登录状态已失效，请重新登录",
                            sessionExpired = true,
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "聊天记录加载失败",
                        )
                    }
                }
        }
    }

    fun updateInput(value: String) {
        _uiState.value = _uiState.value.copy(input = value)
    }

    fun sendMessage() {
        val content = _uiState.value.input.trim()
        if (content.isEmpty() || _uiState.value.isSending) return

        val userMessage = Message(
            id = "user-${System.currentTimeMillis()}",
            content = content,
            role = "user",
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENT,
        )

        val updatedMessages = _uiState.value.messages + userMessage
        _uiState.value = _uiState.value.copy(
            messages = updatedMessages,
            conversationSummary = buildConversationSummary(updatedMessages),
            input = "",
            isSending = true,
            errorMessage = null,
            sessionExpired = false,
        )

        viewModelScope.launch {
            repository.sendMessage(content, updatedMessages)
                .onSuccess { response ->
                    val messagesWithReply = _uiState.value.messages + response
                    _uiState.value = _uiState.value.copy(
                        messages = messagesWithReply,
                        conversationSummary = buildConversationSummary(messagesWithReply),
                        isSending = false,
                    )
                }
                .onFailure { error ->
                    if (error is SessionExpiredException) {
                        repository.clearSession()
                        _uiState.value = _uiState.value.copy(
                            isSending = false,
                            errorMessage = error.message ?: "登录状态已失效，请重新登录",
                            sessionExpired = true,
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isSending = false,
                            errorMessage = error.message ?: "发送失败",
                        )
                    }
                }
        }
    }

    private fun buildConversationSummary(messages: List<Message>): ConversationSummary? {
        val userMessages = messages.filter { it.role == "user" }
        if (userMessages.isEmpty()) return null

        val latestUser = userMessages.last()
        val topic = summarizeTopic(latestUser.content)
        val detail = userMessages.takeLast(3).joinToString(separator = " · ") { it.content.take(12) }
        val latestAdvice = messages.lastOrNull { it.role == "assistant" }
            ?.content
            ?.take(28)
            ?.let { "最近建议：$it" }
            ?: "暂无建议，继续描述症状可获得更具体回复"

        return ConversationSummary(
            title = topic,
            detail = if (detail.isBlank()) latestUser.content else detail,
            latestAdvice = latestAdvice,
            latestTimestamp = latestUser.timestamp,
        )
    }

    private fun summarizeTopic(content: String): String {
        val normalized = content.trim()
        return when {
            normalized.contains("胃") -> "胃部不适"
            normalized.contains("头疼") || normalized.contains("头痛") -> "头痛咨询"
            normalized.contains("发烧") || normalized.contains("发热") -> "发热咨询"
            normalized.contains("脂肪肝") || normalized.contains("肝") -> "肝脏健康"
            normalized.length <= 8 -> normalized
            else -> normalized.take(8)
        }
    }
}
