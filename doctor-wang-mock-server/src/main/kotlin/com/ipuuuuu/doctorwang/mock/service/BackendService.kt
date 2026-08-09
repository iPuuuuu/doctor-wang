package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.AuthContext
import com.ipuuuuu.doctorwang.mock.config.AuthProperties
import com.ipuuuuu.doctorwang.mock.model.ChatMessageResponse
import com.ipuuuuu.doctorwang.mock.model.ChatResponse
import com.ipuuuuu.doctorwang.mock.model.HealthRecordResponse
import com.ipuuuuu.doctorwang.mock.model.LoginResponse
import com.ipuuuuu.doctorwang.mock.model.MedicalReportResponse
import com.ipuuuuu.doctorwang.mock.model.UploadResponse
import com.ipuuuuu.doctorwang.mock.model.UserProfileResponse
import com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.ChatMessageEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.MedicalReportEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.MedicalReportFindingEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.MedicalReportSuggestionEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.UserSessionEntity
import com.ipuuuuu.doctorwang.mock.persistence.repository.AppUserRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.ChatMessageRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.HealthProfileDiseaseRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.HealthProfileRecordRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportFindingRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.MedicalReportSuggestionRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.UserSessionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
class BackendService(
    private val authProperties: AuthProperties,
    private val appUserRepository: AppUserRepository,
    private val userSessionRepository: UserSessionRepository,
    private val healthProfileDiseaseRepository: HealthProfileDiseaseRepository,
    private val healthProfileRecordRepository: HealthProfileRecordRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val medicalReportRepository: MedicalReportRepository,
    private val medicalReportFindingRepository: MedicalReportFindingRepository,
    private val medicalReportSuggestionRepository: MedicalReportSuggestionRepository,
) {
    @Transactional
    fun login(account: String, password: String): LoginResponse {
        val user = appUserRepository.findByAccount(account.trim())
            ?: throw IllegalArgumentException("账号或密码错误")
        if (user.password != password) {
            throw IllegalArgumentException("账号或密码错误")
        }

        val token = buildToken(user)
        userSessionRepository.save(
            UserSessionEntity(
                token = token,
                user = user,
                expiresAt = OffsetDateTime.now().plusDays(30),
            ),
        )
        return LoginResponse(token = token, userId = user.userId)
    }

    @Transactional(readOnly = true)
    fun getProfile(): UserProfileResponse = requireCurrentUser().toUserProfileResponse()

    @Transactional(readOnly = true)
    fun getHealthProfile(): UserProfileResponse = requireCurrentUser().toUserProfileResponse()

    @Transactional(readOnly = true)
    fun getChatHistory(): List<ChatMessageResponse> {
        val user = requireCurrentUser()
        return chatMessageRepository.findAllByUserOrderByCreatedAtAscIdAsc(user).map {
            ChatMessageResponse(
                id = it.id.toString(),
                content = it.content,
                role = it.role,
                timestamp = it.createdAt.toInstant().toEpochMilli(),
            )
        }
    }

    @Transactional
    fun answerQuestion(message: String): ChatResponse {
        val user = requireCurrentUser()
        chatMessageRepository.save(
            ChatMessageEntity(
                user = user,
                role = "user",
                content = message,
            ),
        )

        val response = generateAssistantReply(message, user)
        chatMessageRepository.save(
            ChatMessageEntity(
                user = user,
                role = "assistant",
                content = response.answer,
            ),
        )
        return response
    }

    @Transactional
    fun uploadReport(fileName: String): UploadResponse {
        val user = requireCurrentUser()
        val report = medicalReportRepository.save(
            MedicalReportEntity(
                user = user,
                title = fileName.ifBlank { "检查报告" },
                reportDate = "2026-07-31",
                reportType = inferReportType(fileName),
                summary = "AI 已完成对上传资料的初步解析。",
                status = "completed",
            ),
        )

        medicalReportFindingRepository.saveAll(
            listOf(
                MedicalReportFindingEntity(report = report, findingText = "轻度脂肪肝"),
                MedicalReportFindingEntity(report = report, findingText = "建议结合肝功能结果复查"),
            ),
        )
        medicalReportSuggestionRepository.saveAll(
            listOf(
                MedicalReportSuggestionEntity(report = report, suggestionText = "避免油腻饮食"),
                MedicalReportSuggestionEntity(report = report, suggestionText = "控制体重"),
                MedicalReportSuggestionEntity(report = report, suggestionText = "1-3 个月内复查"),
            ),
        )
        return UploadResponse(reportId = report.id, status = "success")
    }

    @Transactional(readOnly = true)
    fun getReport(reportId: Long): MedicalReportResponse {
        val user = requireCurrentUser()
        val report = medicalReportRepository.findById(reportId)
            .orElseThrow { NoSuchElementException("报告不存在") }
        if (report.user.id != user.id) {
            throw NoSuchElementException("报告不存在")
        }
        return report.toMedicalReportResponse()
    }

    private fun requireCurrentUser(): AppUserEntity {
        return AuthContext.get()?.user ?: throw SecurityException("未登录或登录已失效")
    }

    private fun buildToken(user: AppUserEntity): String {
        return "${authProperties.tokenPrefix}-${user.userId}-${UUID.randomUUID()}"
    }

    private fun generateAssistantReply(message: String, user: AppUserEntity): ChatResponse {
        val normalized = message.lowercase()
        val answer = when {
            normalized.contains("胃") -> "根据你的健康档案，你之前有胃炎记录。建议近期保持清淡饮食，避免辛辣刺激；如果疼痛持续或加重，请及时线下就医。"
            normalized.contains("肝") || normalized.contains("脂肪肝") -> "结合你既往的轻度脂肪肝记录，建议近期控制油脂摄入、规律运动，并复查肝功能。"
            normalized.contains("头晕") || normalized.contains("乏力") -> "头晕或乏力可能与睡眠、饮食、贫血或近期压力有关。建议先记录持续时间与伴随症状，必要时完善血常规等检查。"
            else -> "我已经记录你的情况。结合${user.name}当前档案信息，建议持续观察症状变化；如症状持续或明显加重，请及时线下就医。"
        }
        return ChatResponse(answer = answer)
    }

    private fun inferReportType(fileName: String): String {
        val normalized = fileName.lowercase()
        return when {
            normalized.contains("ct") -> "CT 报告"
            normalized.contains("mri") -> "MRI 报告"
            normalized.contains("血") -> "血液检查"
            else -> "检查报告"
        }
    }

    private fun AppUserEntity.toUserProfileResponse(): UserProfileResponse {
        val diseases = healthProfileDiseaseRepository.findAllByUserOrderByIdAsc(this).map { it.diseaseName }
        val records = healthProfileRecordRepository.findAllByUserOrderByIdAsc(this).map {
            HealthRecordResponse(date = it.recordDate, type = it.recordType)
        }
        return UserProfileResponse(
            userId = userId,
            name = name,
            age = age,
            height = height,
            weight = weight,
            historyDiseases = diseases,
            records = records,
        )
    }

    private fun MedicalReportEntity.toMedicalReportResponse(): MedicalReportResponse {
        val findings = medicalReportFindingRepository.findAllByReportOrderByIdAsc(this).map { it.findingText }
        val suggestions = medicalReportSuggestionRepository.findAllByReportOrderByIdAsc(this).map { it.suggestionText }
        return MedicalReportResponse(
            reportId = id,
            title = title,
            date = reportDate,
            type = reportType,
            summary = summary,
            findings = findings,
            suggestions = suggestions,
        )
    }
}
