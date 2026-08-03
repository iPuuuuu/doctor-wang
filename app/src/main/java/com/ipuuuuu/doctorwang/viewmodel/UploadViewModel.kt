package com.ipuuuuu.doctorwang.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ipuuuuu.doctorwang.data.repository.DoctorWangRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UploadUiState(
    val selectedUri: Uri? = null,
    val isUploading: Boolean = false,
    val uploadSuccess: Boolean = false,
    val uploadedReportId: Long? = null,
    val errorMessage: String? = null,
    val progressMessage: String = "请选择要上传的病历文件",
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
            progressMessage = if (uri == null) "请选择要上传的病历文件" else "文件已选择，准备上传",
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
                errorMessage = null,
                progressMessage = "正在上传文件，请稍候...",
            )
            repository.uploadReport(uri)
                .onSuccess { reportId ->
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        uploadSuccess = true,
                        uploadedReportId = reportId,
                        progressMessage = "上传完成，正在跳转到报告详情",
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        errorMessage = error.message ?: "上传失败",
                        progressMessage = "上传失败，请检查网络或文件格式后重试",
                    )
                }
        }
    }
}
