package com.ipuuuuu.doctorwang.mock.model

import java.time.OffsetDateTime
import java.util.UUID

data class AiIngestionRequest(
    val requestId: String, val reportId: Long, val userId: Long, val namespace: String,
    val fileName: String, val contentType: String, val objectKey: String, val signedUrl: String? = null,
    val fileSize: Long, val sourceVersion: String, val traceId: String,
)
data class AiIngestionResponse(val jobId: String, val status: String, val acceptedAt: OffsetDateTime? = null)
data class AiIngestionStatus(val jobId: String, val status: String, val reportId: Long? = null, val traceId: String? = null,
                             val requiresReview: Boolean = false, val errorCode: String? = null,
                             val resultVersion: String? = null)
data class AiRagRequest(val requestId: String, val userId: Long, val reportId: Long, val question: String,
                        val namespaceHints: List<String>, val topK: Int = 5, val traceId: String,
                        val mode: String = "general", val healthProfile: String = "")
data class AiCitation(val sourceType: String, val sourceId: String, val chunkId: String, val pageNo: Int?, val excerpt: String, val confidence: Double, val documentVersion: String)
data class AiRagResponse(val requestId: String, val status: String, val riskLevel: String, val summary: String,
                         val citations: List<AiCitation> = emptyList(), val warnings: List<String> = emptyList(),
                         val needsReview: Boolean = false, val traceId: String)
