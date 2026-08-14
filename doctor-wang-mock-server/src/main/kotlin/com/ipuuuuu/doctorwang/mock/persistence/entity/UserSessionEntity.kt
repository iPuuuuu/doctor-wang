package com.ipuuuuu.doctorwang.mock.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "user_session")
class UserSessionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "jti", nullable = false, unique = true, length = 64)
    val jti: String,
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    val tokenHash: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: AppUserEntity,
    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "expires_at", nullable = false)
    val expiresAt: OffsetDateTime,
    @Column(name = "revoked_at")
    var revokedAt: OffsetDateTime? = null,
)
