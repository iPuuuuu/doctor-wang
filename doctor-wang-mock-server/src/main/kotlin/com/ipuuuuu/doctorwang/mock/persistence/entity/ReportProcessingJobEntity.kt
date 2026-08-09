package com.ipuuuuu.doctorwang.mock.persistence.entity

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "report_processing_job")
class ReportProcessingJobEntity(
    @Id val id: UUID,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "report_id", nullable = false) val report: MedicalReportEntity,
    @Column(name = "user_id") var userId: Long? = null,
    @Column(nullable = false, length = 32) var status: String,
    @Column(nullable = false, length = 32) var stage: String,
    @Column(name = "phase", length = 32) var phase: String? = null,
    @Column(name = "attempt_count", nullable = false) var attemptCount: Int = 0,
    @Column(nullable = false) var retryable: Boolean = false,
    @Column(name = "error_code", length = 64) var errorCode: String? = null,
    @Column(name = "trace_id", nullable = false, length = 128) val traceId: String,
    @Column(name = "created_at", nullable = false) val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "started_at") var startedAt: OffsetDateTime? = null,
    @Column(name = "finished_at") var finishedAt: OffsetDateTime? = null,
    @Column(name = "next_attempt_at") var nextAttemptAt: OffsetDateTime? = null,
    @Column(name = "locked_at") var lockedAt: OffsetDateTime? = null,
    @Column(name = "locked_by", length = 128) var lockedBy: String? = null,
    @Column(name = "lease_expires_at") var leaseExpiresAt: OffsetDateTime? = null,
    @Column(name = "last_error_at") var lastErrorAt: OffsetDateTime? = null,
    @Column(name = "result_digest", length = 64) var resultDigest: String? = null,
    @Column(name = "result_version", length = 128) var resultVersion: String? = null,
    @Column(name = "payload_hash", length = 64) var payloadHash: String? = null,
    @Column(name = "active_index_version", length = 128) var activeIndexVersion: String? = null,
    @Column(name = "ai_job_id", length = 128) var aiJobId: String? = null,
    @Column(name = "idempotency_key", length = 128) var idempotencyKey: String? = null,
)
