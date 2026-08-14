package com.ipuuuuu.doctorwang.mock.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.ipuuuuu.doctorwang.mock.config.ApiException
import com.ipuuuuu.doctorwang.mock.config.AuthProperties
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class VerifiedJwt(
    val subject: String,
    val userDatabaseId: Long,
    val sessionId: String,
    val expiresAtEpochSeconds: Long,
)

@Component
class JwtTokenService(
    private val properties: AuthProperties,
    private val objectMapper: ObjectMapper,
) {
    fun issue(subject: String, userDatabaseId: Long): Pair<String, VerifiedJwt> {
        val now = Instant.now().epochSecond
        val expiresAt = now + properties.jwtTtlSeconds
        val verified = VerifiedJwt(subject, userDatabaseId, UUID.randomUUID().toString(), expiresAt)
        val header = encode(mapOf("alg" to "HS256", "typ" to "JWT"))
        val payload = encode(
            mapOf(
                "sub" to verified.subject,
                "uid" to verified.userDatabaseId,
                "jti" to verified.sessionId,
                "iss" to properties.jwtIssuer,
                "aud" to properties.jwtAudience,
                "iat" to now,
                "exp" to verified.expiresAtEpochSeconds,
            ),
        )
        val signingInput = "$header.$payload"
        return "$signingInput.${signature(signingInput)}" to verified
    }

    fun verify(token: String): VerifiedJwt {
        val parts = token.split('.')
        if (parts.size != 3) throw ApiException("AUTHENTICATION_REQUIRED", "登录凭据无效或已过期")
        val header: Map<String, Any?> = try {
            objectMapper.readValue(Base64.getUrlDecoder().decode(parts[0]), Map::class.java) as Map<String, Any?>
        } catch (_: Exception) {
            throw ApiException("AUTHENTICATION_REQUIRED", "登录凭据无效或已过期")
        }
        if (header["alg"] != "HS256" || header["typ"] != "JWT") {
            throw ApiException("AUTHENTICATION_REQUIRED", "登录凭据无效或已过期")
        }
        val signingInput = "${parts[0]}.${parts[1]}"
        val expected = signature(signingInput)
        if (!MessageDigest.isEqual(expected.toByteArray(StandardCharsets.US_ASCII), parts[2].toByteArray(StandardCharsets.US_ASCII))) {
            throw ApiException("AUTHENTICATION_REQUIRED", "登录凭据无效或已过期")
        }
        val claims: Map<String, Any?> = try {
            objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Map::class.java) as Map<String, Any?>
        } catch (_: Exception) {
            throw ApiException("AUTHENTICATION_REQUIRED", "登录凭据无效或已过期")
        }
        val subject = claims["sub"] as? String
        val sessionId = claims["jti"] as? String
        val issuer = claims["iss"] as? String
        val audience = claims["aud"] as? String
        val userId = (claims["uid"] as? Number)?.toLong()
        val expiresAt = (claims["exp"] as? Number)?.toLong()
        if (subject.isNullOrBlank() || sessionId.isNullOrBlank() || userId == null || expiresAt == null ||
            issuer != properties.jwtIssuer || audience != properties.jwtAudience || expiresAt <= Instant.now().epochSecond
        ) throw ApiException("AUTHENTICATION_REQUIRED", "登录凭据无效或已过期")
        return VerifiedJwt(subject, userId, sessionId, expiresAt)
    }

    fun tokenHash(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun encode(value: Map<String, Any>): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(objectMapper.writeValueAsBytes(value))

    private fun signature(signingInput: String): String {
        val secret = properties.jwtSecret
        if (secret.length < 32) throw ApiException("AUTH_CONFIGURATION_INVALID", "认证服务配置不可用")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(signingInput.toByteArray(StandardCharsets.UTF_8)))
    }

}
