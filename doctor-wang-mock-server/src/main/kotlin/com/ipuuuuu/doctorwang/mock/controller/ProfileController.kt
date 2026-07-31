package com.ipuuuuu.doctorwang.mock.controller

import com.ipuuuuu.doctorwang.mock.model.UserProfileResponse
import com.ipuuuuu.doctorwang.mock.service.MockDataService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class ProfileController(
    private val mockDataService: MockDataService,
) {
    @GetMapping("/profile")
    fun getProfile(): UserProfileResponse = mockDataService.getProfile()

    @GetMapping("/health/profile")
    fun getHealthProfile(): UserProfileResponse = mockDataService.getHealthProfile()
}
