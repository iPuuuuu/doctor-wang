package com.ipuuuuu.doctorwang.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ipuuuuu.doctorwang.data.repository.DoctorWangRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PasswordChangeUiState(
    val oldPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val changeSuccess: Boolean = false,
)

@HiltViewModel
class PasswordChangeViewModel @Inject constructor(
    private val repository: DoctorWangRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PasswordChangeUiState())
    val uiState: StateFlow<PasswordChangeUiState> = _uiState.asStateFlow()

    fun updateOldPassword(value: String) { _uiState.value = _uiState.value.copy(oldPassword = value, errorMessage = null) }
    fun updateNewPassword(value: String) { _uiState.value = _uiState.value.copy(newPassword = value, errorMessage = null) }
    fun updateConfirmPassword(value: String) { _uiState.value = _uiState.value.copy(confirmPassword = value, errorMessage = null) }

    fun changePassword() {
        if (_uiState.value.isLoading) return
        val current = _uiState.value
        if (current.oldPassword.isBlank() || current.newPassword.isBlank()) {
            _uiState.value = current.copy(errorMessage = "请填写旧密码和新密码")
            return
        }
        if (current.newPassword.length < 6) {
            _uiState.value = current.copy(errorMessage = "新密码长度不能少于6位")
            return
        }
        if (current.newPassword != current.confirmPassword) {
            _uiState.value = current.copy(errorMessage = "两次输入的新密码不一致")
            return
        }

        viewModelScope.launch {
            _uiState.value = current.copy(isLoading = true, errorMessage = null)
            repository.changePassword(current.oldPassword, current.newPassword)
                .onSuccess {
                    // 后端 changePassword 会吊销该用户除当前会话外的所有 JTI（ADR-0003
                    // revokeAllExcept）；密码已变更，本地主动清 session 并引导重新登录，
                    // 避免继续使用旧凭据建立的会话与失效 token。
                    repository.clearSession()
                    _uiState.value = _uiState.value.copy(isLoading = false, changeSuccess = true)
                }
                .onFailure { error -> _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = error.message ?: "密码修改失败") }
        }
    }
}
