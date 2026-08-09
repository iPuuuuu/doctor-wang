package com.ipuuuuu.doctorwang.mock.persistence.repository

import com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity
import org.springframework.data.jpa.repository.JpaRepository

interface AppUserRepository : JpaRepository<AppUserEntity, Long> {
    fun findByAccount(account: String): AppUserEntity?
    fun findByUserId(userId: String): AppUserEntity?
}
