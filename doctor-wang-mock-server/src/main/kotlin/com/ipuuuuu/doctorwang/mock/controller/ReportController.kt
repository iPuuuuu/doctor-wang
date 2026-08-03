package com.ipuuuuu.doctorwang.mock.controller

import com.ipuuuuu.doctorwang.mock.model.MedicalReportResponse
import com.ipuuuuu.doctorwang.mock.model.ReportListItemResponse
import com.ipuuuuu.doctorwang.mock.model.UploadResponse
import com.ipuuuuu.doctorwang.mock.service.AccountService
import com.ipuuuuu.doctorwang.mock.service.ReportStorageService
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/report")
class ReportController(
    private val accountService: AccountService,
    private val reports: ReportStorageService,
) {
    @PostMapping("/upload", consumes = ["multipart/form-data"])
    fun upload(
        @RequestHeader("Authorization", required = false) auth: String?,
        @RequestParam("file") file: MultipartFile,
    ): UploadResponse = reports.upload(accountService.requireUserId(auth), file)

    @GetMapping
    fun listReports(
        @RequestHeader("Authorization", required = false) auth: String?,
    ): List<ReportListItemResponse> = reports.listReports(accountService.requireUserId(auth))

    @GetMapping("/{reportId}")
    fun getReport(
        @RequestHeader("Authorization", required = false) auth: String?,
        @PathVariable reportId: Long,
    ): MedicalReportResponse = reports.report(accountService.requireUserId(auth), reportId)

    @DeleteMapping("/{reportId}")
    fun deleteReport(
        @RequestHeader("Authorization", required = false) auth: String?,
        @PathVariable reportId: Long,
    ) {
        reports.delete(accountService.requireUserId(auth), reportId)
    }

    @GetMapping("/{reportId}/download")
    fun downloadReport(
        @RequestHeader("Authorization", required = false) auth: String?,
        @PathVariable reportId: Long,
    ): ResponseEntity<ByteArrayResource> {
        val file = reports.download(accountService.requireUserId(auth), reportId)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.mediaType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${file.fileName}\"")
            .body(ByteArrayResource(file.bytes))
    }
}
