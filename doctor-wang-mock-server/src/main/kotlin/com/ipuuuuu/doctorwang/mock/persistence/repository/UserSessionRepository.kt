package com.ipuuuuu.doctorwang.mock.persistence.repository

import com.ipuuuuu.doctorwang.mock.persistence.entity.UserSessionEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime

interface UserSessionRepository : JpaRepository<UserSessionEntity, Long> {
    fun findByJti(jti: String): UserSessionEntity?

    @Modifying(flushAutomatically = true)
    @Query("update UserSessionEntity session set session.revokedAt = coalesce(session.revokedAt, :revokedAt) where session.jti = :jti and session.user.id = :userId")
    fun revokeByJtiAndUserId(
        @Param("jti") jti: String,
        @Param("userId") userId: Long,
        @Param("revokedAt") revokedAt: OffsetDateTime,
    ): Int

    @Modifying(flushAutomatically = true)
    @Query("update UserSessionEntity session set session.revokedAt = coalesce(session.revokedAt, :revokedAt) where session.user.id = :userId and session.jti <> :currentJti and session.revokedAt is null")
    fun revokeAllExcept(
        @Param("userId") userId: Long,
        @Param("currentJti") currentJti: String,
        @Param("revokedAt") revokedAt: OffsetDateTime,
    ): Int
}
