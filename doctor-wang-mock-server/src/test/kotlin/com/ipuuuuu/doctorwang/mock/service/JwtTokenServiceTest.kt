package com.ipuuuuu.doctorwang.mock.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.ipuuuuu.doctorwang.mock.config.ApiException
import com.ipuuuuu.doctorwang.mock.config.AuthProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class JwtTokenServiceTest {
    private val service = JwtTokenService(
        AuthProperties(jwtSecret = "01234567890123456789012345678901"),
        ObjectMapper(),
    )

    @Test
    fun `issued token contains verifiable identity and session`() {
        val (token, issued) = service.issue("u1001", 1)
        val verified = service.verify(token)
        assertEquals("u1001", verified.subject)
        assertEquals(1, verified.userDatabaseId)
        assertEquals(issued.sessionId, verified.sessionId)
    }

    @Test
    fun `tampered token is rejected`() {
        val (token, _) = service.issue("u1001", 1)
        val tampered = token.dropLast(1) + if (token.last() == 'a') 'b' else 'a'
        assertThrows(ApiException::class.java) { service.verify(tampered) }
    }

    @Test
    fun `invalid ttl is rejected at service startup`() {
        assertThrows(IllegalArgumentException::class.java) {
            JwtTokenService(AuthProperties(jwtSecret = SECRET, jwtTtlSeconds = 0), ObjectMapper())
        }
        assertThrows(IllegalArgumentException::class.java) {
            JwtTokenService(AuthProperties(jwtSecret = SECRET, jwtTtlSeconds = 86_401), ObjectMapper())
        }
    }

    @Test
    fun `invalid jwt security configuration is rejected at startup`() {
        assertThrows(IllegalArgumentException::class.java) {
            AuthProperties(jwtSecret = "too-short-secret")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AuthProperties(jwtSecret = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AuthProperties(jwtSecret = SECRET, jwtIssuer = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AuthProperties(jwtSecret = SECRET, jwtIssuer = " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AuthProperties(jwtSecret = SECRET, jwtAudience = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AuthProperties(jwtSecret = SECRET, jwtAudience = "bad audience")
        }
    }

    @Test
    fun `ttl endpoints are accepted`() {
        JwtTokenService(AuthProperties(jwtSecret = SECRET, jwtTtlSeconds = 1), ObjectMapper())
        JwtTokenService(AuthProperties(jwtSecret = SECRET, jwtTtlSeconds = 86_400), ObjectMapper())
    }

    @Test
    fun `unexpected jose header is rejected`() {
        val (token, _) = service.issue("u1001", 1)
        val parts = token.split('.')
        listOf(
            "{\"alg\":\"none\",\"typ\":\"JWT\"}",
            "{\"alg\":\"HS256\",\"typ\":\"JWS\"}",
        ).forEach { headerJson ->
            val badHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.toByteArray())
            val malformed = "$badHeader.${parts[1]}.${parts[2]}"
            assertThrows(ApiException::class.java) { service.verify(malformed) }
        }
    }

    @Test
    fun `expired token is rejected by verifier`() {
        val claims = validClaims().toMutableMap().apply { this["exp"] = Instant.now().epochSecond - 1 }
        assertThrows(ApiException::class.java) { service.verify(tokenWithClaims(claims)) }
    }

    @Test
    fun `wrong issuer and audience are rejected by verifier`() {
        val wrongIssuer = validClaims().toMutableMap().apply { this["iss"] = "https://other.example" }
        val wrongAudience = validClaims().toMutableMap().apply { this["aud"] = "other-client" }
        assertThrows(ApiException::class.java) { service.verify(tokenWithClaims(wrongIssuer)) }
        assertThrows(ApiException::class.java) { service.verify(tokenWithClaims(wrongAudience)) }
    }

    @Test
    fun `required identity claims are rejected when missing`() {
        listOf("sub", "uid", "jti").forEach { missingClaim ->
            val claims = validClaims().toMutableMap().apply { remove(missingClaim) }
            assertThrows(ApiException::class.java) { service.verify(tokenWithClaims(claims)) }
        }
    }

    @Test
    fun `malformed payload is rejected by verifier`() {
        val header = encodeJson(mapOf("alg" to "HS256", "typ" to "JWT"))
        val signingInput = "$header.*"
        assertThrows(ApiException::class.java) { service.verify("$signingInput.${sign(signingInput)}") }
    }

    @Test
    fun `malformed jose header is rejected by verifier`() {
        val payload = encodeJson(validClaims())
        val signingInput = "not-a-valid-base64-header.$payload"
        assertThrows(ApiException::class.java) { service.verify("$signingInput.${sign(signingInput)}") }
    }

    private fun validClaims(): Map<String, Any> = mapOf(
        "sub" to "u1001", "uid" to 1L, "jti" to "session-1",
        "iss" to "doctor-wang-backend", "aud" to "doctor-wang-android",
        "iat" to Instant.now().epochSecond, "exp" to Instant.now().epochSecond + 300,
    )

    private fun tokenWithClaims(claims: Map<String, Any?>): String {
        val header = encodeJson(mapOf("alg" to "HS256", "typ" to "JWT"))
        val payload = encodeJson(claims)
        val signingInput = "$header.$payload"
        return "$signingInput.${sign(signingInput)}"
    }

    private fun encodeJson(value: Any): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ObjectMapper().writeValueAsBytes(value))

    private fun sign(signingInput: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(SECRET.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(signingInput.toByteArray(StandardCharsets.UTF_8)))
    }

    companion object {
        private const val SECRET = "01234567890123456789012345678901"
    }
}
