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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ipuuuuu.doctorwang.data.model.MedicalReport
import com.ipuuuuu.doctorwang.viewmodel.ReportDetailViewModel

@Composable
fun ReportDetailScreen(
    onSessionExpired: () -> Unit,
    viewModel: ReportDetailViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val report = uiState.report
    val status = uiState.reportStatus

    LaunchedEffect(uiState.sessionExpired) {
        if (uiState.sessionExpired) onSessionExpired()
    }

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
                        if (report.date.isNotBlank()) InfoChip(text = report.date)
                        if (report.type.isNotBlank()) InfoChip(text = report.type)
                        status?.phase?.let { InfoChip(text = it) }
                    }
                }
            }
        }
        item {
            SectionCard(title = "处理状态") {
                ReportStatusBlock(status = status ?: report, isRefreshing = uiState.isRefreshingStatus)
            }
        }
        item {
            // 健康辅助边界声明（AGENTS.md 强制）：后端 disclaimer 优先，缺失时用固定文案兜底。
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Text(
                    text = report.disclaimer
                        ?: "本内容仅用于健康辅助，不能替代医生诊断；如有紧急或高风险症状（如胸痛、呼吸困难、意识改变、严重出血等），请立即就医。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
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
    }
}

@Composable
private fun ReportStatusBlock(status: MedicalReport, isRefreshing: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = status.summary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        status.phase?.let {
            Text(text = "当前阶段：$it", style = MaterialTheme.typography.bodyMedium)
        }
        Text(text = "可重试：${if (status.canRetry) "是" else "否"}")
        Text(text = "需要人工复核：${if (status.requiresReview) "是" else "否"}")
        status.failureCategory?.let {
            Text(text = "失败分类：$it", color = MaterialTheme.colorScheme.error)
        }
        if (isRefreshing) {
            Text(text = "正在刷新处理状态…", color = MaterialTheme.colorScheme.primary)
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
