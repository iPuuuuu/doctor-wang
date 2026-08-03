package com.ipuuuuu.doctorwang.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ipuuuuu.doctorwang.data.model.Message
import com.ipuuuuu.doctorwang.data.model.MessageStatus
import com.ipuuuuu.doctorwang.ui.theme.Primary
import com.ipuuuuu.doctorwang.ui.theme.PrimaryContainer

@Composable
fun MessageBubble(
    message: Message,
    onRetry: (() -> Unit)? = null,
) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .background(
                    color = if (isUser) Primary else PrimaryContainer,
                    shape = RoundedCornerShape(18.dp),
                )
                .then(
                    if (message.status == MessageStatus.FAILED && onRetry != null) {
                        Modifier.clickable(onClick = onRetry)
                    } else {
                        Modifier
                    },
                )
                .padding(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (isUser) "你" else "Doctor-Wang",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isUser) Color.White else Primary,
                )
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                )
                if (isUser) {
                    Text(
                        text = when (message.status) {
                            MessageStatus.SENDING -> "发送中..."
                            MessageStatus.FAILED -> "发送失败，点击重试"
                            MessageStatus.SENT -> "已发送"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
