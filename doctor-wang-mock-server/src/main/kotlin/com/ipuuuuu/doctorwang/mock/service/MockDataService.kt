package com.ipuuuuu.doctorwang.mock.service

import com.fasterxml.jackson.databind.ObjectMapper
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

    @PostConstruct
    fun load() {
        profile = readJson("mock-data/user.json")
        latestReport = readJson("mock-data/report.json")
    }

    fun getProfile(): UserProfileResponse = profile

    fun getHealthProfile(): UserProfileResponse = profile

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
