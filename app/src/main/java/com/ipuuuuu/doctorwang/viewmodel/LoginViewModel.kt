package com.ipuuuuu.doctorwang.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ipuuuuu.doctorwang.data.repository.DoctorWangRepository
import com.ipuuuuu.doctorwang.storage.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val account: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loginSuccess: Boolean = false,
    val showServerGuide: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: DoctorWangRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showServerGuide = !sessionManager.hasSeenServerGuide())
        }
    }

    fun updateAccount(value: String) {
        _uiState.value = _uiState.value.copy(account = value, errorMessage = null)
    }

    fun updatePassword(value: String) {
        _uiState.value = _uiState.value.copy(password = value, errorMessage = null)
    }

    fun dismissServerGuide() {
        viewModelScope.launch {
            sessionManager.markServerGuideSeen()
            _uiState.value = _uiState.value.copy(showServerGuide = false)
        }
    }

    fun login() {
        val current = _uiState.value
        if (current.account.isBlank() || current.password.isBlank()) {
            _uiState.value = current.copy(errorMessage = "请输入账号和密码")
            return
        }
        viewModelScope.launch {
            _uiState.value = current.copy(isLoading = true, errorMessage = null)
            repository.login(current.account, current.password)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, loginSuccess = true)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "登录失败",
                    )
                }
        }
    }
}
