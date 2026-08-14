package com.ipuuuuu.doctorwang.mock.persistence.repository

import com.ipuuuuu.doctorwang.mock.persistence.entity.MedicalReportEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.OffsetDateTime

interface MedicalReportRepository : JpaRepository<MedicalReportEntity, Long> {
    fun findAllByUserOrderByCreatedAtDescIdDesc(user: AppUserEntity): List<MedicalReportEntity>
    fun findAllByUser(user: AppUserEntity, pageable: Pageable): Page<MedicalReportEntity>
    /** 对象生命周期（ADR-0008 后续）：按更新时间查找可清理的过期报告。 */
    fun findAllByUpdatedAtBefore(updatedAt: OffsetDateTime, pageable: Pageable): Page<MedicalReportEntity>
}
