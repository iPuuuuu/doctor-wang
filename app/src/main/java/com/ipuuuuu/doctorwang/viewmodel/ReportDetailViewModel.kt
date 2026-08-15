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
    val isDeleting: Boolean = false,
    val isRetrying: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadedFile: java.io.File? = null,
    val deleted: Boolean = false,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false,
)

@HiltViewModel
class ReportDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DoctorWangRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
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

    fun downloadFile() {
        if (_uiState.value.isDownloading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDownloading = true, errorMessage = null)
            repository.downloadReportFile(reportId)
                .onSuccess { body ->
                    runCatching {
                        val dir = java.io.File(context.cacheDir, "report_files").apply { mkdirs() }
                        // 用报告标题里的扩展名（若有）决定文件名与 MIME，图片报告也能正确打开
                        val ext = _uiState.value.report?.title?.substringAfterLast('.', "")
                            ?.takeIf { it.isNotBlank() && it.length <= 5 && it.all { c -> c.isLetterOrDigit() } }
                            ?: "pdf"
                        val file = java.io.File(dir, "report-$reportId.$ext")
                        body.byteStream().use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        }
                        file
                    }.onSuccess { file ->
                        _uiState.value = _uiState.value.copy(isDownloading = false, downloadedFile = file)
                    }.onFailure {
                        _uiState.value = _uiState.value.copy(isDownloading = false, errorMessage = "下载失败，请稍后再试")
                    }
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        errorMessage = error.message ?: "下载失败，请稍后再试",
                    )
                }
        }
    }

    fun clearDownloadedFile() {
        _uiState.value = _uiState.value.copy(downloadedFile = null)
    }

    fun retry() {
        if (_uiState.value.isRetrying) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRetrying = true, errorMessage = null)
            repository.retryReport(reportId)
                .onSuccess { status ->
                    _uiState.value = _uiState.value.copy(isRetrying = false, reportStatus = status)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isRetrying = false,
                        errorMessage = error.message ?: "重试失败，请稍后再试",
                    )
                }
        }
    }

    fun delete() {
        if (_uiState.value.isDeleting) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true, errorMessage = null)
            repository.deleteReport(reportId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isDeleting = false, deleted = true)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        errorMessage = error.message ?: "删除失败，请稍后再试",
                    )
                }
        }
    }

    private companion object {
        const val STATUS_POLL_INTERVAL_MS = 2_000L
        const val MAX_STATUS_POLL_ATTEMPTS = 5
    }
}
