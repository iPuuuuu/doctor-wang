package com.ipuuuuu.doctorwang.mock.controller

import com.ipuuuuu.doctorwang.mock.model.LoginRequest
import com.ipuuuuu.doctorwang.mock.model.LoginResponse
import com.ipuuuuu.doctorwang.mock.model.PasswordChangeRequest
import com.ipuuuuu.doctorwang.mock.model.RegisterRequest
import com.ipuuuuu.doctorwang.mock.config.ApiException
import com.ipuuuuu.doctorwang.mock.service.BackendService
import com.ipuuuuu.doctorwang.mock.service.IpThrottleService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class AuthController(
    private val backendService: BackendService,
    private val ipThrottle: IpThrottleService,
) {
    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): LoginResponse {
        require(request.account.isNotBlank() && request.password.isNotBlank()) { "账号和密码不能为空" }
        return backendService.login(request.account, request.password)
    }

    @PostMapping("/register")
    fun register(
        @RequestBody request: RegisterRequest,
        @RequestHeader("X-Real-IP", required = false) clientIp: String?,
    ): LoginResponse {
        require(request.account.isNotBlank()) { "账号不能为空" }
        require(request.password.length >= 6) { "密码长度不能少于6位" }
        require(request.name.isNotBlank()) { "姓名不能为空" }
        val ip = clientIp?.substringBefore(':') ?: "unknown"
        if (!ipThrottle.tryAcquire("register:$ip")) {
            throw ApiException("REGISTER_THROTTLED", "注册过于频繁，请稍后再试")
        }
        return backendService.register(request.account, request.password, request.name, request.age, request.height, request.weight)
    }

    @PostMapping("/password/change")
    fun changePassword(@RequestBody request: PasswordChangeRequest): ResponseEntity<Void> {
        require(request.oldPassword.isNotBlank() && request.newPassword.isNotBlank()) { "密码不能为空" }
        backendService.changePassword(request.oldPassword, request.newPassword)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/logout")
    fun logout(): ResponseEntity<Void> {
        backendService.logout()
        return ResponseEntity.noContent().build()
    }
}
