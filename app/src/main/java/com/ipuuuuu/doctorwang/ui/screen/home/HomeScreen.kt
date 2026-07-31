package com.ipuuuuu.doctorwang.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ipuuuuu.doctorwang.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onChatClick: () -> Unit,
    onUploadClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

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

    if (uiState.errorMessage != null) {
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(text = "Doctor-Wang", style = MaterialTheme.typography.headlineMedium)
            Text(text = "你好，${uiState.userName}", style = MaterialTheme.typography.titleLarge)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(text = "今日健康状态", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DashboardStat(title = "BMI", value = uiState.bmiText, modifier = Modifier.weight(1f))
                        DashboardStat(title = "重点提示", value = uiState.statusSummary, modifier = Modifier.weight(1f))
                    }
                    Text(text = "最近检查：${uiState.recentRecordText}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HomeEntryCard(title = "🩺 AI问诊", onClick = onChatClick, modifier = Modifier.weight(1f))
                HomeEntryCard(title = "📄 报告分析", onClick = onUploadClick, modifier = Modifier.weight(1f))
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HomeEntryCard(title = "❤️ 健康档案", onClick = onProfileClick, modifier = Modifier.weight(1f))
                HomeEntryCard(title = "🏥 服务设置", onClick = onSettingsClick, modifier = Modifier.weight(1f))
            }
        }
        item {
            Text(text = "最近咨询", style = MaterialTheme.typography.titleMedium)
        }
        items(uiState.historyMessages.filter { it.role == "user" }) { message ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = message.content, style = MaterialTheme.typography.bodyLarge)
                    Text(text = "点击 AI 问诊继续", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun DashboardStat(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.labelLarge)
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun HomeEntryCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        }
    }
}
