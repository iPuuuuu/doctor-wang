package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.ApiException
import com.ipuuuuu.doctorwang.mock.config.AuthContext
import com.ipuuuuu.doctorwang.mock.config.AuthenticatedUser
import com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.UserSessionEntity
import com.ipuuuuu.doctorwang.mock.persistence.repository.AppUserRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.ChatMessageRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.HealthProfileDiseaseRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.HealthProfileRecordRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.UserSessionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime

class BackendServiceTest {
    private lateinit var USER: AppUserEntity

    @BeforeEach
    fun setUp() {
        USER = testUser()
    }

    private val passwordHasher = mock(PasswordHasher::class.java)
    private val jwtTokenService = mock(JwtTokenService::class.java)
    private val appUserRepository = mock(AppUserRepository::class.java)
    private val userSessionRepository = mock(UserSessionRepository::class.java)
    private val service = BackendService(
        passwordHasher = passwordHasher,
        jwtTokenService = jwtTokenService,
        appUserRepository = appUserRepository,
        userSessionRepository = userSessionRepository,
        healthProfileDiseaseRepository = mock(HealthProfileDiseaseRepository::class.java),
        healthProfileRecordRepository = mock(HealthProfileRecordRepository::class.java),
    )

    @Test
    fun `login rejects unknown account with generic authentication error`() {
        `when`(appUserRepository.findByAccount("missing@doctorwang.app")).thenReturn(null)

        val error = assertThrows(ApiException::class.java) {
            service.login("missing@doctorwang.app", "123456")
        }

        assertEquals("AUTHENTICATION_FAILED", error.code)
        verify(passwordHasher, never()).matches(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())
        verify(userSessionRepository, never()).save(org.mockito.ArgumentMatchers.any(UserSessionEntity::class.java))
    }

    @Test
    fun `login rejects invalid password with generic authentication error`() {
        `when`(appUserRepository.findByAccount(USER.account)).thenReturn(USER)
        `when`(passwordHasher.matches("wrong-password", USER.passwordHash)).thenReturn(false)

        val error = assertThrows(ApiException::class.java) {
            service.login(USER.account, "wrong-password")
        }

        assertEquals("AUTHENTICATION_FAILED", error.code)
        verify(passwordHasher).matches("wrong-password", USER.passwordHash)
        verify(userSessionRepository, never()).save(org.mockito.ArgumentMatchers.any(UserSessionEntity::class.java))
    }

    @Test
    fun `login trims account persists hashed session and returns token response`() {
        val token = "jwt-token"
        val verifiedJwt = VerifiedJwt(
            subject = USER.userId,
            userDatabaseId = USER.id,
            sessionId = "session-1",
            expiresAtEpochSeconds = OffsetDateTime.now().plusMinutes(30).toEpochSecond(),
        )
        `when`(appUserRepository.findByAccount(USER.account)).thenReturn(USER)
        `when`(passwordHasher.matches("123456", USER.passwordHash)).thenReturn(true)
        `when`(jwtTokenService.issue(USER.userId, USER.id)).thenReturn(token to verifiedJwt)
        `when`(jwtTokenService.tokenHash(token)).thenReturn(TOKEN_HASH)
        `when`(userSessionRepository.save(org.mockito.ArgumentMatchers.any(UserSessionEntity::class.java)))
            .thenAnswer { it.arguments[0] }

        val response = service.login("  ${USER.account}  ", "123456")

        assertEquals(token, response.token)
        assertEquals(USER.userId, response.userId)
        verify(appUserRepository).findByAccount(USER.account)
        verify(passwordHasher).matches("123456", USER.passwordHash)
        verify(jwtTokenService).issue(USER.userId, USER.id)
        verify(jwtTokenService).tokenHash(token)

        val captor = ArgumentCaptor.forClass(UserSessionEntity::class.java)
        verify(userSessionRepository).save(captor.capture())
        val saved = captor.value
        assertEquals("session-1", saved.jti)
        assertEquals(TOKEN_HASH, saved.tokenHash)
        assertEquals(USER.id, saved.user.id)
        assertEquals(USER.userId, saved.user.userId)
        assertNotNull(saved.expiresAt)
        assertEquals(null, saved.revokedAt)
    }

    @Test
    fun `logout revokes only the authenticated users current session`() {
        AuthContext.set(AuthenticatedUser(sessionToken = "current-token", sessionId = "current-jti", user = USER))
        try {
            service.logout()

            verify(userSessionRepository).revokeByJtiAndUserId(
                eqString("current-jti"),
                eqLong(USER.id),
                anyOffsetDateTime(),
            )
            verifyNoMoreInteractions(userSessionRepository)
        } finally {
            AuthContext.clear()
        }
    }

    // ---- register ----

    @Test
    fun `register creates user with hashed password and returns login response`() {
        `when`(appUserRepository.findByAccount("new@test.com")).thenReturn(null)
        `when`(passwordHasher.hash("password1")).thenReturn("bcrypt-new")
        `when`(jwtTokenService.issue(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong()))
            .thenReturn("jwt-new" to VerifiedJwt("u2001", 2, "session-new", OffsetDateTime.now().plusHours(1).toEpochSecond()))
        `when`(jwtTokenService.tokenHash("jwt-new")).thenReturn("hash-new")
        `when`(appUserRepository.save(org.mockito.ArgumentMatchers.any(AppUserEntity::class.java))).thenAnswer { it.arguments[0] }
        `when`(userSessionRepository.save(org.mockito.ArgumentMatchers.any(UserSessionEntity::class.java))).thenAnswer { it.arguments[0] }

        val response = service.register("new@test.com", "password1", "新用户", 25, 175, 70f)

        assertEquals("jwt-new", response.token)
        assertEquals(true, response.userId.isNotBlank())
        verify(passwordHasher).hash("password1")
        verify(appUserRepository).save(org.mockito.ArgumentMatchers.any(AppUserEntity::class.java))
    }

    @Test
    fun `register rejects duplicate account`() {
        `when`(appUserRepository.findByAccount(USER.account)).thenReturn(USER)

        val error = assertThrows(ApiException::class.java) { service.register(USER.account, "password", "test", 20, 170, 60f) }

        assertEquals("ACCOUNT_EXISTS", error.code)
        verify(passwordHasher, never()).hash(org.mockito.ArgumentMatchers.anyString())
    }

    @Test
    fun `register rejects short password`() {
        assertThrows(IllegalArgumentException::class.java) { service.register("a@b.com", "12345", "test", 20, 170, 60f) }
    }

    // ---- updateProfile ----

    @Test
    fun `updateProfile persists only updated fields`() {
        AuthContext.set(AuthenticatedUser(sessionToken = "t", sessionId = "s", user = USER))
        // Re-fetch from repository returns a managed entity
        `when`(appUserRepository.findById(USER.id)).thenReturn(java.util.Optional.of(USER))
        try {
            val result = service.updateProfile(name = "新名字", age = null, height = null, weight = null)

            assertEquals("新名字", result.name)
            assertEquals(USER.age, result.age)
            assertEquals(USER.height, result.height)
            assertEquals(USER.weight, result.weight)
            assertEquals("新名字", USER.name) // mutated directly on managed entity
        } finally {
            AuthContext.clear()
        }
    }

    // ---- changePassword ----

    @Test
    fun `changePassword succeeds with correct old password`() {
        AuthContext.set(AuthenticatedUser(sessionToken = "t", sessionId = "current-jti", user = USER))
        val originalHash = USER.passwordHash
        `when`(appUserRepository.findById(USER.id)).thenReturn(java.util.Optional.of(USER))
        `when`(passwordHasher.matches("old", originalHash)).thenReturn(true)
        `when`(passwordHasher.hash("newpass")).thenReturn("new-hash")
        try {
            service.changePassword("old", "newpass")

            verify(passwordHasher).matches("old", originalHash)
            verify(passwordHasher).hash("newpass")
            assertEquals("new-hash", USER.passwordHash)
            verify(userSessionRepository).revokeAllExcept(
                eqLong(USER.id),
                eqString("current-jti"),
                anyOffsetDateTime(),
            )
        } finally {
            AuthContext.clear()
        }
    }

    @Test
    fun `changePassword rejects wrong old password`() {
        AuthContext.set(AuthenticatedUser(sessionToken = "t", sessionId = "current-jti", user = USER))
        `when`(passwordHasher.matches("wrong", USER.passwordHash)).thenReturn(false)
        try {
            val error = assertThrows(ApiException::class.java) { service.changePassword("wrong", "newpass") }
            assertEquals("PASSWORD_MISMATCH", error.code)
            verify(passwordHasher, never()).hash(org.mockito.ArgumentMatchers.anyString())
        } finally {
            AuthContext.clear()
        }
    }

    @Test
    fun `logout is idempotent when current session was already revoked`() {
        AuthContext.set(AuthenticatedUser(sessionToken = "current-token", sessionId = "current-jti", user = USER))
        try {
            service.logout()

            verify(userSessionRepository).revokeByJtiAndUserId(
                eqString("current-jti"),
                eqLong(USER.id),
                anyOffsetDateTime(),
            )
        } finally {
            AuthContext.clear()
        }
    }

    companion object {
        private fun anyOffsetDateTime(): OffsetDateTime = org.mockito.ArgumentMatchers.any(OffsetDateTime::class.java) ?: OffsetDateTime.MIN
        private fun eqString(value: String): String = org.mockito.ArgumentMatchers.eq(value) ?: value
        private fun eqLong(value: Long): Long = org.mockito.ArgumentMatchers.eq(value) ?: value

        private const val TOKEN_HASH = "abc123hash"
        private fun testUser() = AppUserEntity(
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
