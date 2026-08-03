package com.ipuuuuu.doctorwang.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ipuuuuu.doctorwang.data.model.ReportListItem
import com.ipuuuuu.doctorwang.data.repository.DoctorWangRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReportListUiState(
    val reports: List<ReportListItem> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val deletingReportId: Long? = null,
) {
    val isEmpty: Boolean get() = !isLoading && reports.isEmpty()
}

@HiltViewModel
class ReportListViewModel @Inject constructor(
    private val repository: DoctorWangRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportListUiState())
    val uiState: StateFlow<ReportListUiState> = _uiState.asStateFlow()

    init {
        loadReports()
    }

    fun loadReports() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            repository.listReports()
                .onSuccess { reports ->
                    _uiState.value = ReportListUiState(
                        reports = reports,
                        isLoading = false,
                    )
                }
                .onFailure { error ->
                    _uiState.value = ReportListUiState(
                        isLoading = false,
                        errorMessage = error.message ?: "病历列表加载失败",
                    )
                }
        }
    }

    fun deleteReport(reportId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(deletingReportId = reportId, errorMessage = null)
            repository.deleteReport(reportId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        reports = _uiState.value.reports.filterNot { it.reportId == reportId },
                        deletingReportId = null,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        deletingReportId = null,
                        errorMessage = error.message ?: "删除病历失败",
                    )
                }
        }
    }
}
