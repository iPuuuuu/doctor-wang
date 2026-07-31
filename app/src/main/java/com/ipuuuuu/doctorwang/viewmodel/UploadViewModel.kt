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
        )
    }

    fun upload() {
        val uri = _uiState.value.selectedUri ?: run {
            _uiState.value = _uiState.value.copy(errorMessage = "请先选择文件")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, errorMessage = null)
            repository.uploadReport(uri)
                .onSuccess { reportId ->
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        uploadSuccess = true,
                        uploadedReportId = reportId,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        errorMessage = error.message ?: "上传失败",
                    )
                }
        }
    }
}
