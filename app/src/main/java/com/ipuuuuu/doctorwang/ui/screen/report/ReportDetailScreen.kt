package com.ipuuuuu.doctorwang.ui.screen.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ipuuuuu.doctorwang.viewmodel.ReportDetailViewModel

@Composable
fun ReportDetailScreen(viewModel: ReportDetailViewModel = hiltViewModel()) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val report = uiState.report

    if (uiState.isLoading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (uiState.errorMessage != null || report == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = uiState.errorMessage ?: "报告加载失败", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(text = "检查报告", style = MaterialTheme.typography.titleMedium)
            Text(text = report.title, style = MaterialTheme.typography.headlineSmall)
            Text(text = "${report.date} · ${report.type}")
        }
        item {
            Text(text = "AI解析", style = MaterialTheme.typography.titleMedium)
            Text(text = report.summary)
        }
        item {
            Text(text = "发现", style = MaterialTheme.typography.titleMedium)
        }
        items(report.findings) {
            Text(text = "• $it")
        }
        item {
            Text(text = "建议", style = MaterialTheme.typography.titleMedium)
        }
        items(report.suggestions) {
            Text(text = "• $it")
        }
    }
}
