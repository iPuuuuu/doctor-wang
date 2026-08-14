package com.ipuuuuu.doctorwang.mock.persistence.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "medical_report_file")
class MedicalReportFileEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "report_id", nullable = false) val report: MedicalReportEntity,
    @Column(name = "storage_key", nullable = false, unique = true, length = 512) val storageKey: String,
    @Column(name = "original_file_name", nullable = false) val originalFileName: String,
    @Column(name = "mime_type", nullable = false, length = 128) val mimeType: String,
    @Column(name = "file_size", nullable = false) val fileSize: Long,
    @Column(nullable = false, length = 64) val sha256: String,
    @Column(name = "created_at", nullable = false) val createdAt: OffsetDateTime = OffsetDateTime.now(),
) {
    protected constructor() : this(report = MedicalReportEntity(user = AppUserEntity(userId = "", account = "", passwordHash = "", name = "", age = 0, height = 0, weight = 0f), title = "", reportDate = "", reportType = "", summary = "", status = ""), storageKey = "", originalFileName = "", mimeType = "", fileSize = 0L, sha256 = "")
}
