package com.ipuuuuu.doctorwang.ui.screen.anxiety

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ipuuuuu.doctorwang.ui.component.MessageBubble
import androidx.compose.foundation.isSystemInDarkTheme
import com.ipuuuuu.doctorwang.ui.theme.DarkSoftTeal
import com.ipuuuuu.doctorwang.ui.theme.SoftTeal
import com.ipuuuuu.doctorwang.viewmodel.AnxietyChatViewModel

/**
 * 安心话聊：帮助缓解疑病症/大病焦虑。
 * 结合用户的健康档案与检查报告，由 AI 以专门的安全提示词回应；
 * 页面刻意采用柔和的青色系配色与温和文案，营造安心氛围。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnxietyChatScreen(
    onBack: () -> Unit,
    viewModel: AnxietyChatViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    val listState = rememberLazyListState()
    // 新消息到达时自动滚动到底部（含 AI 回复）
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    LaunchedEffect(uiState.sessionExpired) {
        if (uiState.sessionExpired) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "安心话聊", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "缓解对疾病的担忧 · 结合你的健康档案",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            if (uiState.isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    WelcomeAnxietyCard()
                }
                items(uiState.messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                }
            }

            uiState.errorMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "把你担心的事情告诉我，我会结合你的真实检查结果帮你分析",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    OutlinedTextField(
                        value = uiState.input,
                        onValueChange = viewModel::updateInput,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("例如：我最近总担心自己得了重病") },
                    )
                    Button(
                        onClick = viewModel::send,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isSending,
                    ) {
                        if (uiState.isSending) {
                            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        } else {
                            Text("聊聊心里话")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeAnxietyCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (isSystemInDarkTheme()) DarkSoftTeal else SoftTeal),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "🌿", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "把担心说出来，是缓解焦虑的第一步",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "我会结合你的健康档案和检查报告，帮你把「实际检查结果支持什么」和「你担心的疾病」区分开——很多担忧其实缺乏证据支持。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "请注意：这里是健康辅助，不是诊断。如果症状真实存在、持续或加重，请及时就医；如果焦虑明显影响生活，建议咨询心理科/精神科。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
