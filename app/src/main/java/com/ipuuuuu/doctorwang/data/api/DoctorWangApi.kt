package com.ipuuuuu.doctorwang.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class LoginRequest(
    val account: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val token: String,
    val userId: String,
)

@Serializable
data class ChatRequest(
    val message: String,
)

@Serializable
data class ChatMessageResponse(
    val id: String,
    val content: String,
    val role: String,
    val timestamp: Long,
)

@Serializable
data class ChatCitationResponse(
    val sourceType: String,
    val sourceId: String,
    val pageNo: Int? = null,
    val excerpt: String,
    val confidence: Double? = null,
)

@Serializable
data class ChatResponse(
    val message: String,
    val warnings: List<String> = emptyList(),
    val citations: List<ChatCitationResponse> = emptyList(),
    val traceId: String? = null,
)

@Serializable
data class UploadRequest(
    val fileName: String,
)

@Serializable
data class UploadResponse(
    val reportId: Long,
    val status: String,
)

@Serializable
data class UserProfileResponse(
    val userId: String,
    val name: String,
    val age: Int,
    val height: Int,
    val weight: Float,
    @SerialName("historyDiseases") val historyDiseases: List<String>,
    val records: List<HealthRecordResponse>,
)

@Serializable
data class HealthRecordResponse(
    val date: String,
    val type: String,
)

@Serializable
data class MedicalReportResponse(
    val reportId: Long,
    val title: String,
    val date: String,
    val type: String,
    val summary: String,
    val findings: List<String>,
    val suggestions: List<String>,
)

@Serializable
data class ReportStatusResponse(
    val reportId: Long,
    val status: String,
    val phase: String? = null,
    val canRetry: Boolean = false,
    val requiresReview: Boolean = false,
    val failureCategory: String? = null,
    val traceId: String? = null,
)

interface DoctorWangApi {
    @POST("/api/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("/api/profile")
    suspend fun getProfile(): UserProfileResponse

    @GET("/api/health/profile")
    suspend fun getHealthProfile(): UserProfileResponse

    @GET("/api/chat/history")
    suspend fun getChatHistory(): List<ChatMessageResponse>

    @POST("/api/chat")
    suspend fun chat(@Body request: ChatRequest): ChatResponse

    @POST("/api/report/upload")
    suspend fun uploadReport(@Body request: UploadRequest): UploadResponse

    @GET("/api/report/{reportId}")
    suspend fun getReport(@Path("reportId") reportId: Long): MedicalReportResponse

    @GET("/api/v1/reports/{reportId}/status")
    suspend fun getReportStatus(@Path("reportId") reportId: Long): ReportStatusResponse
}
