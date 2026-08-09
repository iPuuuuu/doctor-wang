package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.ApiException
import org.springframework.stereotype.Component

@Component
class ReportJobStateMachine {
    fun transition(status: String, stage: String, target: String, targetStage: String): Pair<String, String> {
        val allowed = when (status to target) {
            "STORED" to "PROCESSING" -> true
            "PROCESSING" to "FAILED", "PROCESSING" to "RETRYING", "PROCESSING" to "COMPLETED" -> true
            "FAILED" to "RETRYING", "RETRYING" to "PROCESSING", "RETRYING" to "FAILED" -> true
            else -> false
        }
        if (!allowed || !validStage(target, targetStage)) throw ApiException("REPORT_PROCESSING_CONFLICT", "非法的报告处理状态迁移")
        return target to targetStage
    }

    private fun validStage(status: String, stage: String) = when (status) {
        "STORED" -> stage == "STORED"
        "PROCESSING" -> stage in setOf("EXTRACTING", "CHUNKING", "INDEXING", "ANALYZING", "SAFETY_REVIEW")
        "RETRYING" -> stage == "STORED" || stage in setOf("EXTRACTING", "CHUNKING", "INDEXING", "ANALYZING", "SAFETY_REVIEW")
        "FAILED" -> stage in setOf("STORED", "EXTRACTING", "CHUNKING", "INDEXING", "ANALYZING", "SAFETY_REVIEW")
        "COMPLETED" -> stage == "FINISHED"
        else -> false
    }
}
