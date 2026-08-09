package com.ipuuuuu.doctorwang.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ipuuuuu.doctorwang.data.model.UserProfile
import com.ipuuuuu.doctorwang.data.repository.DoctorWangRepository
import com.ipuuuuu.doctorwang.data.repository.SessionExpiredException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: UserProfile? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: DoctorWangRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { repository.getHealthProfile() }
                .onSuccess { profile ->
                    _uiState.value = ProfileUiState(
                        profile = profile,
                        isLoading = false,
                    )
                }
                .onFailure { error ->
                    if (error is SessionExpiredException) {
                        repository.clearSession()
                        _uiState.value = ProfileUiState(
                            isLoading = false,
                            errorMessage = error.message ?: "登录状态已失效，请重新登录",
                            sessionExpired = true,
                        )
                    } else {
                        _uiState.value = ProfileUiState(
                            isLoading = false,
                            errorMessage = error.message ?: "健康档案加载失败",
                        )
                    }
                }
        }
    }
}
