package com.ipuuuuu.doctorwang.mock.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "app_user")
class AppUserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    val userId: String,
    @Column(nullable = false, unique = true)
    val account: String,
    @Column(nullable = false)
    val password: String,
    @Column(nullable = false)
    val name: String,
    @Column(nullable = false)
    val age: Int,
    @Column(nullable = false)
    val height: Int,
    @Column(nullable = false)
    val weight: Float,
    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
