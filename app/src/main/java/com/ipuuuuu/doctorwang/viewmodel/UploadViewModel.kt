package com.ipuuuuu.doctorwang.viewmodel

import android.net.Uri
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

data class UploadUiState(
    val selectedUri: Uri? = null,
    val isUploading: Boolean = false,
    val uploadSuccess: Boolean = false,
    val uploadedReportId: Long? = null,
    val reportStatus: MedicalReport? = null,
    val isPollingStatus: Boolean = false,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false,
)

@HiltViewModel
class UploadViewModel @Inject constructor(
    private val repository: DoctorWangRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    fun onFileSelected(uri: Uri?) {
        _uiState.value = _uiState.value.copy(
            selectedUri = uri,
            errorMessage = null,
            uploadSuccess = false,
            uploadedReportId = null,
            reportStatus = null,
            isPollingStatus = false,
            sessionExpired = false,
        )
    }

    fun upload() {
        val uri = _uiState.value.selectedUri ?: run {
            _uiState.value = _uiState.value.copy(errorMessage = "请先选择文件")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isUploading = true,
                uploadSuccess = false,
                uploadedReportId = null,
                reportStatus = null,
                isPollingStatus = false,
                errorMessage = null,
                sessionExpired = false,
            )
            repository.uploadReport(uri)
                .onSuccess { reportId ->
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        uploadSuccess = true,
                        uploadedReportId = reportId,
                        isPollingStatus = true,
                    )
                    viewModelScope.launch {
                        pollReportStatus(reportId)
                    }
                }
                .onFailure { error ->
                    if (error is SessionExpiredException) {
                        repository.clearSession()
                        _uiState.value = _uiState.value.copy(
                            isUploading = false,
                            isPollingStatus = false,
                            errorMessage = error.message ?: "登录状态已失效，请重新登录",
                            sessionExpired = true,
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isUploading = false,
                            isPollingStatus = false,
                            errorMessage = error.message ?: "上传失败",
                        )
                    }
                }
        }
    }

    private suspend fun pollReportStatus(reportId: Long) {
        repeat(MAX_STATUS_POLLS) { attempt ->
            try {
                val status = repository.getReportStatus(reportId)
                _uiState.value = _uiState.value.copy(
                    reportStatus = status,
                    isPollingStatus = !status.isTerminalStatus(),
                )
                if (status.isTerminalStatus()) {
                    return
                }
            } catch (error: Throwable) {
                if (error is SessionExpiredException) {
                    repository.clearSession()
                    _uiState.value = _uiState.value.copy(
                        isPollingStatus = false,
                        errorMessage = error.message ?: "登录状态已失效，请重新登录",
                        sessionExpired = true,
                    )
                    return
                }
                if (attempt == MAX_STATUS_POLLS - 1) {
                    _uiState.value = _uiState.value.copy(
                        isPollingStatus = false,
                        errorMessage = error.message ?: "报告状态查询失败",
                    )
                }
            }
            if (attempt < MAX_STATUS_POLLS - 1) {
                delay(STATUS_POLL_INTERVAL_MS)
            }
        }
        _uiState.value = _uiState.value.copy(isPollingStatus = false)
    }

    private fun MedicalReport.isTerminalStatus(): Boolean {
        return phase.equals("COMPLETED", ignoreCase = true) ||
            phase.equals("FAILED", ignoreCase = true) ||
            phase.equals("REVIEW_REQUIRED", ignoreCase = true) ||
            summary.equals("COMPLETED", ignoreCase = true) ||
            summary.equals("FAILED", ignoreCase = true) ||
            summary.equals("REVIEW_REQUIRED", ignoreCase = true)
    }

    private companion object {
        const val MAX_STATUS_POLLS = 3
        const val STATUS_POLL_INTERVAL_MS = 1_500L
    }
}
