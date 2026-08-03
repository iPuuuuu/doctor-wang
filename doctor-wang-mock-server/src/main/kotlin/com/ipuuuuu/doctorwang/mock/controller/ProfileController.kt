package com.ipuuuuu.doctorwang.mock.controller

import com.ipuuuuu.doctorwang.mock.model.UserProfileResponse
import com.ipuuuuu.doctorwang.mock.model.UpdateProfileRequest
import com.ipuuuuu.doctorwang.mock.service.AccountService
import com.ipuuuuu.doctorwang.mock.service.ProfileService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class ProfileController(
    private val accountService: AccountService,
    private val profileService: ProfileService,
) {
    @GetMapping("/profile")
    fun getProfile(@RequestHeader("Authorization", required = false) authorization: String?): UserProfileResponse =
        profileService.profile(accountService.requireUserId(authorization))

    @GetMapping("/health/profile")
    fun getHealthProfile(@RequestHeader("Authorization", required = false) authorization: String?): UserProfileResponse =
        profileService.profile(accountService.requireUserId(authorization))

    @PutMapping("/profile")
    fun updateProfile(@RequestHeader("Authorization", required = false) authorization: String?, @RequestBody request: UpdateProfileRequest): UserProfileResponse =
        profileService.update(accountService.requireUserId(authorization), request)
}
