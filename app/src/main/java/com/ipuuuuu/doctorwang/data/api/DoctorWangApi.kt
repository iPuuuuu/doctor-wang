package com.ipuuuuu.doctorwang.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Streaming

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
data class RegisterRequest(val account: String, val password: String)

@Serializable
data class ChatRequest(
    val message: String,
)

@Serializable
data class ChatResponse(
    val answer: String,
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
data class UpdateProfileRequest(val name: String, val age: Int, val height: Int, val weight: Float, val historyDiseases: List<String>)

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
data class ReportListItemResponse(
    val reportId: Long,
    val title: String,
    val date: String,
    val type: String,
    val status: String,
)

interface DoctorWangApi {
    @POST("/api/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("/api/register")
    suspend fun register(@Body request: RegisterRequest): LoginResponse

    @GET("/api/profile")
    suspend fun getProfile(@Header("Authorization") authorization: String): UserProfileResponse

    @GET("/api/health/profile")
    suspend fun getHealthProfile(@Header("Authorization") authorization: String): UserProfileResponse

    @PUT("/api/profile")
    suspend fun updateProfile(@Header("Authorization") authorization: String, @Body request: UpdateProfileRequest): UserProfileResponse

    @POST("/api/chat")
    suspend fun chat(
        @Header("Authorization") authorization: String,
        @Body request: ChatRequest,
    ): ChatResponse

    @Multipart
    @POST("/api/report/upload")
    suspend fun uploadReport(@Header("Authorization") authorization: String, @Part file: okhttp3.MultipartBody.Part): UploadResponse

    @GET("/api/report")
    suspend fun listReports(@Header("Authorization") authorization: String): List<ReportListItemResponse>

    @DELETE("/api/report/{reportId}")
    suspend fun deleteReport(
        @Header("Authorization") authorization: String,
        @Path("reportId") reportId: Long,
    )

    @Streaming
    @GET("/api/report/{reportId}/download")
    suspend fun downloadReport(
        @Header("Authorization") authorization: String,
        @Path("reportId") reportId: Long,
    ): ResponseBody

    @GET("/api/report/{reportId}")
    suspend fun getReport(
        @Header("Authorization") authorization: String,
        @Path("reportId") reportId: Long,
    ): MedicalReportResponse
}
