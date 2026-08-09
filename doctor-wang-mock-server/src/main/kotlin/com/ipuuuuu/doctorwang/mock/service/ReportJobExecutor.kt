package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.persistence.entity.ReportProcessingJobEntity

data class ReportJobResult(val jobId: String, val reportId: Long, val attempt: Int, val traceId: String, val processingVersion: String, val parserVersion: String? = null, val modelVersion: String? = null, val promptVersion: String? = null, val safetyPolicyVersion: String? = null, val status: String, val summary: String? = null, val findings: List<String> = emptyList(), val suggestions: List<String> = emptyList(), val citations: List<String> = emptyList(), val confidence: Double? = null, val reviewRequired: Boolean = false, val errorCode: String? = null, val resultDigest: String? = null)
interface ReportJobExecutor { fun execute(job: ReportProcessingJobEntity): ReportJobResult }
