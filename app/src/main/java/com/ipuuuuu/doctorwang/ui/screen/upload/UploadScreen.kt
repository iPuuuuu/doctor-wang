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
        Text(text = "上传医疗资料", style = MaterialTheme.typography.headlineSmall)
        Text(text = "支持 PDF / JPG / PNG")
        Text(text = "已选择：${uiState.selectedUri?.lastPathSegment?.substringAfterLast('/') ?: "未选择文件"}")
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
                    Text(text = "可重试：${if (status.canRetry) "是" else "否"}")
                    Text(text = "需要复核：${if (status.requiresReview) "是" else "否"}")
                    status.failureCategory?.let { Text(text = "失败分类：$it") }
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
