package com.ipuuuuu.doctorwang.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ipuuuuu.doctorwang.data.model.Message
import com.ipuuuuu.doctorwang.data.model.MessageStatus
import com.ipuuuuu.doctorwang.data.repository.DoctorWangRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: DoctorWangRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            runCatching { repository.getHistoryMessages() }
                .onSuccess { messages ->
                    _uiState.value = _uiState.value.copy(messages = messages, isLoading = false, errorMessage = null)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "聊天记录加载失败",
                    )
                }
        }
    }

    fun updateInput(value: String) {
        _uiState.value = _uiState.value.copy(input = value)
    }

    fun sendMessage() {
        val content = _uiState.value.input.trim()
        if (content.isEmpty() || _uiState.value.isSending) return
        send(content)
    }

    fun retryMessage(messageId: String) {
        val failedMessage = _uiState.value.messages.firstOrNull { it.id == messageId && it.status == MessageStatus.FAILED }
            ?: return
        if (_uiState.value.isSending) return

        val retriedMessages = _uiState.value.messages.map {
            if (it.id == messageId) it.copy(status = MessageStatus.SENDING, timestamp = System.currentTimeMillis()) else it
        }
        _uiState.value = _uiState.value.copy(messages = retriedMessages, errorMessage = null)
        send(failedMessage.content, existingUserMessageId = messageId)
    }

    private fun send(content: String, existingUserMessageId: String? = null) {
        val now = System.currentTimeMillis()
        val userMessage = Message(
            id = existingUserMessageId ?: "user-$now",
            content = content,
            role = "user",
            timestamp = now,
            status = MessageStatus.SENDING,
        )

        val updatedMessages = if (existingUserMessageId == null) {
            _uiState.value.messages + userMessage
        } else {
            _uiState.value.messages.map { if (it.id == existingUserMessageId) userMessage else it }
        }

        _uiState.value = _uiState.value.copy(
            messages = updatedMessages,
            input = if (existingUserMessageId == null) "" else _uiState.value.input,
            isSending = true,
            errorMessage = null,
        )

        viewModelScope.launch {
            repository.sendMessage(content, updatedMessages)
                .onSuccess { response ->
                    val sentMessages = _uiState.value.messages.map {
                        if (it.id == userMessage.id) it.copy(status = MessageStatus.SENT) else it
                    }
                    _uiState.value = _uiState.value.copy(
                        messages = sentMessages + response,
                        isSending = false,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages.map {
                            if (it.id == userMessage.id) it.copy(status = MessageStatus.FAILED) else it
                        },
                        isSending = false,
                        errorMessage = error.message ?: "发送失败",
                    )
                }
        }
    }
}
