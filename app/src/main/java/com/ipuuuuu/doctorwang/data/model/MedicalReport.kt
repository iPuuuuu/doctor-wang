package com.ipuuuuu.doctorwang.data.model

data class MedicalReport(
    val reportId: Long,
    val title: String,
    val date: String,
    val type: String,
    val summary: String,
    val findings: List<String>,
    val suggestions: List<String>,
    val phase: String? = null,
    val canRetry: Boolean = false,
    val requiresReview: Boolean = false,
    val failureCategory: String? = null,
)
