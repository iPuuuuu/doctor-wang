package com.ipuuuuu.doctorwang.mock.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "medical_report_finding")
class MedicalReportFindingEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    val report: MedicalReportEntity,
    @Column(name = "finding_text", nullable = false, columnDefinition = "text")
    val findingText: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
) {
    protected constructor() : this(report = MedicalReportEntity(user = AppUserEntity(userId = "", account = "", passwordHash = "", name = "", age = 0, height = 0, weight = 0f), title = "", reportDate = "", reportType = "", summary = "", status = ""), findingText = "")
}
