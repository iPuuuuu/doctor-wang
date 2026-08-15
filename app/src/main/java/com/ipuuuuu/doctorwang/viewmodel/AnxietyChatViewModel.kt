package com.ipuuuuu.doctorwang.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ipuuuuu.doctorwang.data.model.Message
import com.ipuuuuu.doctorwang.data.repository.DoctorWangRepository
import com.ipuuuuu.doctorwang.data.repository.SessionExpiredException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AnxietyChatUiState(
    val messages: List<Message> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false,
)

/**
 * 安心话聊：帮助缓解疑病症/大病焦虑。结合用户的健康档案与检查报告，
 * 由 AI 以专门的安全提示词回应；提醒真实症状需就医，焦虑影响生活可咨询心理科。
 */
@HiltViewModel
class AnxietyChatViewModel @Inject constructor(
    private val repository: DoctorWangRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnxietyChatUiState())
    val uiState: StateFlow<AnxietyChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.getAnxietyHistoryMessages() }
                .onSuccess { messages ->
                    _uiState.value = _uiState.value.copy(messages = messages, isLoading = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "加载聊天记录失败",
                    )
                }
        }
    }

    fun updateInput(value: String) {
        _uiState.value = _uiState.value.copy(input = value, errorMessage = null)
    }

    fun send() {
        val content = _uiState.value.input.trim()
        if (content.isEmpty() || _uiState.value.isSending) return
        val current = _uiState.value
        _uiState.value = current.copy(
            input = "",
            isSending = true,
            errorMessage = null,
            messages = current.messages + Message(
                id = "user-${current.messages.size + 1}",
                content = content,
                role = "user",
                timestamp = System.currentTimeMillis(),
            ),
        )
        viewModelScope.launch {
            repository.sendAnxietyMessage(content, _uiState.value.messages)
                .onSuccess { reply ->
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        messages = _uiState.value.messages + reply,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        sessionExpired = error is SessionExpiredException,
                        errorMessage = error.message ?: "发送失败，请稍后再试",
                    )
                }
        }
    }
}
