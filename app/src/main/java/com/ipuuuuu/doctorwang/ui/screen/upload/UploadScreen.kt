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
import androidx.compose.material3.CircularProgressIndicator
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
    onOpenReportList: () -> Unit,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "上传医疗资料", style = MaterialTheme.typography.headlineSmall)
        Text(text = "支持 PDF / JPG / PNG")
        Text(text = "已选择：${uiState.selectedUri?.lastPathSegment?.substringAfterLast('/') ?: "未选择文件"}")
        Text(text = "当前进度：${uiState.progressMessage}")
        uiState.errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
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
            enabled = !uiState.isUploading,
        ) {
            Text("选择文件")
        }
        Button(
            onClick = viewModel::upload,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isUploading,
        ) {
            if (uiState.isUploading) {
                CircularProgressIndicator()
            } else {
                Text("开始上传")
            }
        }
        Button(
            onClick = onOpenReportList,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isUploading,
        ) {
            Text("查看我的病历")
        }
    }
}
