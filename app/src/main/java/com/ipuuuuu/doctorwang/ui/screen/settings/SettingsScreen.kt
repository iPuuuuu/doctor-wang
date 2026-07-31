package com.ipuuuuu.doctorwang.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ipuuuuu.doctorwang.storage.DEMO_SERVER_PLACEHOLDER_URL
import com.ipuuuuu.doctorwang.storage.LOCAL_DEBUG_SERVER_URL
import com.ipuuuuu.doctorwang.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    LaunchedEffect(uiState.loggedOut) {
        if (uiState.loggedOut) onLoggedOut()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "服务设置", style = MaterialTheme.typography.headlineSmall)
        Text(text = "当前默认环境：${uiState.environmentLabel}")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "连接说明", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(text = "当前构建默认地址：${uiState.defaultServerUrl}")
                Text(text = "本地调试推荐：$LOCAL_DEBUG_SERVER_URL")
                Text(text = "线上演示发布时，请在构建阶段把默认地址改成真实 HTTPS 域名。")
                if (!uiState.isHostedDefaultConfigured && uiState.defaultServerUrl == DEMO_SERVER_PLACEHOLDER_URL) {
                    Text(
                        text = "当前 release 默认地址仍是占位域名，正式发布前请通过 doctorWangReleaseBaseUrl 或 DOCTOR_WANG_RELEASE_BASE_URL 注入真实后端地址。",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        OutlinedTextField(
            value = uiState.serverUrl,
            onValueChange = viewModel::updateServerUrl,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("服务地址") },
        )

        uiState.connectionMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp))
                    .padding(14.dp),
            )
        }
        uiState.errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(14.dp))
                    .padding(14.dp),
            )
        }

        Button(
            onClick = viewModel::saveServerUrl,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isSaving,
        ) {
            Text(if (uiState.isSaving) "保存中..." else "保存服务地址")
        }

        Button(
            onClick = viewModel::testConnection,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isTestingConnection,
        ) {
            Text(if (uiState.isTestingConnection) "检测中..." else "测试连接")
        }

        Button(
            onClick = viewModel::logout,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoggingOut,
        ) {
            Text(if (uiState.isLoggingOut) "退出中..." else "退出登录")
        }
    }
}
