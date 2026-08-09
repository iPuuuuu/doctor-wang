package com.ipuuuuu.doctorwang.mock.persistence.repository

import com.ipuuuuu.doctorwang.mock.persistence.entity.ReportCleanupTaskEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.OffsetDateTime

interface ReportCleanupTaskRepository : JpaRepository<ReportCleanupTaskEntity, Long> {
    fun deleteByStatusAndNextAttemptAtLessThanEqual(status: String, at: OffsetDateTime): Long
}
