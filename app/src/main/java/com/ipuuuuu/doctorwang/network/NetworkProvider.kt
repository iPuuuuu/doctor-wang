package com.ipuuuuu.doctorwang.network

import com.ipuuuuu.doctorwang.data.api.DoctorWangApi
import com.ipuuuuu.doctorwang.storage.SessionManager
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkProvider @Inject constructor(
    private val sessionManager: SessionManager,
) {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val authInterceptor = okhttp3.Interceptor { chain ->
        val token = runBlocking { sessionManager.getToken() }
        val request = chain.request().newBuilder().apply {
            if (!token.isNullOrBlank()) {
                header("Authorization", "Bearer $token")
            }
        }.build()
        chain.proceed(request).also { response ->
            if (response.code == 401) {
                runBlocking { sessionManager.clearSession() }
            }
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        // AI 生成/自校正（生成→校验→回溯）耗时可达 60-90s，须与后端 readTimeoutMs=90000 对齐
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun createApi(baseUrl: String): DoctorWangApi {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DoctorWangApi::class.java)
    }
}
