package com.ipuuuuu.doctorwang.mock.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.ipuuuuu.doctorwang.mock.model.ChatRule
import com.ipuuuuu.doctorwang.mock.model.ChatResponse
import com.ipuuuuu.doctorwang.mock.model.MedicalReportResponse
import com.ipuuuuu.doctorwang.mock.model.UploadResponse
import com.ipuuuuu.doctorwang.mock.model.UserProfileResponse
import jakarta.annotation.PostConstruct
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

@Service
class MockDataService(
    private val objectMapper: ObjectMapper,
) {
    private lateinit var profile: UserProfileResponse
    private lateinit var latestReport: MedicalReportResponse
    private var chatRules: List<ChatRule> = emptyList()

    @PostConstruct
    fun load() {
        profile = readJson("mock-data/user.json")
        latestReport = readJson("mock-data/report.json")
        chatRules = objectMapper.readValue(
            ClassPathResource("mock-data/chat-rules.json").inputStream,
            object : TypeReference<List<ChatRule>>() {},
        )
    }

    fun getProfile(): UserProfileResponse = profile

    fun getHealthProfile(): UserProfileResponse = profile

    fun answerQuestion(message: String): ChatResponse {
        val rule = chatRules.firstOrNull { message.contains(it.keyword, ignoreCase = true) }
        return ChatResponse(
            answer = rule?.answer
                ?: "我已经记录你的情况。建议结合既往病史继续观察，如症状持续或加重，请及时线下就医。",
        )
    }

    fun uploadReport(fileName: String): UploadResponse {
        latestReport = latestReport.copy(
            reportId = 10002,
            title = fileName.ifBlank { "检查报告" },
            date = "2026-07-31",
            summary = "AI 已完成对上传资料的初步解析。",
            findings = listOf("轻度脂肪肝", "建议结合肝功能结果复查"),
            suggestions = listOf("避免油腻饮食", "控制体重", "1-3 个月内复查"),
        )
        return UploadResponse(reportId = latestReport.reportId, status = "success")
    }

    fun getReport(reportId: Long): MedicalReportResponse {
        return latestReport.copy(reportId = reportId)
    }

    private inline fun <reified T> readJson(path: String): T {
        return objectMapper.readValue(ClassPathResource(path).inputStream, T::class.java)
    }
}
