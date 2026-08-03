package com.ipuuuuu.doctorwang.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.ipuuuuu.doctorwang.data.api.ChatRequest
import com.ipuuuuu.doctorwang.data.api.HealthRecordResponse
import com.ipuuuuu.doctorwang.data.api.LoginRequest
import com.ipuuuuu.doctorwang.data.api.MedicalReportResponse
import com.ipuuuuu.doctorwang.data.api.RegisterRequest
import com.ipuuuuu.doctorwang.data.api.ReportListItemResponse
import com.ipuuuuu.doctorwang.data.api.UpdateProfileRequest
import com.ipuuuuu.doctorwang.data.api.UserProfileResponse
import com.ipuuuuu.doctorwang.data.model.HealthRecord
import com.ipuuuuu.doctorwang.data.model.MedicalReport
import com.ipuuuuu.doctorwang.data.model.Message
import com.ipuuuuu.doctorwang.data.model.MessageStatus
import com.ipuuuuu.doctorwang.data.model.ReportDownload
import com.ipuuuuu.doctorwang.data.model.ReportListItem
import com.ipuuuuu.doctorwang.data.model.UserProfile
import com.ipuuuuu.doctorwang.database.dao.MessageDao
import com.ipuuuuu.doctorwang.database.entity.MessageEntity
import com.ipuuuuu.doctorwang.network.NetworkProvider
import com.ipuuuuu.doctorwang.storage.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException

@Singleton
class RemoteDoctorWangRepository @Inject constructor(
    private val networkProvider: NetworkProvider,
    private val sessionManager: SessionManager,
    private val contentResolver: ContentResolver,
    private val messageDao: MessageDao,
    @ApplicationContext private val context: Context,
) : DoctorWangRepository {

    override suspend fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()

    override suspend fun login(account: String, password: String): Result<Unit> {
        return networkCall {
            val response = api().login(LoginRequest(account = account, password = password))
            sessionManager.saveSession(token = response.token, userId = response.userId)
        }
    }

    override suspend fun register(account: String, password: String): Result<Unit> = networkCall {
        val response = api().register(RegisterRequest(account, password))
        sessionManager.saveSession(response.token, response.userId)
    }

    override suspend fun logout() {
        sessionManager.clearSession()
    }

    override suspend fun getProfile(): UserProfile {
        return networkCall { api().getProfile(authorization()).toDomain() }.getOrThrow()
    }

    override suspend fun getHealthProfile(): UserProfile {
        return networkCall { api().getHealthProfile(authorization()).toDomain() }.getOrThrow()
    }

    override suspend fun updateProfile(profile: UserProfile): Result<UserProfile> = networkCall {
        api().updateProfile(
            authorization(),
            UpdateProfileRequest(profile.name, profile.age, profile.height, profile.weight, profile.historyDiseases),
        ).toDomain()
    }

    override suspend fun getHistoryMessages(): List<Message> {
        val userId = requireUserId()
        return messageDao.getAll(userId).map(MessageEntity::toDomain)
    }

    override suspend fun sendMessage(content: String, currentMessages: List<Message>): Result<Message> {
        val userId = requireUserId()
        val pendingUserMessage = currentMessages.lastOrNull { it.role == "user" && it.content == content }
        if (pendingUserMessage != null) {
            messageDao.insert(pendingUserMessage.toEntity(userId))
        }

        return networkCall {
            val response = api().chat(authorization(), ChatRequest(message = content))
            pendingUserMessage?.let { messageDao.updateStatus(it.id, MessageStatus.SENT.name) }
            val assistantMessage = Message(
                id = "assistant-${System.currentTimeMillis()}",
                content = response.answer,
                role = "assistant",
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.SENT,
            )
            messageDao.insert(assistantMessage.toEntity(userId))
            assistantMessage
        }.onFailure {
            pendingUserMessage?.let { messageDao.updateStatus(it.id, MessageStatus.FAILED.name) }
        }
    }

    override suspend fun uploadReport(uri: Uri?): Result<Long> {
        return networkCall {
            requireNotNull(uri) { "请先选择文件" }
            val fileName = resolveFileName(uri)
            val temp = File.createTempFile("doctor-wang-upload-", "-" + fileName, context.cacheDir)
            try {
                contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use(input::copyTo) }
                    ?: throw IOException("无法读取所选文件")
                val type = contentResolver.getType(uri)?.toMediaTypeOrNull()
                val part = MultipartBody.Part.createFormData("file", fileName, temp.asRequestBody(type))
                api().uploadReport(authorization(), part).reportId
            } finally {
                temp.delete()
            }
        }
    }

    override suspend fun listReports(): Result<List<ReportListItem>> {
        return networkCall {
            api().listReports(authorization()).map(ReportListItemResponse::toDomain)
        }
    }

    override suspend fun deleteReport(reportId: Long): Result<Unit> {
        return networkCall {
            api().deleteReport(authorization(), reportId)
            Unit
        }
    }

    override suspend fun downloadReport(reportId: Long): Result<ReportDownload> {
        return networkCall {
            val body = api().downloadReport(authorization(), reportId)
            ReportDownload(
                fileName = "report-$reportId",
                mediaType = body.contentType()?.toString() ?: "application/octet-stream",
                bytes = body.bytes(),
            )
        }
    }

    override suspend fun getReport(reportId: Long): MedicalReport {
        return networkCall { api().getReport(authorization(), reportId).toDomain() }.getOrThrow()
    }

    suspend fun getServerUrl(): String = sessionManager.getServerUrl()

    suspend fun saveServerUrl(value: String) {
        sessionManager.saveServerUrl(value)
    }

    suspend fun testConnection(): Result<Unit> {
        return networkCall { api().getProfile(authorization()); Unit }
    }

    private suspend fun api() = networkProvider.createApi(sessionManager.getServerUrl())
    private suspend fun authorization(): String = "Bearer " + (sessionManager.getToken() ?: throw IOException("请先登录"))
    private suspend fun requireUserId(): String = sessionManager.getUserId() ?: throw IOException("请先登录")

    private inline fun <T> networkCall(block: () -> T): Result<T> {
        return runCatching(block).mapError(::mapNetworkError)
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
                "无法连接到 Doctor-Wang 服务，请确认当前服务地址可访问；本地调试时请检查 mock server 与网络连接。",
                error,
            )
            is HttpException -> when (error.code()) {
                401 -> IOException("请先登录后再访问该功能。", error)
                403 -> IOException("你没有权限访问该病历。", error)
                404 -> IOException("病历不存在或已被删除。", error)
                else -> IOException("服务请求失败（${error.code()}），请稍后重试。", error)
            }
            is IOException -> IOException(
                "网络请求失败，请确认服务地址填写正确，或检查当前网络与后端服务状态。",
                error,
            )
            else -> error
        }
    }
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

private fun ReportListItemResponse.toDomain(): ReportListItem {
    return ReportListItem(
        reportId = reportId,
        title = title,
        date = date,
        type = type,
        status = status,
    )
}

private fun MessageEntity.toDomain(): Message {
    return Message(
        id = id,
        content = content,
        role = role,
        timestamp = timestamp,
        status = MessageStatus.valueOf(status),
    )
}

private fun Message.toEntity(userId: String): MessageEntity {
    return MessageEntity(
        id = id,
        userId = userId,
        content = content,
        role = role,
        timestamp = timestamp,
        status = status.name,
    )
}
