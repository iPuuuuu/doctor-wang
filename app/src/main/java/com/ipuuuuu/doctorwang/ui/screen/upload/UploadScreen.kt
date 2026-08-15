package com.ipuuuuu.doctorwang.ui.screen.upload

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ipuuuuu.doctorwang.viewmodel.UploadViewModel

@Composable
fun UploadScreen(
    onUploadSuccess: (Long) -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: UploadViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onFileSelected(result.data?.data)
    }

    LaunchedEffect(uiState.uploadSuccess, uiState.uploadedReportId) {
        val reportId = uiState.uploadedReportId
        if (uiState.uploadSuccess && reportId != null) onUploadSuccess(reportId)
    }

    LaunchedEffect(uiState.sessionExpired) {
        if (uiState.sessionExpired) onSessionExpired()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "上传医疗资料", style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(text = "支持 PDF / JPG / PNG，单文件不超过 20MB", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = "已选择：${uiState.selectedUri?.lastPathSegment?.substringAfterLast('/') ?: "未选择文件"}",
            style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = uiState.reportType,
            onValueChange = viewModel::onReportTypeChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("检查类型（选填）") },
            placeholder = { Text("如：腹部超声 / 血常规 / 胸部X线") },
            singleLine = true,
            enabled = !uiState.isUploading && !uiState.isPollingStatus,
        )
        OutlinedTextField(
            value = uiState.reportDate,
            onValueChange = viewModel::onReportDateChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("检查日期（选填）") },
            placeholder = { Text("如：2026-06-17") },
            singleLine = true,
            enabled = !uiState.isUploading && !uiState.isPollingStatus,
        )
        uiState.errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "当前状态", style = MaterialTheme.typography.titleSmall)
                Text(text = when {
                    uiState.isUploading -> "正在上传报告…"
                    uiState.isPollingStatus -> "正在获取解析状态…"
                    uiState.reportStatus != null -> {
                        val status = uiState.reportStatus!!
                        "${status.summary} · ${status.phase ?: "未知阶段"}"
                    }
                    else -> "等待选择并上传文件"
                })
                uiState.reportStatus?.let { status ->
                    if (status.requiresReview) {
                        Text(text = "报告内容需要人工复核，结果会尽快更新，请留意。", color = MaterialTheme.colorScheme.error)
                    }
                    if (status.canRetry) {
                        Text(text = "解析暂时失败，可稍后重试。")
                    }
                    status.failureCategory?.let { code ->
                        Text(text = friendlyFailure(code), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "image/jpeg", "image/png"))
                }
                launcher.launch(intent)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("选择文件")
        }
        Button(
            onClick = viewModel::upload,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isUploading && !uiState.isPollingStatus,
        ) {
            Text(if (uiState.isUploading) "上传中..." else if (uiState.isPollingStatus) "查询状态中..." else "开始上传")
        }
    }
}

private fun friendlyFailure(code: String): String = when (code) {
    "LOW_OCR_CONFIDENCE" -> "报告图片不够清晰，无法准确识别，请上传更清晰的图片或 PDF。"
    "REVIEW_REQUIRED" -> "报告内容需要人工复核，结果会尽快更新。"
    "AI_RESULT_INVALID" -> "AI 分析结果未通过校验，请稍后重试或联系客服。"
    "AI_SERVICE_UNAVAILABLE" -> "AI 服务暂时不可用，请稍后重试。"
    "SCAN_UNAVAILABLE" -> "安全扫描服务暂时不可用，上传已暂停，请稍后重试。"
    "MALWARE_DETECTED" -> "文件未通过安全扫描，已被拒绝。"
    else -> "解析未完成，请稍后重试或重新上传。"
}
