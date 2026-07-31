package com.ipuuuuu.doctorwang.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ipuuuuu.doctorwang.data.model.MedicalReport
import com.ipuuuuu.doctorwang.data.repository.DoctorWangRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReportDetailUiState(
    val report: MedicalReport? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
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
            runCatching { repository.getReport(reportId) }
                .onSuccess { report ->
                    _uiState.value = ReportDetailUiState(
                        report = report,
                        isLoading = false,
                    )
                }
                .onFailure { error ->
                    _uiState.value = ReportDetailUiState(
                        isLoading = false,
                        errorMessage = error.message ?: "报告加载失败",
                    )
                }
        }
    }
}
