package com.ipuuuuu.doctorwang.mock.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.auth")
data class AuthProperties(
    val jwtSecret: String = "",
    val jwtIssuer: String = "doctor-wang-backend",
    val jwtAudience: String = "doctor-wang-android",
    val jwtTtlSeconds: Long = 3600,
) {
    init {
        require(jwtSecret.length >= MIN_SECRET_LENGTH) {
            "app.auth.jwt-secret must contain at least $MIN_SECRET_LENGTH characters"
        }
        validateClaim("app.auth.jwt-issuer", jwtIssuer)
        validateClaim("app.auth.jwt-audience", jwtAudience)
        require(jwtTtlSeconds in MIN_TTL_SECONDS..MAX_TTL_SECONDS) {
            "app.auth.jwt-ttl-seconds must be between $MIN_TTL_SECONDS and $MAX_TTL_SECONDS"
        }
    }

    private fun validateClaim(name: String, value: String) {
        require(value.isNotBlank() && value.length <= MAX_CLAIM_LENGTH && value.none { it.isWhitespace() || it.isISOControl() }) {
            "$name must be non-blank, at most $MAX_CLAIM_LENGTH characters, and contain no whitespace/control characters"
        }
    }

    companion object {
        private const val MIN_SECRET_LENGTH = 32
        private const val MAX_CLAIM_LENGTH = 256
        private const val MIN_TTL_SECONDS = 1L
        private const val MAX_TTL_SECONDS = 86_400L
    }
}
