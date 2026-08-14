package com.ipuuuuu.doctorwang.mock.model

data class LoginRequest(
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

data class ChatMessageResponse(
    val id: String,
    val content: String,
    val role: String,
    val timestamp: Long,
)

data class ChatResponse(
    val message: String,
    val warnings: List<String> = emptyList(),
    val citations: List<ChatCitationResponse> = emptyList(),
    val traceId: String? = null,
)

data class ChatCitationResponse(
    val sourceType: String,
    val sourceId: String,
    val pageNo: Int? = null,
    val excerpt: String,
    val confidence: Double? = null,
)

data class RegisterRequest(
    val account: String,
    val password: String,
    val name: String,
    val age: Int,
    val height: Int,
    val weight: Float,
)

data class ProfileUpdateRequest(
    val name: String? = null,
    val age: Int? = null,
    val height: Int? = null,
    val weight: Float? = null,
)

data class PasswordChangeRequest(
    val oldPassword: String,
    val newPassword: String,
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
