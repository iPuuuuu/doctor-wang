package com.ipuuuuu.doctorwang.mock.controller

import com.ipuuuuu.doctorwang.mock.model.LoginRequest
import com.ipuuuuu.doctorwang.mock.model.LoginResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class AuthController {
    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): LoginResponse {
        require(request.account.isNotBlank() && request.password.isNotBlank()) { "账号和密码不能为空" }
        return LoginResponse(token = "abc123", userId = "u1001")
    }
}
