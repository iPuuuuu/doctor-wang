package com.ipuuuuu.doctorwang.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ipuuuuu.doctorwang.data.model.UserProfile
import com.ipuuuuu.doctorwang.data.repository.DoctorWangRepository
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
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
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
                    _uiState.value = ProfileUiState(
                        isLoading = false,
                        errorMessage = error.message ?: "健康档案加载失败",
                    )
                }
        }
    }

    fun beginEdit() {
        _uiState.value = _uiState.value.copy(errorMessage = null, isEditing = true)
    }

    fun cancelEdit() {
        _uiState.value = _uiState.value.copy(errorMessage = null, isEditing = false)
    }

    fun save(profile: UserProfile) {
        val sanitized = profile.sanitize()
        validate(sanitized)?.let { message ->
            _uiState.value = _uiState.value.copy(errorMessage = message)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            repository.updateProfile(sanitized)
                .onSuccess { updated ->
                    _uiState.value = ProfileUiState(profile = updated, isEditing = false, isLoading = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = error.message ?: "保存失败",
                    )
                }
        }
    }

    private fun validate(profile: UserProfile): String? {
        return when {
            profile.name.isBlank() -> "姓名不能为空"
            profile.age !in 1..120 -> "年龄需在 1 到 120 岁之间"
            profile.height !in 50..260 -> "身高需在 50 到 260 cm 之间"
            profile.weight !in 2f..300f -> "体重需在 2 到 300 kg 之间"
            else -> null
        }
    }
}

private fun UserProfile.sanitize(): UserProfile {
    return copy(
        name = name.trim(),
        historyDiseases = historyDiseases
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct(),
    )
}
