package com.ipuuuuu.doctorwang.mock.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.UserSessionEntity
import com.ipuuuuu.doctorwang.mock.persistence.repository.UserSessionRepository
import com.ipuuuuu.doctorwang.mock.service.JwtTokenService
import com.ipuuuuu.doctorwang.mock.service.VerifiedJwt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.OffsetDateTime

class AuthTokenFilterTest {
    @Test
    fun `invalid authentication configuration fails startup`() {
        assertThrows(IllegalArgumentException::class.java) {
            JwtTokenService(AuthProperties(jwtSecret = "too-short-secret"), ObjectMapper())
        }
    }

    @Test
    fun `request without authorization is rejected with stable error`() {
        val sessions = mock(UserSessionRepository::class.java)
        val jwt = mock(JwtTokenService::class.java)
        val filter = AuthTokenFilter(sessions, jwt, ObjectMapper())
        val request = MockHttpServletRequest("GET", "/api/profile").apply { servletPath = "/api/profile" }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(401, response.status)
        assertEquals(true, response.contentAsString.contains("AUTHENTICATION_REQUIRED"))
        assertNull(chain.request)
        verifyNoInteractions(sessions, jwt)
    }

    @Test
    fun `only the exact method and path combinations bypass authentication`() {
        val sessions = mock(UserSessionRepository::class.java)
        val jwt = mock(JwtTokenService::class.java)
        val filter = AuthTokenFilter(sessions, jwt, ObjectMapper())

        listOf(
            MockHttpServletRequest("POST", "/gateway/api/login").apply {
                contextPath = "/gateway"
                servletPath = "/api/login"
            },
            MockHttpServletRequest("POST", "/api/register").apply { servletPath = "/api/register" },
            MockHttpServletRequest("GET", "/healthz").apply { servletPath = "/healthz" },
            MockHttpServletRequest("GET", "/").apply { servletPath = "/" },
            MockHttpServletRequest("GET", "/actuator/health").apply { servletPath = "/actuator/health" },
        ).forEach { request ->
            val chain = MockFilterChain()

            filter.doFilter(request, MockHttpServletResponse(), chain)

            assertNotNull(chain.request)
        }

        verifyNoInteractions(sessions, jwt)
    }

    @Test
    fun `wrong methods and non-exact public paths require authentication`() {
        val sessions = mock(UserSessionRepository::class.java)
        val jwt = mock(JwtTokenService::class.java)
        val filter = AuthTokenFilter(sessions, jwt, ObjectMapper())

        listOf(
            MockHttpServletRequest("GET", "/api/login").apply { servletPath = "/api/login" },
            MockHttpServletRequest("GET", "/api/register").apply { servletPath = "/api/register" },
            MockHttpServletRequest("POST", "/healthz").apply { servletPath = "/healthz" },
            MockHttpServletRequest("HEAD", "/").apply { servletPath = "/" },
            MockHttpServletRequest("POST", "/api/login/").apply { servletPath = "/api/login/" },
            MockHttpServletRequest("POST", "/api/register/").apply { servletPath = "/api/register/" },
            MockHttpServletRequest("GET", "/healthz/").apply { servletPath = "/healthz/" },
            MockHttpServletRequest("GET", "/healthz/ready").apply { servletPath = "/healthz/ready" },
            MockHttpServletRequest("GET", "/api/login/extra").apply { servletPath = "/api/login/extra" },
        ).forEach { request ->
            val response = MockHttpServletResponse()
            val chain = MockFilterChain()

            filter.doFilter(request, response, chain)

            assertEquals(401, response.status, "${request.method} ${request.requestURI} must not bypass authentication")
            assertNull(chain.request)
        }

        verifyNoInteractions(sessions, jwt)
    }

    @Test
    fun `malformed bearer token is rejected with 401`() {
        val sessions = mock(UserSessionRepository::class.java)
        val jwt = mock(JwtTokenService::class.java)
        val filter = AuthTokenFilter(sessions, jwt, ObjectMapper())
        val request = MockHttpServletRequest("GET", "/api/profile").apply {
            servletPath = "/api/profile"
            addHeader("Authorization", "Bearer definitely-not-a-jwt")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertEquals(401, response.status)
        assertEquals(true, response.contentAsString.contains("AUTHENTICATION_REQUIRED"))
        verify(jwt).verify("definitely-not-a-jwt")
        verifyNoInteractions(sessions)
    }

    @Test
    fun `expired bearer token is rejected with 401`() {
        val sessions = mock(UserSessionRepository::class.java)
        val jwt = mock(JwtTokenService::class.java)
        `when`(jwt.verify("expired-token")).thenThrow(ApiException("AUTHENTICATION_REQUIRED", "登录凭据无效或已过期"))
        val filter = AuthTokenFilter(sessions, jwt, ObjectMapper())
        val request = MockHttpServletRequest("GET", "/api/profile").apply {
            servletPath = "/api/profile"
            addHeader("Authorization", "Bearer expired-token")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertEquals(401, response.status)
        assertEquals(true, response.contentAsString.contains("AUTHENTICATION_REQUIRED"))
        verify(jwt).verify("expired-token")
        verifyNoInteractions(sessions)
    }

    @Test
    fun `configuration failure returns 503 and does not query sessions`() {
        val sessions = mock(UserSessionRepository::class.java)
        val jwt = mock(JwtTokenService::class.java)
        `when`(jwt.verify("bad-config-token")).thenThrow(ApiException("AUTH_CONFIGURATION_INVALID", "认证服务配置不可用"))
        val filter = AuthTokenFilter(sessions, jwt, ObjectMapper())
        val request = MockHttpServletRequest("GET", "/api/profile").apply {
            servletPath = "/api/profile"
            addHeader("Authorization", "Bearer bad-config-token")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertEquals(503, response.status)
        assertEquals(true, response.contentAsString.contains("AUTH_CONFIGURATION_INVALID"))
        verify(jwt).verify("bad-config-token")
        verifyNoInteractions(sessions)
    }

    @Test
    fun `revoked session is rejected with 401`() {
        val sessions = mock(UserSessionRepository::class.java)
        val jwt = mock(JwtTokenService::class.java)
        val verified = VerifiedJwt(subject = USER.userId, userDatabaseId = USER.id, sessionId = "session-1", expiresAtEpochSeconds = 4_102_444_800)
        `when`(jwt.verify(TOKEN)).thenReturn(verified)
        `when`(jwt.tokenHash(TOKEN)).thenReturn(TOKEN_HASH)
        `when`(sessions.findByJti("session-1")).thenReturn(
            UserSessionEntity(
                id = 1,
                jti = "session-1",
                tokenHash = TOKEN_HASH,
                user = USER,
                expiresAt = OffsetDateTime.now().plusMinutes(5),
                revokedAt = OffsetDateTime.now().minusSeconds(30),
            ),
        )
        val filter = AuthTokenFilter(sessions, jwt, ObjectMapper())
        val request = MockHttpServletRequest("GET", "/api/profile").apply {
            servletPath = "/api/profile"
            addHeader("Authorization", "Bearer $TOKEN")
        }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(401, response.status)
        assertNull(chain.request)
        verify(jwt).verify(TOKEN)
        verify(jwt).tokenHash(TOKEN)
        verify(sessions).findByJti("session-1")
    }

    @Test
    fun `expired persisted session is rejected with 401`() {
        val sessions = mock(UserSessionRepository::class.java)
        val jwt = mock(JwtTokenService::class.java)
        val verified = VerifiedJwt(subject = USER.userId, userDatabaseId = USER.id, sessionId = "session-2", expiresAtEpochSeconds = 4_102_444_800)
        `when`(jwt.verify(TOKEN)).thenReturn(verified)
        `when`(jwt.tokenHash(TOKEN)).thenReturn(TOKEN_HASH)
        `when`(sessions.findByJti("session-2")).thenReturn(
            UserSessionEntity(
                id = 2,
                jti = "session-2",
                tokenHash = TOKEN_HASH,
                user = USER,
                expiresAt = OffsetDateTime.now().minusSeconds(5),
            ),
        )
        val filter = AuthTokenFilter(sessions, jwt, ObjectMapper())
        val request = MockHttpServletRequest("GET", "/api/profile").apply {
            servletPath = "/api/profile"
            addHeader("Authorization", "Bearer $TOKEN")
        }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertEquals(401, response.status)
        assertNull(chain.request)
        verify(jwt).verify(TOKEN)
        verify(jwt).tokenHash(TOKEN)
        verify(sessions).findByJti("session-2")
    }

    @Test
    fun `valid session sets auth context and proceeds`() {
        val sessions = mock(UserSessionRepository::class.java)
        val jwt = mock(JwtTokenService::class.java)
        val verified = VerifiedJwt(subject = USER.userId, userDatabaseId = USER.id, sessionId = "session-3", expiresAtEpochSeconds = 4_102_444_800)
        val persisted = UserSessionEntity(
            id = 3,
            jti = "session-3",
            tokenHash = TOKEN_HASH,
            user = USER,
            expiresAt = OffsetDateTime.now().plusMinutes(5),
        )
        `when`(jwt.verify(TOKEN)).thenReturn(verified)
        `when`(jwt.tokenHash(TOKEN)).thenReturn(TOKEN_HASH)
        `when`(sessions.findByJti("session-3")).thenReturn(persisted)
        val filter = AuthTokenFilter(sessions, jwt, ObjectMapper())
        val request = MockHttpServletRequest("GET", "/api/profile").apply {
            servletPath = "/api/profile"
            addHeader("Authorization", "Bearer $TOKEN")
        }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertNotNull(chain.request)
        assertEquals(200, response.status)
        verify(jwt).verify(TOKEN)
        verify(jwt).tokenHash(TOKEN)
        verify(sessions).findByJti("session-3")
        verify(jwt, never()).verify("other-token")
        assertNull(AuthContext.get())
    }

    companion object {
        private const val TOKEN = "valid-token"
        private const val TOKEN_HASH = "hash-123"
        private val USER = AppUserEntity(
            id = 1,
            userId = "u1001",
            account = "demo@doctorwang.app",
            passwordHash = "bcrypt-hash",
            name = "Demo User",
            age = 30,
            height = 170,
            weight = 65f,
        )
    }
}
