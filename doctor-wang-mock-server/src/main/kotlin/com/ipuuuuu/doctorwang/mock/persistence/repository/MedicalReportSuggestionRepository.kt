package com.ipuuuuu.doctorwang.mock.persistence.repository

import com.ipuuuuu.doctorwang.mock.persistence.entity.MedicalReportEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.MedicalReportSuggestionEntity
import org.springframework.data.jpa.repository.JpaRepository

interface MedicalReportSuggestionRepository : JpaRepository<MedicalReportSuggestionEntity, Long> {
    fun findAllByReportOrderByIdAsc(report: MedicalReportEntity): List<MedicalReportSuggestionEntity>
}
