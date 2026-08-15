package com.ipuuuuu.doctorwang.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ipuuuuu.doctorwang.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onChatClick: () -> Unit,
    onAnxietyChatClick: () -> Unit,
    onUploadClick: () -> Unit,
    onProfileClick: () -> Unit,
    onReportListClick: () -> Unit = {},
    onSettingsClick: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    LaunchedEffect(uiState.sessionExpired) {
        if (uiState.sessionExpired) onSessionExpired()
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
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            WelcomeBanner(userName = uiState.userName)
        }
        item {
            HealthOverviewCard(
                bmiText = uiState.bmiText,
                statusSummary = uiState.statusSummary,
                recentRecordText = uiState.recentRecordText,
            )
        }
        item {
            Text(text = "快捷服务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HomeEntryCard(
                    title = "AI 问诊",
                    subtitle = "即时咨询常见不适",
                    accent = "🩺",
                    onClick = onChatClick,
                    modifier = Modifier.weight(1f),
                )
                HomeEntryCard(
                    title = "报告分析",
                    subtitle = "上传体检或检查报告",
                    accent = "📄",
                    onClick = onUploadClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HomeEntryCard(
                    title = "健康档案",
                    subtitle = "查看病史与指标",
                    accent = "❤️",
                    onClick = onProfileClick,
                    modifier = Modifier.weight(1f),
                )
                HomeEntryCard(
                    title = "安心话聊",
                    subtitle = "缓解对疾病的担忧",
                    accent = "🌿",
                    onClick = onAnxietyChatClick,
                    modifier = Modifier.weight(1f),
                )
                HomeEntryCard(
                    title = "账号设置",
                    subtitle = "修改密码、退出登录",
                    accent = "⚙️",
                    onClick = onSettingsClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Text(text = "最近咨询", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (uiState.historyMessages.isEmpty()) {
            item {
                EmptyHistoryCard(onChatClick = onChatClick)
            }
        } else {
            items(uiState.historyMessages.filter { it.role == "user" }) { message ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(text = message.content, style = MaterialTheme.typography.bodyLarge)
                        Text(text = "点击 AI 问诊继续", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeBanner(
    userName: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = "Doctor-Wang", style = MaterialTheme.typography.titleMedium)
            Text(text = "你好，${userName}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                text = "今天也可以从健康档案、报告分析或 AI 问诊开始。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun HealthOverviewCard(
    bmiText: String,
    statusSummary: String,
    recentRecordText: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "今日健康状态",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardStat(title = "BMI", value = bmiText, modifier = Modifier.weight(1f))
                DashboardStat(title = "重点提示", value = statusSummary, modifier = Modifier.weight(1f))
            }
            Text(text = "最近检查", style = MaterialTheme.typography.labelLarge)
            Text(text = recentRecordText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmptyHistoryCard(onChatClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "还没有最近咨询", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text = "可以先试试输入“胃疼”“头疼”这类问题，快速体验 AI 问诊流程。")
            Text(
                text = "立即开始问诊",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onChatClick),
            )
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
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HomeEntryCard(
    title: String,
    subtitle: String,
    accent: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .height(128.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(text = accent)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
