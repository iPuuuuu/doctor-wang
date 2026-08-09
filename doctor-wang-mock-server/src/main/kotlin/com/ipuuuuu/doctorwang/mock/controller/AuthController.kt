package com.ipuuuuu.doctorwang.mock.controller

import com.ipuuuuu.doctorwang.mock.model.LoginRequest
import com.ipuuuuu.doctorwang.mock.model.LoginResponse
import com.ipuuuuu.doctorwang.mock.service.BackendService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class AuthController(
    private val backendService: BackendService,
) {
    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): LoginResponse {
        require(request.account.isNotBlank() && request.password.isNotBlank()) { "账号和密码不能为空" }
        return backendService.login(request.account, request.password)
    }
}
