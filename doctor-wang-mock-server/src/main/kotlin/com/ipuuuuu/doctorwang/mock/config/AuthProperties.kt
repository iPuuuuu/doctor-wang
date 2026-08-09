package com.ipuuuuu.doctorwang.mock.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.auth")
data class AuthProperties(
    val tokenPrefix: String = "dw-token",
)
