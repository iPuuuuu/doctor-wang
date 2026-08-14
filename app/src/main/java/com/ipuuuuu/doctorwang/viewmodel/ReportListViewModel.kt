package com.ipuuuuu.doctorwang.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ipuuuuu.doctorwang.data.api.ReportListItem
import com.ipuuuuu.doctorwang.data.repository.DoctorWangRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReportListUiState(
    val reports: List<ReportListItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val currentPage: Int = 0,
    val hasMore: Boolean = true,
)

@HiltViewModel
class ReportListViewModel @Inject constructor(
    private val repository: DoctorWangRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportListUiState())
    val uiState: StateFlow<ReportListUiState> = _uiState.asStateFlow()

    init {
        loadReports()
    }

    fun loadReports(page: Int = 0) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            repository.listReports(page = page)
                .onSuccess { pageData ->
                    _uiState.value = _uiState.value.copy(
                        reports = if (page == 0) pageData.items else _uiState.value.reports + pageData.items,
                        isLoading = false,
                        currentPage = page,
                        hasMore = page < pageData.totalPages - 1,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = error.message ?: "加载报告列表失败")
                }
        }
    }

    fun loadMore() {
        if (!_uiState.value.isLoading && _uiState.value.hasMore) {
            loadReports(_uiState.value.currentPage + 1)
        }
    }

    fun refresh() {
        loadReports(page = 0)
    }
}
