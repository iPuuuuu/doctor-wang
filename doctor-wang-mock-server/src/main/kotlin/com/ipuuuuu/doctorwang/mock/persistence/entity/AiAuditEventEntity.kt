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

/**
 * AI 交互审计（AGENTS.md 安全规范：AI 输出必须可追溯）。
 * 记录每次 AI 请求的用户、traceId、事件类型、模型/提示词版本、元数据（风险/状态/耗时等）。
 * 表结构见 V3__add_report_processing.sql（ai_audit_event）。
 */
@Entity
@Table(name = "ai_audit_event")
class AiAuditEventEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: AppUserEntity,
    @Column(name = "event_type", nullable = false, length = 64)
    val eventType: String,
    @Column(name = "model_version", length = 128)
    val modelVersion: String? = null,
    @Column(name = "prompt_version", length = 128)
    val promptVersion: String? = null,
    @Column(name = "safety_policy_version", length = 128)
    val safetyPolicyVersion: String? = null,
    @Column(name = "trace_id", nullable = false, length = 128)
    val traceId: String,
    @Column(name = "metadata_json", columnDefinition = "text")
    val metadataJson: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
) {
    protected constructor() : this(
        user = AppUserEntity(userId = "", account = "", passwordHash = "", name = "", age = 0, height = 0, weight = 0f),
        eventType = "", traceId = "",
    )
}
