package com.ipuuuuu.doctorwang.data.api

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

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
data class ChatResponse(
    val answer: String,
)

@Serializable
data class UploadResponse(
    val reportId: Long,
)

interface DoctorWangApi {
    @POST("/api/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("/api/profile")
    suspend fun getProfile(): String

    @POST("/api/chat")
    suspend fun chat(@Body request: ChatRequest): ChatResponse

    @POST("/api/report/upload")
    suspend fun uploadReport(@Body body: Map<String, String>): UploadResponse

    @GET("/api/health/profile")
    suspend fun getHealthProfile(): String
}
