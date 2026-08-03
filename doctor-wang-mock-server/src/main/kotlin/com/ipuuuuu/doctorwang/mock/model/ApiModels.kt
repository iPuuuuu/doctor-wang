package com.ipuuuuu.doctorwang.mock.model

data class LoginRequest(
    val account: String,
    val password: String,
)

data class RegisterRequest(
    val account: String,
    val password: String,
)

data class LoginResponse(
    val token: String,
    val userId: String,
)

data class ChatRequest(
    val message: String,
)

data class ChatResponse(
    val answer: String,
    val provider: String = "mock",
)

data class UploadRequest(
    val fileName: String,
)

data class UploadResponse(
    val reportId: Long,
    val status: String,
)

data class UserProfileResponse(
    val userId: String,
    val name: String,
    val age: Int,
    val height: Int,
    val weight: Float,
    val historyDiseases: List<String>,
    val records: List<HealthRecordResponse>,
)

data class UpdateProfileRequest(
    val name: String,
    val age: Int,
    val height: Int,
    val weight: Float,
    val historyDiseases: List<String>,
)

data class HealthRecordResponse(
    val date: String,
    val type: String,
)

data class MedicalReportResponse(
    val reportId: Long,
    val title: String,
    val date: String,
    val type: String,
    val summary: String,
    val findings: List<String>,
    val suggestions: List<String>,
)

data class ReportListItemResponse(
    val reportId: Long,
    val title: String,
    val date: String,
    val type: String,
    val status: String,
)

data class ChatRule(
    val keyword: String,
    val answer: String,
)

data class AiChatRequest(
    val userId: String,
    val message: String,
)

data class AiChatResult(
    val answer: String,
    val provider: String,
)

data class ReportAnalysisResult(
    val summary: String,
    val findings: List<String>,
    val suggestions: List<String>,
)
