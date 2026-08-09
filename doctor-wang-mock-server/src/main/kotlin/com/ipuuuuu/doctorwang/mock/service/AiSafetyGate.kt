package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.ApiException
import com.ipuuuuu.doctorwang.mock.model.AiRagResponse

/** Backend-side fail-closed checks; this does not replace AI Service safety review or evaluation. */
object AiSafetyGate {
    fun validate(response: AiRagResponse, reportId: Long): AiRagResponse {
        if (response.status == "OK" && response.citations.isEmpty()) throw ApiException("AI_RESULT_INVALID", "AI 结果缺少可追溯证据")
        if (response.citations.any { it.sourceId != reportId.toString() }) throw ApiException("AI_RESULT_INVALID", "AI 结果包含不可验证来源")
        if (response.status == "SAFETY_BLOCKED" || response.riskLevel == "HIGH") return response.copy(needsReview = true)
        if (response.status == "INSUFFICIENT_EVIDENCE") return response.copy(needsReview = true)
        return response
    }
}
