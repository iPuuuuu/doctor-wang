package com.ipuuuuu.doctorwang.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.ipuuuuu.doctorwang.data.api.ChatRequest
import com.ipuuuuu.doctorwang.data.api.DoctorWangApi
import com.ipuuuuu.doctorwang.data.api.HealthRecordResponse
import com.ipuuuuu.doctorwang.data.api.LoginRequest
import com.ipuuuuu.doctorwang.data.api.MedicalReportResponse
import com.ipuuuuu.doctorwang.data.api.UploadRequest
import com.ipuuuuu.doctorwang.data.api.UserProfileResponse
import com.ipuuuuu.doctorwang.data.model.HealthRecord
import com.ipuuuuu.doctorwang.data.model.MedicalReport
import com.ipuuuuu.doctorwang.data.model.Message
import com.ipuuuuu.doctorwang.data.model.MessageStatus
import com.ipuuuuu.doctorwang.data.model.UserProfile
import com.ipuuuuu.doctorwang.storage.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteDoctorWangRepository @Inject constructor(
    private val api: DoctorWangApi,
    private val sessionManager: SessionManager,
    private val contentResolver: ContentResolver,
) : DoctorWangRepository {

    override suspend fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()

    override suspend fun login(account: String, password: String): Result<Unit> {
        return runCatching {
            val response = api.login(LoginRequest(account = account, password = password))
            sessionManager.saveSession(token = response.token, userId = response.userId)
        }
    }

    override suspend fun logout() {
        sessionManager.clearSession()
    }

    override suspend fun getProfile(): UserProfile {
        return api.getProfile().toDomain()
    }

    override suspend fun getHealthProfile(): UserProfile {
        return api.getHealthProfile().toDomain()
    }

    override suspend fun getHistoryMessages(): List<Message> {
        return listOf(
            Message(
                id = "history-user-1",
                content = "最近胃有点不舒服",
                role = "user",
                timestamp = System.currentTimeMillis() - 120_000,
            ),
            Message(
                id = "history-assistant-1",
                content = "根据你的历史记录，你之前有胃炎记录。建议近期保持清淡饮食，避免辛辣刺激。",
                role = "assistant",
                timestamp = System.currentTimeMillis() - 60_000,
            ),
        )
    }

    override suspend fun sendMessage(content: String, currentMessages: List<Message>): Result<Message> {
        return runCatching {
            val response = api.chat(ChatRequest(message = content))
            Message(
                id = "assistant-${currentMessages.size + 1}",
                content = response.answer,
                role = "assistant",
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.SENT,
            )
        }
    }

    override suspend fun uploadReport(uri: Uri?): Result<Long> {
        val fileName = resolveFileName(uri)
        return runCatching {
            api.uploadReport(UploadRequest(fileName = fileName)).reportId
        }
    }

    override suspend fun getReport(reportId: Long): MedicalReport {
        return api.getReport(reportId).toDomain()
    }

    private fun resolveFileName(uri: Uri?): String {
        if (uri == null) return "检查报告"
        val cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    return it.getString(index)
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "检查报告"
    }
}

private fun UserProfileResponse.toDomain(): UserProfile {
    return UserProfile(
        userId = userId,
        name = name,
        age = age,
        height = height,
        weight = weight,
        historyDiseases = historyDiseases,
        records = records.map(HealthRecordResponse::toDomain),
    )
}

private fun HealthRecordResponse.toDomain(): HealthRecord {
    return HealthRecord(
        date = date,
        type = type,
    )
}

private fun MedicalReportResponse.toDomain(): MedicalReport {
    return MedicalReport(
        reportId = reportId,
        title = title,
        date = date,
        type = type,
        summary = summary,
        findings = findings,
        suggestions = suggestions,
    )
}
