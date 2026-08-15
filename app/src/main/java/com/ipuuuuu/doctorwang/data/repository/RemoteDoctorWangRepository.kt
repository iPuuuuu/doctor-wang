package com.ipuuuuu.doctorwang.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.ipuuuuu.doctorwang.data.api.ChatMessageResponse
import com.ipuuuuu.doctorwang.data.api.ChatRequest
import com.ipuuuuu.doctorwang.data.api.HealthRecordResponse
import com.ipuuuuu.doctorwang.data.api.LoginRequest
import com.ipuuuuu.doctorwang.data.api.MedicalReportResponse
import com.ipuuuuu.doctorwang.data.api.PasswordChangeRequest
import com.ipuuuuu.doctorwang.data.api.ProfileUpdateRequest
import com.ipuuuuu.doctorwang.data.api.RegisterRequest
import com.ipuuuuu.doctorwang.data.api.ReportStatusResponse
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
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

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

    override suspend fun register(account: String, password: String, name: String, age: Int, height: Int, weight: Float): Result<Unit> {
        return networkCall {
            val response = api().register(
                RegisterRequest(account = account, password = password, name = name, age = age, height = height, weight = weight),
            )
            sessionManager.saveSession(token = response.token, userId = response.userId)
        }
    }

    override suspend fun logout() {
        try {
            // Revoke the server-side JTI when reachable. Local cleanup is
            // unconditional so logout remains safe during network failure.
            api().logout()
        } catch (_: Exception) {
            // Do not retain a bearer token merely because the revoke request
            // could not complete; the next login establishes a new session.
        } finally {
            sessionManager.clearSession()
        }
    }

    override suspend fun clearSession() {
        sessionManager.clearSession()
    }

    override suspend fun getProfile(): UserProfile {
        return networkCall { api().getProfile().toDomain() }.getOrThrow()
    }

    override suspend fun updateProfile(name: String?, age: Int?, height: Int?, weight: Float?): Result<UserProfile> {
        return networkCall {
            api().updateProfile(ProfileUpdateRequest(name = name, age = age, height = height, weight = weight)).toDomain()
        }
    }

    override suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit> {
        return networkCall { api().changePassword(PasswordChangeRequest(oldPassword, newPassword)) }
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
            persistChat(content, currentMessages.size, assistantMessage)
            assistantMessage
        }
    }

    override suspend fun getAnxietyHistoryMessages(): List<Message> {
        return networkCall {
            val remoteMessages = api().anxietyHistory().map(ChatMessageResponse::toDomain)
            messageDao.insertAll(remoteMessages.map(Message::toEntity))
            remoteMessages
        }.getOrElse {
            messageDao.getAll().map(MessageEntity::toDomain)
        }
    }

    override suspend fun sendAnxietyMessage(content: String, currentMessages: List<Message>): Result<Message> {
        return networkCall {
            val response = api().anxietyChat(ChatRequest(message = content))
            val assistantMessage = Message(
                id = "anxiety-${currentMessages.size + 1}",
                content = response.message,
                role = "assistant",
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.SENT,
                warnings = response.warnings,
                citations = response.citations.map { it.toDomain() },
            )
            persistChat(content, currentMessages.size, assistantMessage)
            assistantMessage
        }
    }

    private suspend fun persistChat(content: String, index: Int, assistantMessage: Message) {
        messageDao.insertAll(
            listOf(
                Message(
                    id = "user-${index + 1}",
                    content = content,
                    role = "user",
                    timestamp = assistantMessage.timestamp - 1,
                    status = MessageStatus.SENT,
                ).toEntity(),
                assistantMessage.toEntity(),
            ),
        )
    }

    override suspend fun uploadReport(uri: Uri?): Result<Long> {
        if (uri == null) return Result.failure(IllegalArgumentException("请选择要上传的报告文件"))
        return networkCall {
            val fileName = resolveFileName(uri)
            val contentType = contentResolver.getType(uri)
                ?.lowercase()
                ?.takeIf { it in SUPPORTED_MEDIA_TYPES }
                ?: throw IllegalArgumentException("仅支持 PDF、JPG、PNG 文件")
            // 先查长度再读入，避免超大文件整体 readBytes() 造成 OOM。
            val size = contentResolver.openAssetFileDescriptor(uri, "r")?.length
                ?: throw IllegalArgumentException("无法读取所选文件")
            if (size > MAX_UPLOAD_BYTES) throw IllegalArgumentException("文件大小不能超过 20MB")
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("无法读取所选文件")
            val body = bytes.toRequestBody(contentType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", fileName, body)
            api().uploadReport(
                // 内容哈希作幂等键：同一文件重试/重复提交命中服务端 dedup，而非每次新 UUID。
                idempotencyKey = sha256Hex(bytes),
                file = part,
            ).reportId
        }
    }

    override suspend fun getReport(reportId: Long): MedicalReport {
        return networkCall { api().getReport(reportId).toDomain() }.getOrThrow()
    }

    override suspend fun getReportStatus(reportId: Long): MedicalReport {
        return networkCall { api().getReportStatus(reportId).toDomain() }.getOrThrow()
    }

    override suspend fun listReports(page: Int, size: Int): Result<ReportPage> {
        return networkCall {
            val response = api().listReports(page, size)
            ReportPage(items = response.items, totalPages = response.totalPages)
        }
    }

    override suspend fun retryReport(reportId: Long): Result<MedicalReport> {
        return networkCall { api().retryReport(reportId).toDomain() }
    }

    override suspend fun deleteReport(reportId: Long): Result<Unit> {
        return networkCall { api().deleteReport(reportId); Unit }
    }

    override suspend fun downloadReportFile(reportId: Long): Result<okhttp3.ResponseBody> {
        return networkCall { api().downloadReportFile(reportId) }
    }

    suspend fun getServerUrl(): String = sessionManager.getServerUrl()

    suspend fun saveServerUrl(value: String) {
        sessionManager.saveServerUrl(value)
    }

    suspend fun testConnection(): Result<Unit> {
        // 用公开健康检查端点验证连通性，不要求登录
        return networkCall { api().healthz(); Unit }
    }

    private suspend fun api() = networkProvider.createApi(sessionManager.getServerUrl())

    private suspend inline fun <T> networkCall(crossinline block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: CancellationException) {
            // 结构化并发：协程取消必须向上传播，不能被包装成业务错误。
            throw e
        } catch (e: Throwable) {
            Result.failure(mapNetworkError(e))
        }
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

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MAX_UPLOAD_BYTES = 20L * 1024 * 1024
        val SUPPORTED_MEDIA_TYPES = setOf("application/pdf", "image/jpeg", "image/png")
    }

    private fun mapNetworkError(error: Throwable): Throwable {
        return when (error) {
            is ConnectException, is SocketTimeoutException -> IOException(
                "无法连接到服务，请检查网络连接后重试。",
                error,
            )
            is HttpException -> {
                val serverMessage = extractErrorMessage(error.response())
                if (error.code() == 401) {
                    SessionExpiredException(serverMessage ?: "登录状态已失效，请重新登录", error)
                } else {
                    // 服务端业务错误（400/409/413/415/422 等）应透出真实 message，
                    // 例如改密旧密码错误（PASSWORD_MISMATCH）、账号已存在（ACCOUNT_EXISTS）。
                    IOException(serverMessage ?: "服务请求失败（${error.code()}），请稍后重试。", error)
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
        summary = summary ?: "",
        findings = findings,
        suggestions = suggestions,
        disclaimer = disclaimer,
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
        // 后端对需复核的 AI 结果 fail closed：job.status="FAILED" + errorCode="REVIEW_REQUIRED"
        // （PostgreSqlPollingWorker.fail()），从不发出 status="REVIEW_REQUIRED"。
        requiresReview = requiresReview || failureCategory == "REVIEW_REQUIRED",
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
