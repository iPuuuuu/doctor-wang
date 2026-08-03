package com.ipuuuuu.doctorwang.ui.screen.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ipuuuuu.doctorwang.viewmodel.LoginViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    LaunchedEffect(uiState.loginSuccess) {
        if (uiState.loginSuccess) onLoginSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Doctor-Wang", style = MaterialTheme.typography.headlineMedium)
        Text(text = if (uiState.isRegisterMode) "注册新账号" else "手机号或邮箱登录", style = MaterialTheme.typography.bodyMedium)

        if (uiState.showServerGuide) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "首次使用前请先设置服务地址", style = MaterialTheme.typography.titleMedium)
                Text(text = "1. 先在电脑启动 mock backend\n2. 手机和电脑连接同一 Wi‑Fi\n3. 进入服务设置填写 http://<电脑IP>:8080/")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenSettings, modifier = Modifier.weight(1f)) {
                        Text("打开服务设置")
                    }
                    Button(onClick = viewModel::dismissServerGuide, modifier = Modifier.weight(1f)) {
                        Text("我知道了")
                    }
                }
            }
        }

        Text(
            text = if (uiState.isRegisterMode) "账号支持手机号或邮箱；密码长度至少 8 位。" else "请输入已注册的账号和密码。",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = uiState.account,
            onValueChange = viewModel::updateAccount,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("手机号 / 邮箱") },
        )
        OutlinedTextField(
            value = uiState.password,
            onValueChange = viewModel::updatePassword,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("密码") },
        )

        uiState.errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = viewModel::login,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading,
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else {
                Text(if (uiState.isRegisterMode) "注册并登录" else "登录")
            }
        }
        Button(onClick = viewModel::toggleRegisterMode, modifier = Modifier.fillMaxWidth()) {
            Text(if (uiState.isRegisterMode) "已有账号，去登录" else "没有账号，注册新账号")
        }
    }
}
