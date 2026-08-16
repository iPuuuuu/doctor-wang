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

data class SettingsUiState(
    val isLoggingOut: Boolean = false,
    val loggedOut: Boolean = false,
    val isDeleting: Boolean = false,
    val deleteError: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: DoctorWangRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun logout() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoggingOut = true)
            repository.logout()
            _uiState.value = _uiState.value.copy(isLoggingOut = false, loggedOut = true)
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true, deleteError = null)
            repository.deleteAccount()
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isDeleting = false, loggedOut = true)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        deleteError = error.message ?: "注销失败，请稍后重试",
                    )
                }
        }
    }
}
