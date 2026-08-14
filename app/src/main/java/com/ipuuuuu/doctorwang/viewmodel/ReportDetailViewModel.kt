package com.ipuuuuu.doctorwang.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ipuuuuu.doctorwang.data.model.MedicalReport
import com.ipuuuuu.doctorwang.data.repository.DoctorWangRepository
import com.ipuuuuu.doctorwang.data.repository.SessionExpiredException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReportDetailUiState(
    val report: MedicalReport? = null,
    val reportStatus: MedicalReport? = null,
    val isLoading: Boolean = true,
    val isRefreshingStatus: Boolean = false,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false,
)

@HiltViewModel
class ReportDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DoctorWangRepository,
) : ViewModel() {

    private val reportId: Long = checkNotNull(savedStateHandle["reportId"])

    private val _uiState = MutableStateFlow(ReportDetailUiState())
    val uiState: StateFlow<ReportDetailUiState> = _uiState.asStateFlow()

    init {
        loadReport()
    }

    fun loadReport() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, sessionExpired = false)
            runCatching { repository.getReport(reportId) }
                .onSuccess { report ->
                    _uiState.value = _uiState.value.copy(
                        report = report,
                        reportStatus = report,
                        isLoading = false,
                        isRefreshingStatus = false,
                    )
                    viewModelScope.launch {
                        refreshReportStatus()
                    }
                }
                .onFailure { error ->
                    if (error is SessionExpiredException) {
                        repository.clearSession()
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshingStatus = false,
                            errorMessage = error.message ?: "登录状态已失效，请重新登录",
                            sessionExpired = true,
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshingStatus = false,
                            errorMessage = error.message ?: "报告加载失败",
                        )
                    }
                }
        }
    }

    private suspend fun refreshReportStatus() {
        _uiState.value = _uiState.value.copy(isRefreshingStatus = true)
        try {
            var status = repository.getReportStatus(reportId)
            _uiState.value = _uiState.value.copy(
                reportStatus = status,
                isRefreshingStatus = false,
            )
            // PROCESSING/RETRYING 时有限轮询（与 UploadViewModel 轮询策略一致），
            // 避免用户手动"重试"才能看到最新状态。
            var attempts = 0
            while (status.summary in setOf("PROCESSING", "RETRYING") && attempts < MAX_STATUS_POLL_ATTEMPTS) {
                delay(STATUS_POLL_INTERVAL_MS)
                status = repository.getReportStatus(reportId)
                attempts++
                _uiState.value = _uiState.value.copy(reportStatus = status)
            }
        } catch (error: Throwable) {
            if (error is SessionExpiredException) {
                repository.clearSession()
                _uiState.value = _uiState.value.copy(
                    isRefreshingStatus = false,
                    errorMessage = error.message ?: "登录状态已失效，请重新登录",
                    sessionExpired = true,
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isRefreshingStatus = false,
                    errorMessage = error.message ?: "报告状态刷新失败",
                )
            }
        }
    }

    private companion object {
        const val STATUS_POLL_INTERVAL_MS = 2_000L
        const val MAX_STATUS_POLL_ATTEMPTS = 5
    }
}
