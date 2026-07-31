package com.ipuuuuu.doctorwang.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
        Text(text = "设置", style = MaterialTheme.typography.headlineSmall)
        Text(text = "手机真机测试时，请把这里改成你电脑的局域网地址，例如 http://192.168.1.10:8080/")

        OutlinedTextField(
            value = uiState.serverUrl,
            onValueChange = viewModel::updateServerUrl,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("服务地址") },
        )

        uiState.connectionMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.primary)
        }
        uiState.errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
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
