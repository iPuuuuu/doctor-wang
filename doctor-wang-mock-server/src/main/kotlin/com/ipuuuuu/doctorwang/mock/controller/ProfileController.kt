package com.ipuuuuu.doctorwang.mock.controller

import com.ipuuuuu.doctorwang.mock.model.ProfileUpdateRequest
import com.ipuuuuu.doctorwang.mock.model.UserProfileResponse
import com.ipuuuuu.doctorwang.mock.service.BackendService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class ProfileController(
    private val backendService: BackendService,
) {
    @GetMapping("/profile")
    fun getProfile(): UserProfileResponse = backendService.getProfile()

    @PutMapping("/profile")
    fun updateProfile(@RequestBody request: ProfileUpdateRequest): UserProfileResponse =
        backendService.updateProfile(request.name, request.age, request.height, request.weight)

    @GetMapping("/health/profile")
    fun getHealthProfile(): UserProfileResponse = backendService.getHealthProfile()
}
