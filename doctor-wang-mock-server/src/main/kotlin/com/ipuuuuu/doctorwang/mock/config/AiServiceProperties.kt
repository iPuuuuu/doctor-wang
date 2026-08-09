package com.ipuuuuu.doctorwang.mock.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.ai-service")
data class AiServiceProperties(
    var baseUrl: String = "http://localhost:8000",
    var internalToken: String = "",
    var connectTimeoutMs: Long = 2000,
    var readTimeoutMs: Long = 30000,
    var sourceVersion: String = "upload-v1",
    var enabled: Boolean = false,
)
