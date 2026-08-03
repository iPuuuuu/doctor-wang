package com.ipuuuuu.doctorwang.ui.screen.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ipuuuuu.doctorwang.data.model.UserProfile
import com.ipuuuuu.doctorwang.viewmodel.ProfileViewModel

@Composable
fun HealthProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val profile = uiState.profile

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

    if (uiState.errorMessage != null && profile == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = uiState.errorMessage, color = MaterialTheme.colorScheme.error)
            Button(onClick = viewModel::refresh, modifier = Modifier.padding(top = 12.dp)) {
                Text("重新加载")
            }
        }
        return
    }

    if (profile == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "健康档案加载失败", color = MaterialTheme.colorScheme.error)
            Button(onClick = viewModel::refresh, modifier = Modifier.padding(top = 12.dp)) {
                Text("重新加载")
            }
        }
        return
    }

    if (uiState.isEditing) {
        EditableProfileContent(
            profile = profile,
            errorMessage = uiState.errorMessage,
            isSaving = uiState.isSaving,
            onSave = viewModel::save,
            onCancel = viewModel::cancelEdit,
        )
        return
    }

    ReadonlyProfileContent(
        profile = profile,
        errorMessage = uiState.errorMessage,
        onEdit = viewModel::beginEdit,
        onRefresh = viewModel::refresh,
    )
}

@Composable
private fun ReadonlyProfileContent(
    profile: UserProfile,
    errorMessage: String?,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = profile.name, style = MaterialTheme.typography.headlineSmall)
                    Text(text = "年龄：${profile.age}")
                    Text(text = "身高：${profile.height} cm")
                    Text(text = "体重：${profile.weight} kg")
                }
                Button(onClick = onEdit) {
                    Text("编辑")
                }
            }
        }
        errorMessage?.let { message ->
            item {
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            Text(text = "既往疾病", style = MaterialTheme.typography.titleMedium)
        }
        if (profile.historyDiseases.isEmpty()) {
            item {
                Text(text = "暂无既往疾病记录")
            }
        } else {
            items(profile.historyDiseases) {
                Text(text = "• $it")
            }
        }
        item {
            Text(text = "检查记录", style = MaterialTheme.typography.titleMedium)
        }
        if (profile.records.isEmpty()) {
            item {
                Text(text = "暂无检查记录")
            }
        } else {
            items(profile.records) { record ->
                Text(text = "• ${record.date} ${record.type}")
            }
        }
        item {
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text("重新加载")
            }
        }
    }
}

@Composable
private fun EditableProfileContent(
    profile: UserProfile,
    errorMessage: String?,
    isSaving: Boolean,
    onSave: (UserProfile) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember(profile) { mutableStateOf(profile.name) }
    var age by remember(profile) { mutableStateOf(profile.age.toString()) }
    var height by remember(profile) { mutableStateOf(profile.height.toString()) }
    var weight by remember(profile) { mutableStateOf(profile.weight.toString()) }
    var historyDiseases by remember(profile) { mutableStateOf(profile.historyDiseases.joinToString("\n")) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(text = "编辑健康档案", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                label = { Text("姓名") },
            )
        }
        item {
            OutlinedTextField(
                value = age,
                onValueChange = { age = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                label = { Text("年龄") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        item {
            OutlinedTextField(
                value = height,
                onValueChange = { height = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                label = { Text("身高（cm）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        item {
            OutlinedTextField(
                value = weight,
                onValueChange = { value -> weight = value.filter { it.isDigit() || it == '.' } },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                label = { Text("体重（kg）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        item {
            OutlinedTextField(
                value = historyDiseases,
                onValueChange = { historyDiseases = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                minLines = 4,
                label = { Text("既往疾病（每行一项）") },
            )
        }
        errorMessage?.let { message ->
            item {
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        onSave(
                            profile.copy(
                                name = name,
                                age = age.toIntOrNull() ?: 0,
                                height = height.toIntOrNull() ?: 0,
                                weight = weight.toFloatOrNull() ?: 0f,
                                historyDiseases = historyDiseases.lines(),
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving,
                ) {
                    if (isSaving) {
                        CircularProgressIndicator()
                    } else {
                        Text("保存")
                    }
                }
                Button(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving,
                ) {
                    Text("取消")
                }
            }
        }
        item {
            Text(
                text = "本地验收：编辑资料并保存后，返回页面应展示最新内容；重新进入页面时应继续显示服务端保存结果。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
