package com.ipuuuuu.doctorwang.mock.persistence.repository

import com.ipuuuuu.doctorwang.mock.persistence.entity.ChatMessageEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ChatMessageRepository : JpaRepository<ChatMessageEntity, Long> {
    fun findAllByUserAndConversationOrderByCreatedAtAscIdAsc(user: AppUserEntity, conversation: String): List<ChatMessageEntity>
}
