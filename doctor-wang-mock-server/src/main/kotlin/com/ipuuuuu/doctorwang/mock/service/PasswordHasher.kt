package com.ipuuuuu.doctorwang.mock.service

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

@Component
class PasswordHasher {
    private val encoder = BCryptPasswordEncoder()

    fun hash(rawPassword: String): String = encoder.encode(rawPassword)

    fun matches(rawPassword: String, encodedPassword: String): Boolean = encoder.matches(rawPassword, encodedPassword)
}
