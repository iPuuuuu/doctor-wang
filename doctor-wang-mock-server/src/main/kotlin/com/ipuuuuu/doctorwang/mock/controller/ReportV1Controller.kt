package com.ipuuuuu.doctorwang.mock.controller

import com.ipuuuuu.doctorwang.mock.model.ReportDetailV1Response
import com.ipuuuuu.doctorwang.mock.model.ReportStatusResponse
import com.ipuuuuu.doctorwang.mock.model.ReportUploadResponse
import com.ipuuuuu.doctorwang.mock.service.ReportProcessingService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/reports")
class ReportV1Controller(private val service: ReportProcessingService) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @RequestPart("file") file: MultipartFile,
        @RequestParam(required = false) reportDate: String?,
        @RequestParam(required = false) reportType: String?,
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?,
    ): ResponseEntity<ReportUploadResponse> = ResponseEntity.status(HttpStatus.ACCEPTED).body(service.upload(file, reportDate, reportType, idempotencyKey))

    @GetMapping("/{reportId}/status")
    fun status(@PathVariable reportId: Long): ReportStatusResponse = service.status(reportId)

    @GetMapping("/{reportId}")
    fun detail(@PathVariable reportId: Long): ReportDetailV1Response = service.detail(reportId)

    @PostMapping("/{reportId}/retry")
    fun retry(@PathVariable reportId: Long): ReportStatusResponse = service.retry(reportId)

    @DeleteMapping("/{reportId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable reportId: Long) = service.delete(reportId)
}
