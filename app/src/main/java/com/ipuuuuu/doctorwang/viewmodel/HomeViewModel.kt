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

private const val HEALTHY_BMI_MIN = 18.5f
private const val HEALTHY_BMI_MAX = 24.0f

data class HomeUiState(
    val userName: String = "",
    val bmiText: String = "--",
    val statusSummary: String = "暂无数据",
    val recentRecordText: String = "暂无检查记录",
    val historyMessages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DoctorWangRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, sessionExpired = false)
            runCatching {
                val profile = repository.getProfile()
                val messages = repository.getHistoryMessages()
                val bmi = profile.weight / ((profile.height / 100f) * (profile.height / 100f))
                val summary = profile.historyDiseases.firstOrNull() ?: "暂无异常"
                val recentRecord = profile.records.firstOrNull()?.let { "${it.date} ${it.type}" } ?: "暂无检查记录"
                HomeUiState(
                    userName = profile.name,
                    bmiText = String.format("%.1f", bmi),
                    statusSummary = when {
                        bmi > HEALTHY_BMI_MAX -> "$summary · BMI偏高"
                        bmi < HEALTHY_BMI_MIN -> "$summary · BMI偏低"
                        else -> summary
                    },
                    recentRecordText = recentRecord,
                    historyMessages = messages,
                    isLoading = false,
                )
            }.onSuccess { state ->
                _uiState.value = state
            }.onFailure { error ->
                if (error is SessionExpiredException) {
                    repository.clearSession()
                    _uiState.value = HomeUiState(
                        isLoading = false,
                        errorMessage = error.message ?: "登录状态已失效，请重新登录",
                        sessionExpired = true,
                    )
                } else {
                    _uiState.value = HomeUiState(
                        isLoading = false,
                        errorMessage = error.message ?: "首页加载失败",
                    )
                }
            }
        }
    }
}
