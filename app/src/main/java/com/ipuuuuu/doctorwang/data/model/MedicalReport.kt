package com.ipuuuuu.doctorwang.data.model

data class MedicalReport(
    val reportId: Long,
    val title: String,
    val date: String,
    val type: String,
    val summary: String,
    val findings: List<String>,
    val suggestions: List<String>,
)

data class ReportListItem(
    val reportId: Long,
    val title: String,
    val date: String,
    val type: String,
    val status: String,
)

data class ReportDownload(
    val fileName: String,
    val mediaType: String,
    val bytes: ByteArray,
)
