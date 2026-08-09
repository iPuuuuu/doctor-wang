package com.ipuuuuu.doctorwang.mock.persistence.repository

import com.ipuuuuu.doctorwang.mock.persistence.entity.MedicalReportEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.MedicalReportFileEntity
import org.springframework.data.jpa.repository.JpaRepository

interface MedicalReportFileRepository : JpaRepository<MedicalReportFileEntity, Long> {
    fun findByReport(report: MedicalReportEntity): MedicalReportFileEntity?
}
