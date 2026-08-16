package com.ipuuuuu.doctorwang.mock.config

import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import com.ipuuuuu.doctorwang.mock.model.ApiError

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(ApiException::class)
    fun handleApi(error: ApiException): ResponseEntity<ApiError> = ResponseEntity.status(statusFor(error.code)).body(ApiError(error.code, error.message, TraceIdContext.get()))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(error: IllegalArgumentException): ResponseEntity<ApiError> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError("INVALID_REQUEST", error.message ?: "请求参数错误", TraceIdContext.get()))
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(error: NoSuchElementException): ResponseEntity<ApiError> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError("REPORT_NOT_FOUND", error.message ?: "资源不存在", TraceIdContext.get()))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(error: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val message = error.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "请求参数校验失败"
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError("INVALID_REQUEST", message, TraceIdContext.get()))
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(error: ConstraintViolationException): ResponseEntity<ApiError> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError("INVALID_REQUEST", error.constraintViolations.firstOrNull()?.message ?: "请求参数校验失败", TraceIdContext.get()))
    }

    @ExceptionHandler(SecurityException::class)
    fun handleSecurity(error: SecurityException): ResponseEntity<ApiError> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiError("AUTHENTICATION_REQUIRED", error.message ?: "未登录或登录已失效", TraceIdContext.get()))
    }

    @ExceptionHandler(MissingServletRequestPartException::class)
    fun handleMissingPart(error: MissingServletRequestPartException): ResponseEntity<ApiError> =
        ResponseEntity.badRequest().body(ApiError("FILE_REQUIRED", "必须上传文件", TraceIdContext.get()))

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameter(error: MissingServletRequestParameterException): ResponseEntity<ApiError> =
        ResponseEntity.badRequest().body(ApiError("INVALID_REQUEST", "缺少请求参数", TraceIdContext.get()))

    private fun statusFor(code: String): HttpStatus = when (code) {
        "UNSUPPORTED_MEDIA_TYPE" -> HttpStatus.UNSUPPORTED_MEDIA_TYPE
        "MALWARE_DETECTED" -> HttpStatus.UNPROCESSABLE_ENTITY
        "FILE_TOO_LARGE" -> HttpStatus.PAYLOAD_TOO_LARGE
        "REPORT_NOT_FOUND", "PROCESSING_JOB_NOT_FOUND" -> HttpStatus.NOT_FOUND
        "ACCOUNT_EXISTS", "REPORT_PROCESSING_CONFLICT", "DUPLICATE_UPLOAD", "IDEMPOTENCY_KEY_REUSED", "REPORT_NOT_RETRYABLE", "RETRY_LIMIT_EXCEEDED" -> HttpStatus.CONFLICT
        "STORAGE_FAILED", "AI_SERVICE_UNAVAILABLE", "AI_SERVICE_NOT_CONFIGURED", "AUTH_CONFIGURATION_INVALID" -> HttpStatus.SERVICE_UNAVAILABLE
        "AUTHENTICATION_FAILED", "AUTHENTICATION_REQUIRED" -> HttpStatus.UNAUTHORIZED
        "LOGIN_LOCKED", "REGISTER_THROTTLED" -> HttpStatus.TOO_MANY_REQUESTS
        "PROCESSING_TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT
        "AI_RESULT_INVALID" -> HttpStatus.BAD_GATEWAY
        else -> HttpStatus.BAD_REQUEST
    }
}
