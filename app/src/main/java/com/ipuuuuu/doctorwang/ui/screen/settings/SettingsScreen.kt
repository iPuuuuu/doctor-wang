package com.ipuuuuu.doctorwang.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ipuuuuu.doctorwang.viewmodel.PasswordChangeViewModel
import com.ipuuuuu.doctorwang.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    passwordViewModel: PasswordChangeViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val passwordUiState = passwordViewModel.uiState.collectAsStateWithLifecycle().value
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.loggedOut) {
        if (uiState.loggedOut) onLoggedOut()
    }

    // 后端 changePassword 会吊销会话并清空本地 session（ADR-0003），
    // 成功后引导用户重新登录。
    LaunchedEffect(passwordUiState.changeSuccess) {
        if (passwordUiState.changeSuccess) onLoggedOut()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) { Text("返回首页") }
        Text(text = "账号设置", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "版本 ${com.ipuuuuu.doctorwang.BuildConfig.VERSION_NAME} · ${com.ipuuuuu.doctorwang.BuildConfig.SERVER_ENVIRONMENT_LABEL}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = { showPasswordDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("修改密码")
        }

        Button(
            onClick = viewModel::logout,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoggingOut && !uiState.isDeleting,
        ) {
            Text(if (uiState.isLoggingOut) "退出中..." else "退出登录")
        }

        uiState.deleteError?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        TextButton(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isDeleting && !uiState.isLoggingOut,
        ) {
            Text(
                text = if (uiState.isDeleting) "注销中..." else "注销账号",
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { if (!passwordUiState.isLoading) showPasswordDialog = false },
            title = { Text("修改密码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = passwordUiState.oldPassword,
                        onValueChange = passwordViewModel::updateOldPassword,
                        label = { Text("旧密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    OutlinedTextField(
                        value = passwordUiState.newPassword,
                        onValueChange = passwordViewModel::updateNewPassword,
                        label = { Text("新密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    OutlinedTextField(
                        value = passwordUiState.confirmPassword,
                        onValueChange = passwordViewModel::updateConfirmPassword,
                        label = { Text("确认新密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    passwordUiState.errorMessage?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = passwordViewModel::changePassword,
                    enabled = !passwordUiState.isLoading,
                ) {
                    Text(if (passwordUiState.isLoading) "提交中..." else "确认修改")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPasswordDialog = false },
                    enabled = !passwordUiState.isLoading,
                ) {
                    Text("取消")
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!uiState.isDeleting) showDeleteDialog = false },
            title = { Text("注销账号") },
            text = {
                Text(
                    "注销后您的账号、健康档案、全部报告和聊天记录将被永久删除，且无法恢复。确定要注销吗？",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteAccount()
                    },
                    enabled = !uiState.isDeleting,
                ) {
                    Text("确认注销", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !uiState.isDeleting,
                ) {
                    Text("取消")
                }
            },
        )
    }
}
