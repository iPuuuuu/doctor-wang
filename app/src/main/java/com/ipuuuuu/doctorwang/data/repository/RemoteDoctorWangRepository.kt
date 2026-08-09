package com.ipuuuuu.doctorwang.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.ipuuuuu.doctorwang.data.api.ChatMessageResponse
import com.ipuuuuu.doctorwang.data.api.ChatRequest
import com.ipuuuuu.doctorwang.data.api.HealthRecordResponse
import com.ipuuuuu.doctorwang.data.api.LoginRequest
import com.ipuuuuu.doctorwang.data.api.MedicalReportResponse
import com.ipuuuuu.doctorwang.data.api.ReportStatusResponse
import com.ipuuuuu.doctorwang.data.api.UploadRequest
import com.ipuuuuu.doctorwang.data.api.UserProfileResponse
import com.ipuuuuu.doctorwang.data.model.HealthRecord
import com.ipuuuuu.doctorwang.data.model.MedicalReport
import com.ipuuuuu.doctorwang.data.model.Message
import com.ipuuuuu.doctorwang.data.model.MessageCitation
import com.ipuuuuu.doctorwang.data.model.MessageStatus
import com.ipuuuuu.doctorwang.data.model.UserProfile
import com.ipuuuuu.doctorwang.database.dao.MessageDao
import com.ipuuuuu.doctorwang.database.entity.MessageEntity
import com.ipuuuuu.doctorwang.network.NetworkProvider
import com.ipuuuuu.doctorwang.storage.SessionManager
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException
import retrofit2.Response

@Singleton
class RemoteDoctorWangRepository @Inject constructor(
    private val networkProvider: NetworkProvider,
    private val sessionManager: SessionManager,
    private val contentResolver: ContentResolver,
    private val messageDao: MessageDao,
) : DoctorWangRepository {

    override suspend fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()

    override suspend fun login(account: String, password: String): Result<Unit> {
        return networkCall {
            val response = api().login(LoginRequest(account = account, password = password))
            sessionManager.saveSession(token = response.token, userId = response.userId)
        }
    }

    override suspend fun logout() {
        sessionManager.clearSession()
    }

    override suspend fun clearSession() {
        sessionManager.clearSession()
    }

    override suspend fun getProfile(): UserProfile {
        return networkCall { api().getProfile().toDomain() }.getOrThrow()
    }

    override suspend fun getHealthProfile(): UserProfile {
        return networkCall { api().getHealthProfile().toDomain() }.getOrThrow()
    }

    override suspend fun getHistoryMessages(): List<Message> {
        return networkCall {
            val remoteMessages = api().getChatHistory().map(ChatMessageResponse::toDomain)
            messageDao.insertAll(remoteMessages.map(Message::toEntity))
            remoteMessages
        }.getOrElse {
            messageDao.getAll().map(MessageEntity::toDomain)
        }
    }

    override suspend fun sendMessage(content: String, currentMessages: List<Message>): Result<Message> {
        return networkCall {
            val response = api().chat(ChatRequest(message = content))
            val assistantMessage = Message(
                id = "assistant-${currentMessages.size + 1}",
                content = response.message,
                role = "assistant",
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.SENT,
                warnings = response.warnings,
                citations = response.citations.map { it.toDomain() },
            )
            messageDao.insertAll(
                listOf(
                    Message(
                        id = "user-${currentMessages.size + 1}",
                        content = content,
                        role = "user",
                        timestamp = assistantMessage.timestamp - 1,
                        status = MessageStatus.SENT,
                    ).toEntity(),
                    assistantMessage.toEntity(),
                ),
            )
            assistantMessage
        }
    }

    override suspend fun uploadReport(uri: Uri?): Result<Long> {
        val fileName = resolveFileName(uri)
        return networkCall {
            api().uploadReport(UploadRequest(fileName = fileName)).reportId
        }
    }

    override suspend fun getReport(reportId: Long): MedicalReport {
        return networkCall { api().getReport(reportId).toDomain() }.getOrThrow()
    }

    override suspend fun getReportStatus(reportId: Long): MedicalReport {
        return networkCall { api().getReportStatus(reportId).toDomain() }.getOrThrow()
    }

    suspend fun getServerUrl(): String = sessionManager.getServerUrl()

    suspend fun saveServerUrl(value: String) {
        sessionManager.saveServerUrl(value)
    }

    suspend fun testConnection(): Result<Unit> {
        return networkCall { api().getProfile(); Unit }
    }

    private suspend fun api() = networkProvider.createApi(sessionManager.getServerUrl())

    private suspend inline fun <T> networkCall(crossinline block: suspend () -> T): Result<T> {
        return runCatching { block() }.mapError(::mapNetworkError)
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

    private fun mapNetworkError(error: Throwable): Throwable {
        return when (error) {
            is ConnectException, is SocketTimeoutException -> IOException(
                "无法连接到 Doctor-Wang 服务，请确认当前服务地址可访问；本地调试时请检查后端服务与网络连接。",
                error,
            )
            is HttpException -> {
                if (error.code() == 401) {
                    SessionExpiredException(extractErrorMessage(error.response()) ?: "登录状态已失效，请重新登录", error)
                } else {
                    IOException("服务请求失败（${error.code()}），请稍后重试。", error)
                }
            }
            is IOException -> IOException(
                "网络请求失败，请确认服务地址填写正确，或检查当前网络与后端服务状态。",
                error,
            )
            else -> error
        }
    }
}

class SessionExpiredException(
    message: String = "登录状态已失效，请重新登录",
    cause: Throwable? = null,
) : IOException(message, cause)

private fun extractErrorMessage(response: Response<*>?): String? {
    return response
        ?.errorBody()
        ?.string()
        ?.substringAfter("\"message\":\"")
        ?.substringBefore('"')
        ?.takeIf { it.isNotBlank() }
}

private inline fun <T> Result<T>.mapError(transform: (Throwable) -> Throwable): Result<T> {
    return fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(transform(it)) },
    )
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

private fun ReportStatusResponse.toDomain(): MedicalReport {
    return MedicalReport(
        reportId = reportId,
        title = "报告 #$reportId",
        date = "",
        type = "",
        summary = status,
        findings = listOfNotNull(phase),
        suggestions = buildList {
            if (canRetry) add("可重试")
            if (requiresReview) add("需要人工复核")
            failureCategory?.let { add(it) }
        },
        phase = phase,
        canRetry = canRetry,
        requiresReview = requiresReview,
        failureCategory = failureCategory,
    )
}

private fun ChatMessageResponse.toDomain(): Message {
    return Message(
        id = id,
        content = content,
        role = role,
        timestamp = timestamp,
        status = MessageStatus.SENT,
    )
}

private fun com.ipuuuuu.doctorwang.data.api.ChatCitationResponse.toDomain(): MessageCitation {
    return MessageCitation(
        sourceType = sourceType,
        sourceId = sourceId,
        pageNo = pageNo,
        excerpt = excerpt,
        confidence = confidence,
    )
}

private fun Message.toEntity(): MessageEntity {
    return MessageEntity(
        id = id,
        content = content,
        role = role,
        timestamp = timestamp,
    )
}

private fun MessageEntity.toDomain(): Message {
    return Message(
        id = id,
        content = content,
        role = role,
        timestamp = timestamp,
        status = MessageStatus.SENT,
    )
}
