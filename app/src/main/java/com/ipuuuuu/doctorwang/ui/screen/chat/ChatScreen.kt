package com.ipuuuuu.doctorwang.ui.screen.chat

import androidx.compose.foundation.background
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ipuuuuu.doctorwang.ui.component.MessageBubble
import com.ipuuuuu.doctorwang.viewmodel.ChatViewModel

@Composable
fun ChatScreen(
    onUploadClick: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    LaunchedEffect(uiState.sessionExpired) {
        if (uiState.sessionExpired) onSessionExpired()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "AI 问诊", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(text = "你可以直接输入“胃疼”“头疼”等问题进行演示。", style = MaterialTheme.typography.bodySmall)

        uiState.conversationSummary?.let { summary ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(text = "最近一次会话", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(text = summary.title)
                    Text(text = summary.detail, style = MaterialTheme.typography.bodySmall)
                    Text(text = summary.latestAdvice, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = "推荐提问", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(text = "• 最近胃疼怎么办\n• 头疼三天需要注意什么\n• 体检提示轻度脂肪肝怎么调理")
            }
        }

        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text(text = "正在加载问诊记录…", modifier = Modifier.padding(top = 12.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (uiState.messages.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(text = "开始你的第一次问诊", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(text = "把症状、持续时间或最近做过的检查发给 Doctor-Wang，就可以开始演示。")
                            }
                        }
                    }
                }
                items(uiState.messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                }
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
                Text(text = "输入你的健康问题", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = uiState.input,
                    onValueChange = viewModel::updateInput,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("例如：最近胃疼，饭后更明显") },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = onUploadClick, modifier = Modifier.weight(1f)) {
                        Text("上传报告")
                    }
                    Button(
                        onClick = viewModel::sendMessage,
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isSending,
                    ) {
                        Text(if (uiState.isSending) "发送中..." else "发送问题")
                    }
                }
                if (uiState.isSending) {
                    Text(
                        text = "Doctor-Wang 正在整理回复，请稍等…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
