package com.ipuuuuu.doctorwang.data.model

enum class MessageStatus {
    SENT,
    SENDING,
    ERROR,
}

data class MessageCitation(
    val sourceType: String,
    val sourceId: String,
    val pageNo: Int?,
    val excerpt: String,
    val confidence: Double?,
)

data class Message(
    val id: String,
    val content: String,
    val role: String,
    val timestamp: Long,
    val status: MessageStatus = MessageStatus.SENT,
    val warnings: List<String> = emptyList(),
    val citations: List<MessageCitation> = emptyList(),
)

data class ConversationSummary(
    val title: String,
    val detail: String,
    val latestAdvice: String,
    val latestTimestamp: Long,
)
