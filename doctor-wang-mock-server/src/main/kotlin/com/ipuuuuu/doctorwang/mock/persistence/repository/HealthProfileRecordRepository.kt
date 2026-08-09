package com.ipuuuuu.doctorwang.mock.persistence.repository

import com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.HealthProfileRecordEntity
import org.springframework.data.jpa.repository.JpaRepository

interface HealthProfileRecordRepository : JpaRepository<HealthProfileRecordEntity, Long> {
    fun findAllByUserOrderByIdAsc(user: AppUserEntity): List<HealthProfileRecordEntity>
}
