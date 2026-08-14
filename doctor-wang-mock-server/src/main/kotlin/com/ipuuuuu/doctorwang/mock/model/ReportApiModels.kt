package com.ipuuuuu.doctorwang.mock.model

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.OffsetDateTime
import java.util.UUID

data class ReportUploadResponse(val reportId: Long, val processingJobId: UUID, val status: String, val statusUrl: String)
data class ReportStatusResponse(
    val reportId: Long, val processingJobId: UUID, val status: String, val stage: String,
    val retryable: Boolean, val errorCode: String?, val updatedAt: OffsetDateTime, val traceId: String,
)
data class ReportDetailV1Response(
    val reportId: Long, val title: String, val reportDate: String, val reportType: String,
    val status: String, val summary: String?, val findings: List<String>, val suggestions: List<String>,
    val disclaimer: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ReportListItemResponse(
    val reportId: Long,
    val title: String,
    val reportDate: String,
    val reportType: String,
    val status: String,
    val summary: String?,
    val createdAt: OffsetDateTime?,
)

data class ReportListResponse(
    val items: List<ReportListItemResponse>,
    val page: Int,
    val size: Int,
    val totalPages: Int,
    val totalElements: Long,
)
