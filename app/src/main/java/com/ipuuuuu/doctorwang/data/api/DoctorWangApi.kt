package com.ipuuuuu.doctorwang.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import okhttp3.MultipartBody
import okhttp3.RequestBody

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
    val processingJobId: String? = null,
    val statusUrl: String? = null,
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
    @SerialName("reportDate") val date: String,
    @SerialName("reportType") val type: String,
    val summary: String? = null,
    val findings: List<String>,
    val suggestions: List<String>,
    // 后端 ReportDetailV1Response 自带健康辅助边界声明，客户端必须展示。
    val disclaimer: String? = null,
)

@Serializable
data class ReportStatusResponse(
    val reportId: Long,
    val status: String,
    // Backend contract (ReportApiModels.ReportStatusResponse):
    // reportId, processingJobId, status, stage, retryable, errorCode,
    // updatedAt, traceId. Unknown fields are ignored by NetworkProvider Json.
    @SerialName("stage") val phase: String? = null,
    @SerialName("retryable") val canRetry: Boolean = false,
    @SerialName("errorCode") val failureCategory: String? = null,
    // Backend has no requiresReview field; REVIEW_REQUIRED is derived from
    // errorCode=="REVIEW_REQUIRED" in ReportStatusResponse.toDomain()
    // (backend fail-closes review-required results as status=FAILED).
    val requiresReview: Boolean = false,
    val traceId: String? = null,
)

@Serializable
data class RegisterRequest(
    val account: String,
    val password: String,
    val name: String,
    val age: Int,
    val height: Int,
    val weight: Float,
)

@Serializable
data class ProfileUpdateRequest(
    val name: String? = null,
    val age: Int? = null,
    val height: Int? = null,
    val weight: Float? = null,
)

@Serializable
data class PasswordChangeRequest(
    val oldPassword: String,
    val newPassword: String,
)

@Serializable
data class ReportListItem(
    val reportId: Long,
    val title: String,
    val reportDate: String,
    val reportType: String,
    val status: String,
    val summary: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class ReportListResponse(
    val items: List<ReportListItem>,
    val page: Int,
    val size: Int,
    val totalPages: Int,
    val totalElements: Long,
)

interface DoctorWangApi {
    // 公开健康检查（AuthTokenFilter 白名单），用于"测试连接"无需登录
    @GET("/healthz")
    suspend fun healthz(): Map<String, String>

    @POST("/api/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("/api/logout")
    suspend fun logout()

    @POST("/api/register")
    suspend fun register(@Body request: RegisterRequest): LoginResponse

    @GET("/api/profile")
    suspend fun getProfile(): UserProfileResponse

    @PUT("/api/profile")
    suspend fun updateProfile(@Body request: ProfileUpdateRequest): UserProfileResponse

    @GET("/api/health/profile")
    suspend fun getHealthProfile(): UserProfileResponse

    @POST("/api/password/change")
    suspend fun changePassword(@Body request: PasswordChangeRequest)

    @GET("/api/chat/history")
    suspend fun getChatHistory(): List<ChatMessageResponse>

    @POST("/api/chat")
    suspend fun chat(@Body request: ChatRequest): ChatResponse

    @Multipart
    @POST("/api/v1/reports")
    suspend fun uploadReport(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Part file: MultipartBody.Part,
        @Part("reportDate") reportDate: RequestBody? = null,
        @Part("reportType") reportType: RequestBody? = null,
    ): UploadResponse

    @GET("/api/v1/reports/{reportId}")
    suspend fun getReport(@Path("reportId") reportId: Long): MedicalReportResponse

    @GET("/api/v1/reports")
    suspend fun listReports(
        @retrofit2.http.Query("page") page: Int = 0,
        @retrofit2.http.Query("size") size: Int = 20,
    ): ReportListResponse

    @GET("/api/v1/reports/{reportId}/status")
    suspend fun getReportStatus(@Path("reportId") reportId: Long): ReportStatusResponse

    @retrofit2.http.Streaming
    @GET("/api/v1/reports/{reportId}/file")
    suspend fun downloadReportFile(@Path("reportId") reportId: Long): okhttp3.ResponseBody
}
