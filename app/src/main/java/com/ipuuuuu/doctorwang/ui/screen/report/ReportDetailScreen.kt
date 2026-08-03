package com.ipuuuuu.doctorwang.ui.screen.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

    if (uiState.errorMessage != null && report == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = uiState.errorMessage, color = MaterialTheme.colorScheme.error)
            Button(onClick = viewModel::loadReport, modifier = Modifier.padding(top = 12.dp)) {
                Text("重试")
            }
        }
        return
    }

    if (report == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "报告加载失败", color = MaterialTheme.colorScheme.error)
            Button(onClick = viewModel::loadReport, modifier = Modifier.padding(top = 12.dp)) {
                Text("重试")
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = "检查报告", style = MaterialTheme.typography.titleMedium)
                    Text(text = report.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoChip(text = report.date)
                        InfoChip(text = report.type)
                    }
                }
            }
        }
        uiState.errorMessage?.let { message ->
            item {
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }
        }
        uiState.downloadMessage?.let { message ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = viewModel::loadReport, modifier = Modifier.weight(1f)) {
                    Text("刷新结果")
                }
                Button(
                    onClick = viewModel::downloadReport,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isDownloading,
                ) {
                    if (uiState.isDownloading) {
                        CircularProgressIndicator()
                    } else {
                        Text("下载原文件")
                    }
                }
            }
        }
        item {
            SectionCard(title = "AI 解析摘要") {
                Text(text = report.summary, style = MaterialTheme.typography.bodyLarge)
            }
        }
        item {
            SectionCard(title = "重点发现") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    report.findings.forEach {
                        BulletItem(text = it)
                    }
                }
            }
        }
        item {
            SectionCard(title = "建议与后续动作") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    report.suggestions.forEach {
                        BulletItem(text = it)
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Text(
                    text = "当前页面展示的是 mock backend 返回的报告结果；后续会在此基础上继续接入 OCR、分析状态与 Claude 网关。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun BulletItem(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "•", color = MaterialTheme.colorScheme.primary)
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}
