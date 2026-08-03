package com.ipuuuuu.doctorwang.ui.screen.report

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ipuuuuu.doctorwang.data.model.ReportListItem
import com.ipuuuuu.doctorwang.viewmodel.ReportListViewModel

@Composable
fun ReportListScreen(
    onOpenReport: (Long) -> Unit,
    viewModel: ReportListViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

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

    if (uiState.errorMessage != null && uiState.reports.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = uiState.errorMessage, color = MaterialTheme.colorScheme.error)
            Button(onClick = viewModel::loadReports, modifier = Modifier.padding(top = 12.dp)) {
                Text("重试")
            }
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
            Text(text = "我的病历", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Text(
                text = "支持查看上传记录、删除记录，并进入详情页查看 AI 解析结果。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        uiState.errorMessage?.let { message ->
            item {
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }
        }
        if (uiState.isEmpty) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "还没有上传病历，先去上传一份 PDF、JPG 或 PNG 吧。",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            items(uiState.reports, key = { it.reportId }) { report ->
                ReportRow(
                    report = report,
                    isDeleting = uiState.deletingReportId == report.reportId,
                    onOpen = { onOpenReport(report.reportId) },
                    onDelete = { viewModel.deleteReport(report.reportId) },
                )
            }
        }
        item {
            Button(onClick = viewModel::loadReports, modifier = Modifier.fillMaxWidth()) {
                Text("刷新列表")
            }
        }
    }
}

@Composable
private fun ReportRow(
    report: ReportListItem,
    isDeleting: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = report.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text = "日期：${report.date}")
            Text(text = "类型：${report.type}")
            Text(text = "状态：${report.status}")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Text("查看详情")
                }
                Button(onClick = onDelete, enabled = !isDeleting, modifier = Modifier.weight(1f)) {
                    Text(if (isDeleting) "删除中..." else "删除")
                }
            }
        }
    }
}
