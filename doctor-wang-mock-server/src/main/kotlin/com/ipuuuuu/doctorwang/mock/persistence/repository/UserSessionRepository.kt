package com.ipuuuuu.doctorwang.mock.persistence.repository

import com.ipuuuuu.doctorwang.mock.persistence.entity.UserSessionEntity
import org.springframework.data.jpa.repository.JpaRepository

interface UserSessionRepository : JpaRepository<UserSessionEntity, Long> {
    fun findByToken(token: String): UserSessionEntity?
}
