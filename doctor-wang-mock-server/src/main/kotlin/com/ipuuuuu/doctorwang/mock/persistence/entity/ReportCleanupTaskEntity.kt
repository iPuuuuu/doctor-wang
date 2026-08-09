package com.ipuuuuu.doctorwang.mock.persistence.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "report_cleanup_task")
class ReportCleanupTaskEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "report_id") val report: MedicalReportEntity? = null,
    @Column(name = "storage_key", nullable = false, length = 512) val storageKey: String,
    @Column(nullable = false, length = 64) val reason: String,
    @Column(nullable = false, length = 32) var status: String = "PENDING",
    @Column(name = "attempt_count", nullable = false) var attemptCount: Int = 0,
    @Column(name = "next_attempt_at", nullable = false) var nextAttemptAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "trace_id", nullable = false, length = 128) val traceId: String,
    @Column(name = "created_at", nullable = false) val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
