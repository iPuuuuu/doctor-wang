package com.ipuuuuu.doctorwang.data.repository

import android.net.Uri
import com.ipuuuuu.doctorwang.data.api.ReportListItem
import com.ipuuuuu.doctorwang.data.model.HealthRecord
import com.ipuuuuu.doctorwang.data.model.MedicalReport
import com.ipuuuuu.doctorwang.data.model.Message
import com.ipuuuuu.doctorwang.data.model.MessageStatus
import com.ipuuuuu.doctorwang.data.model.UserProfile
import com.ipuuuuu.doctorwang.storage.SessionManager
import kotlinx.coroutines.delay
import okhttp3.ResponseBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeDoctorWangRepository @Inject constructor(
    private val sessionManager: SessionManager,
) : DoctorWangRepository {

    private val profile = UserProfile(
        userId = "u1001",
        name = "王先生",
        age = 25,
        height = 170,
        weight = 73f,
        historyDiseases = listOf("轻度脂肪肝", "胃炎"),
        records = listOf(
            HealthRecord(date = "2026-06", type = "腹部超声"),
            HealthRecord(date = "2026-07", type = "血液检查"),
        ),
    )

    private var latestReport = MedicalReport(
        reportId = 10001,
        title = "腹部超声",
        date = "2026-06-17",
        type = "影像报告",
        summary = "AI 初步判断存在轻度脂肪肝倾向。",
        findings = listOf("轻度脂肪肝"),
        suggestions = listOf("控制体重", "规律饮食", "复查肝功能"),
    )

    override suspend fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()

    override suspend fun login(account: String, password: String): Result<Unit> {
        delay(800)
        return if (account.isNotBlank() && password.isNotBlank()) {
            sessionManager.saveSession(token = "mock-token", userId = profile.userId)
            Result.success(Unit)
        } else {
            Result.failure(IllegalArgumentException("请输入账号和密码"))
        }
    }

    override suspend fun register(account: String, password: String, name: String, age: Int, height: Int, weight: Float): Result<Unit> {
        delay(1000)
        return if (account.isNotBlank() && password.length >= 6 && name.isNotBlank()) {
            sessionManager.saveSession(token = "mock-token", userId = "u2001")
            Result.success(Unit)
        } else {
            Result.failure(IllegalArgumentException("请填写完整的注册信息"))
        }
    }

    override suspend fun logout() {
        sessionManager.clearSession()
    }

    override suspend fun clearSession() {
        sessionManager.clearSession()
    }

    override suspend fun getProfile(): UserProfile {
        delay(250)
        return profile
    }

    override suspend fun updateProfile(name: String?, age: Int?, height: Int?, weight: Float?): Result<UserProfile> {
        delay(500)
        return Result.success(profile)
    }

    override suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit> {
        delay(500)
        return if (oldPassword.isNotBlank() && newPassword.length >= 6) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalArgumentException("密码长度不能少于6位"))
        }
    }

    override suspend fun getHealthProfile(): UserProfile {
        delay(250)
        return profile
    }

    override suspend fun getHistoryMessages(): List<Message> {
        delay(200)
        return listOf(
            Message(
                id = "m1",
                content = "最近右侧肋骨疼",
                role = "user",
                timestamp = System.currentTimeMillis() - 60_000,
            ),
            Message(
                id = "m2",
                content = "根据你的历史资料，建议先观察疼痛持续时间，如伴随发热或剧烈疼痛应及时线下就医。",
                role = "assistant",
                timestamp = System.currentTimeMillis() - 50_000,
            ),
        )
    }

    override suspend fun sendAnxietyMessage(content: String, currentMessages: List<Message>): Result<Message> {
        delay(1000)
        val reply = Message(
            id = "anxiety-${currentMessages.size + 1}",
            content = "我听到了你的担心。结合你的健康档案，很多身体不适都有常见且良性的解释；如果这种担心经常出现并影响生活，建议和心理科医生聊聊，也欢迎上传检查报告让我结合真实结果和你分析。",
            role = "assistant",
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENT,
            warnings = listOf("健康辅助不能替代医生诊断；如有紧急症状请立即就医。"),
        )
        return Result.success(reply)
    }

    override suspend fun sendMessage(content: String, currentMessages: List<Message>): Result<Message> {
        delay(1000)
        val response = when {
            content.contains("胃") -> "根据你的健康档案，胃部不适可能和饮食不规律有关。建议清淡饮食，若持续疼痛请及时就医。"
            content.contains("肋骨") -> "右侧肋骨疼痛可能与肌肉拉伤、胆囊或肝区不适有关，若疼痛明显加重建议尽快做线下检查。"
            else -> "我已经记录你的情况。建议结合既往病史继续观察，如症状持续或加重，请及时到医院就诊。"
        }
        return Result.success(
            Message(
                id = "assistant-${currentMessages.size + 1}",
                content = response,
                role = "assistant",
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.SENT,
            ),
        )
    }

    override suspend fun uploadReport(uri: Uri?): Result<Long> {
        delay(1200)
        val fileName = uri?.lastPathSegment?.substringAfterLast('/') ?: "检查报告"
        latestReport = latestReport.copy(
            reportId = 10002,
            title = fileName,
            date = "2026-07-31",
            summary = "AI 已完成对上传资料的初步解析。",
            findings = listOf("轻度脂肪肝", "建议结合肝功能结果复查"),
            suggestions = listOf("避免油腻饮食", "控制体重", "1-3 个月内复查"),
        )
        return Result.success(latestReport.reportId)
    }

    override suspend fun getReport(reportId: Long): MedicalReport {
        delay(150)
        return latestReport.copy(reportId = reportId)
    }

    override suspend fun getReportStatus(reportId: Long): MedicalReport {
        delay(120)
        return latestReport.copy(
            reportId = reportId,
            phase = "COMPLETED",
            canRetry = false,
            requiresReview = false,
            failureCategory = null,
        )
    }

    override suspend fun listReports(page: Int, size: Int): Result<ReportPage> {
        delay(300)
        return Result.success(
            ReportPage(
                items = listOf(
                    ReportListItem(
                        reportId = 10001,
                        title = "腹部超声",
                        reportDate = "2026-06-17",
                        reportType = "影像报告",
                        status = "COMPLETED",
                        summary = "AI 初步判断存在轻度脂肪肝倾向。",
                    ),
                    ReportListItem(
                        reportId = 10002,
                        title = "血液检查",
                        reportDate = "2026-07-01",
                        reportType = "血液检查",
                        status = "COMPLETED",
                        summary = "各项指标基本正常。",
                    ),
                ),
                totalPages = 1,
            ),
        )
    }

    override suspend fun downloadReportFile(reportId: Long): Result<ResponseBody> {
        delay(500)
        return Result.failure(UnsupportedOperationException("离线模式下不支持文件下载"))
    }
}
