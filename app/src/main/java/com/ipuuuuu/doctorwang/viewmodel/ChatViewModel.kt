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
        viewModelScope.launch {
            runCatching { repository.getHistoryMessages() }
                .onSuccess { messages ->
                    _uiState.value = _uiState.value.copy(messages = messages, isLoading = false)
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
            input = "",
            isSending = true,
            errorMessage = null,
        )

        viewModelScope.launch {
            repository.sendMessage(content, updatedMessages)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + response,
                        isSending = false,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        errorMessage = error.message ?: "发送失败",
                    )
                }
        }
    }
}
