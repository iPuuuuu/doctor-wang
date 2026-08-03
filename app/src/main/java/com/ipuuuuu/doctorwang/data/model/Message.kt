package com.ipuuuuu.doctorwang.data.model

enum class MessageStatus {
    SENT,
    SENDING,
    FAILED,
}

data class Message(
    val id: String,
    val content: String,
    val role: String,
    val timestamp: Long,
    val status: MessageStatus = MessageStatus.SENT,
)
