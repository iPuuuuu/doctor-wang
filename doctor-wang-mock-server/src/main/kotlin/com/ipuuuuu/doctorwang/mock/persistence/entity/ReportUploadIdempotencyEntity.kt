package com.ipuuuuu.doctorwang.mock.persistence.entity

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "report_upload_idempotency", uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "idempotency_key"])])
class ReportUploadIdempotencyEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) val user: AppUserEntity,
    @Column(name = "idempotency_key", nullable = false, length = 128) val idempotencyKey: String,
    @Column(name = "request_digest", nullable = false, length = 64) val requestDigest: String,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "report_id", nullable = false) val report: MedicalReportEntity,
    @Column(name = "processing_job_id", nullable = false) val processingJobId: UUID,
    @Column(name = "response_snapshot", nullable = false, columnDefinition = "text") val responseSnapshot: String,
    @Column(name = "created_at", nullable = false) val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "expires_at", nullable = false) val expiresAt: OffsetDateTime,
) {
    protected constructor() : this(user = AppUserEntity(userId = "", account = "", passwordHash = "", name = "", age = 0, height = 0, weight = 0f), idempotencyKey = "", requestDigest = "", report = MedicalReportEntity(user = AppUserEntity(userId = "", account = "", passwordHash = "", name = "", age = 0, height = 0, weight = 0f), title = "", reportDate = "", reportType = "", summary = "", status = ""), processingJobId = UUID.randomUUID(), responseSnapshot = "", expiresAt = OffsetDateTime.now())
}
