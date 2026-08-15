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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import com.ipuuuuu.doctorwang.data.model.MedicalReport
import com.ipuuuuu.doctorwang.viewmodel.ReportDetailViewModel

@Composable
fun ReportDetailScreen(
    onSessionExpired: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: ReportDetailViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val report = uiState.report
    val status = uiState.reportStatus
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.sessionExpired) {
        if (uiState.sessionExpired) onSessionExpired()
    }

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onDeleted()
    }

    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除这份报告？") },
            text = { Text("删除后报告记录与解析结果将无法恢复。此操作不可撤销。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false; viewModel.delete() }) {
                    Text("确认删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }

    // 下载完成后用系统"打开方式"展示原报告文件
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(uiState.downloadedFile) {
        val file = uiState.downloadedFile ?: return@LaunchedEffect
        runCatching {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val mime = android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "打开报告文件"))
        }
        viewModel.clearDownloadedFile()
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
            Button(
                onClick = viewModel::downloadFile,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isDownloading,
            ) {
                Text(if (uiState.isDownloading) "下载中..." else "查看原报告文件")
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (status?.canRetry == true) {
                    Button(
                        onClick = viewModel::retry,
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isRetrying,
                    ) {
                        Text(if (uiState.isRetrying) "重试中..." else "重新解析")
                    }
                }
                Button(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isDeleting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text(if (uiState.isDeleting) "删除中..." else "删除报告", color = MaterialTheme.colorScheme.onErrorContainer)
                }
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
        if (status.requiresReview) {
            Text(text = "报告内容需要人工复核，结果会尽快更新，请留意。", color = MaterialTheme.colorScheme.error)
        }
        status.failureCategory?.let {
            Text(text = friendlyFailure(it), color = MaterialTheme.colorScheme.error)
        }
        if (isRefreshing) {
            Text(text = "正在刷新处理状态…", color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun friendlyFailure(code: String): String = when (code) {
    "LOW_OCR_CONFIDENCE" -> "报告图片不够清晰，无法准确识别，请上传更清晰的图片或 PDF。"
    "REVIEW_REQUIRED" -> "报告内容需要人工复核，结果会尽快更新。"
    "AI_RESULT_INVALID" -> "AI 分析结果未通过校验，请稍后重试或联系客服。"
    "AI_SERVICE_UNAVAILABLE" -> "AI 服务暂时不可用，请稍后重试。"
    "SCAN_UNAVAILABLE" -> "安全扫描服务暂时不可用，请稍后重试。"
    "MALWARE_DETECTED" -> "文件未通过安全扫描，已被拒绝。"
    else -> "解析未完成，请稍后重试或重新上传。"
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
