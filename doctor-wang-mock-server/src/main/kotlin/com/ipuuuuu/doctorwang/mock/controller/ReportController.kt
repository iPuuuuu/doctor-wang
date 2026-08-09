package com.ipuuuuu.doctorwang.mock.controller

import com.ipuuuuu.doctorwang.mock.model.MedicalReportResponse
import com.ipuuuuu.doctorwang.mock.model.UploadRequest
import com.ipuuuuu.doctorwang.mock.model.UploadResponse
import com.ipuuuuu.doctorwang.mock.service.BackendService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/report")
class ReportController(
    private val backendService: BackendService,
) {
    @PostMapping("/upload")
    fun upload(@RequestBody request: UploadRequest): UploadResponse {
        return backendService.uploadReport(request.fileName)
    }

    @GetMapping("/{reportId}")
    fun getReport(@PathVariable reportId: Long): MedicalReportResponse {
        return backendService.getReport(reportId)
    }
}
