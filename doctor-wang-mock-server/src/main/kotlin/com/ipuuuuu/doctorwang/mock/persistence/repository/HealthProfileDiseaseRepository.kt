package com.ipuuuuu.doctorwang.mock.persistence.repository

import com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.HealthProfileDiseaseEntity
import org.springframework.data.jpa.repository.JpaRepository

interface HealthProfileDiseaseRepository : JpaRepository<HealthProfileDiseaseEntity, Long> {
    fun findAllByUserOrderByIdAsc(user: AppUserEntity): List<HealthProfileDiseaseEntity>
}
