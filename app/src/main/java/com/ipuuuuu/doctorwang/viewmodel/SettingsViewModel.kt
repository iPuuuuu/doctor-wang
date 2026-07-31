package com.ipuuuuu.doctorwang.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ipuuuuu.doctorwang.data.repository.DoctorWangRepository
import com.ipuuuuu.doctorwang.data.repository.RemoteDoctorWangRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val serverUrl: String = "",
    val isSaving: Boolean = false,
    val isTestingConnection: Boolean = false,
    val isLoggingOut: Boolean = false,
    val connectionMessage: String? = null,
    val errorMessage: String? = null,
    val loggedOut: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: DoctorWangRepository,
    private val remoteRepository: RemoteDoctorWangRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(serverUrl = remoteRepository.getServerUrl())
        }
    }

    fun updateServerUrl(value: String) {
        _uiState.value = _uiState.value.copy(serverUrl = value, errorMessage = null, connectionMessage = null)
    }

    fun saveServerUrl() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null, connectionMessage = null)
            runCatching { remoteRepository.saveServerUrl(_uiState.value.serverUrl) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        connectionMessage = "服务地址已保存",
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = error.message ?: "服务地址保存失败",
                    )
                }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingConnection = true, errorMessage = null, connectionMessage = null)
            remoteRepository.testConnection()
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isTestingConnection = false,
                        connectionMessage = "连接成功，可以在手机上继续使用。",
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isTestingConnection = false,
                        errorMessage = error.message ?: "连接失败",
                    )
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoggingOut = true)
            repository.logout()
            _uiState.value = _uiState.value.copy(isLoggingOut = false, loggedOut = true)
        }
    }
}
