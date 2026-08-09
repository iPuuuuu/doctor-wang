package com.ipuuuuu.doctorwang.mock.persistence.repository

import com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.ReportUploadIdempotencyEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.OffsetDateTime

interface ReportUploadIdempotencyRepository : JpaRepository<ReportUploadIdempotencyEntity, Long> {
    fun findByUserAndIdempotencyKey(user: AppUserEntity, idempotencyKey: String): ReportUploadIdempotencyEntity?
    fun findByUserAndIdempotencyKeyAndExpiresAtAfter(user: AppUserEntity, idempotencyKey: String, now: OffsetDateTime): ReportUploadIdempotencyEntity?
    @Modifying
    @Query("delete from ReportUploadIdempotencyEntity i where i.expiresAt <= :now")
    fun deleteExpired(now: OffsetDateTime): Long
}
