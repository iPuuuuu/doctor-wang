package com.ipuuuuu.doctorwang.mock.persistence.repository

import com.ipuuuuu.doctorwang.mock.persistence.entity.MedicalReportEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.MedicalReportFindingEntity
import org.springframework.data.jpa.repository.JpaRepository

interface MedicalReportFindingRepository : JpaRepository<MedicalReportFindingEntity, Long> {
    fun findAllByReportOrderByIdAsc(report: MedicalReportEntity): List<MedicalReportFindingEntity>
}
