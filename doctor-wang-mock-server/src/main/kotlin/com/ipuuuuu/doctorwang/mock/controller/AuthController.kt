package com.ipuuuuu.doctorwang.mock.controller

import com.ipuuuuu.doctorwang.mock.model.LoginRequest
import com.ipuuuuu.doctorwang.mock.model.LoginResponse
import com.ipuuuuu.doctorwang.mock.model.RegisterRequest
import com.ipuuuuu.doctorwang.mock.service.AccountService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class AuthController(private val accountService: AccountService) {
    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): LoginResponse =
        accountService.login(request.account, request.password)

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): LoginResponse =
        accountService.register(request.account, request.password)
}
