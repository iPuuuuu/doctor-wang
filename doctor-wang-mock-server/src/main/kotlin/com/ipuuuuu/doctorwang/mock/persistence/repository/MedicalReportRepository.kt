package com.ipuuuuu.doctorwang.mock.persistence.repository

import com.ipuuuuu.doctorwang.mock.persistence.entity.MedicalReportEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface MedicalReportRepository : JpaRepository<MedicalReportEntity, Long> {
    fun findAllByUserOrderByCreatedAtDescIdDesc(user: AppUserEntity): List<MedicalReportEntity>
    fun findAllByUser(user: AppUserEntity, pageable: Pageable): Page<MedicalReportEntity>
}
