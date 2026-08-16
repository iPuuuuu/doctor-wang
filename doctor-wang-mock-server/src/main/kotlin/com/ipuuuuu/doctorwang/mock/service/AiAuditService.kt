package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.persistence.entity.AiAuditEventEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity
import com.ipuuuuu.doctorwang.mock.persistence.repository.AiAuditEventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * AI 交互审计：记录每次 AI 请求的可追溯元数据（事件类型/版本/traceId/状态/耗时等）。
 * 审计失败不影响主流程（审计是记录而非门禁），但会记 warning 日志。
 */
@Service
class AiAuditService(private val repository: AiAuditEventRepository) {
    private val log = LoggerFactory.getLogger(AiAuditService::class.java)

    @Transactional
    fun record(
        user: AppUserEntity,
        eventType: String,
        traceId: String,
        modelVersion: String? = null,
        promptVersion: String? = null,
        safetyPolicyVersion: String? = null,
        metadata: Map<String, Any?> = emptyMap(),
    ) {
        try {
            val json = metadata.entries.joinToString(",") { (k, v) ->
                val escaped = v?.toString()?.replace("\"", "\\\"") ?: "null"
                "\"$k\":\"$escaped\""
            }.let { if (it.isEmpty()) "{}" else "{$it}" }
            repository.save(AiAuditEventEntity(
                user = user,
                eventType = eventType,
                modelVersion = modelVersion,
                promptVersion = promptVersion,
                safetyPolicyVersion = safetyPolicyVersion,
                traceId = traceId,
                metadataJson = json,
            ))
        } catch (e: Exception) {
            log.warn("audit record failed: {}", e.message)
        }
    }
}
