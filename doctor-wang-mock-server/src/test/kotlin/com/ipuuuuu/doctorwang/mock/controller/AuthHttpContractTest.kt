package com.ipuuuuu.doctorwang.mock.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ipuuuuu.doctorwang.mock.config.AuthProperties
import com.ipuuuuu.doctorwang.mock.config.AuthTokenFilter
import com.ipuuuuu.doctorwang.mock.config.GlobalExceptionHandler
import com.ipuuuuu.doctorwang.mock.model.LoginResponse
import com.ipuuuuu.doctorwang.mock.model.UserProfileResponse
import com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity
import com.ipuuuuu.doctorwang.mock.persistence.entity.UserSessionEntity
import com.ipuuuuu.doctorwang.mock.persistence.repository.UserSessionRepository
import com.ipuuuuu.doctorwang.mock.service.AccountService
import com.ipuuuuu.doctorwang.mock.service.BackendService
import com.ipuuuuu.doctorwang.mock.service.JwtTokenService
import com.ipuuuuu.doctorwang.mock.service.IpThrottleService
import com.ipuuuuu.doctorwang.mock.service.LoginThrottleService
import com.ipuuuuu.doctorwang.mock.service.PasswordHasher
import com.ipuuuuu.doctorwang.mock.persistence.repository.AppUserRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.HealthProfileDiseaseRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.HealthProfileRecordRepository
import jakarta.servlet.Filter
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import java.time.OffsetDateTime

class AuthHttpContractTest {

    private val accountService = mock(AccountService::class.java)
    private val objectMapper = ObjectMapper()
    private val backendService = mock(BackendService::class.java)
    private val userSessions = mock(UserSessionRepository::class.java)
    private val jwtTokenService = JwtTokenService(AuthProperties(jwtSecret = SECRET), objectMapper)
    private val filter: Filter = AuthTokenFilter(userSessions, jwtTokenService, objectMapper)

    @Test
    fun `protected profile endpoint returns 401 without bearer token`() {
        val mvc = profileMvc()

        mvc.perform(get("/api/profile"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))

        verifyNoInteractions(backendService, userSessions)
    }

    @Test
    fun `login endpoint bypasses auth filter and returns login response`() {
        val mvc = authMvc()
        doReturn(LoginResponse(token = "jwt-token", userId = "u1001")).`when`(backendService).login("demo@doctorwang.app", "123456")

        mvc.perform(
            post("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"account":"demo@doctorwang.app","password":"123456"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").value("jwt-token"))
            .andExpect(jsonPath("$.userId").value("u1001"))

        verify(backendService).login("demo@doctorwang.app", "123456")
        verifyNoInteractions(userSessions)
    }

    @Test
    fun `protected profile endpoint returns 200 with valid persisted session`() {
        val mvc = profileMvc()
        val (token, jwt) = jwtTokenService.issue(USER.userId, USER.id)
        val tokenHash = jwtTokenService.tokenHash(token)
        doReturn(
            UserSessionEntity(
                id = 1,
                jti = jwt.sessionId,
                tokenHash = tokenHash,
                user = USER,
                expiresAt = OffsetDateTime.now().plusMinutes(5),
            ),
        ).`when`(userSessions).findByJti(jwt.sessionId)
        doReturn(
            UserProfileResponse(
                userId = USER.userId,
                name = USER.name,
                age = USER.age,
                height = USER.height,
                weight = USER.weight,
                historyDiseases = emptyList(),
                records = emptyList(),
            ),
        ).`when`(backendService).getProfile()

        mvc.perform(get("/api/profile").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(USER.userId))
            .andExpect(jsonPath("$.name").value(USER.name))

        verify(userSessions).findByJti(jwt.sessionId)
        verify(backendService).getProfile()
    }

    @Test
    fun `logout requires a bearer token`() {
        authMvc().perform(post("/api/logout"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))

        verifyNoInteractions(backendService, userSessions)
    }

    @Test
    fun `account delete requires a bearer token`() {
        authMvc().perform(delete("/api/account"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
    }

    @Test
    fun `account delete succeeds with valid session`() {
        val sessions = statefulSessions()
        val tokenService = JwtTokenService(AuthProperties(jwtSecret = SECRET), objectMapper)
        val mvc = builder(AuthController(realBackend(sessions, tokenService), IpThrottleService(limit = 10000), accountService), sessions.repository, tokenService).build()
        val (token, jwt) = tokenService.issue(USER.userId, USER.id)
        sessions.put(
            UserSessionEntity(
                id = 7,
                jti = jwt.sessionId,
                tokenHash = tokenService.tokenHash(token),
                user = USER,
                expiresAt = OffsetDateTime.now().plusMinutes(5),
            ),
        )

        mvc.perform(delete("/api/account").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)

        verify(accountService).deleteAccount()
    }

    @Test
    fun `logout accepts the current valid session only`() {
        val mvc = authMvc()
        val (token, jwt) = jwtTokenService.issue(USER.userId, USER.id)
        doReturn(
            UserSessionEntity(
                id = 1,
                jti = jwt.sessionId,
                tokenHash = jwtTokenService.tokenHash(token),
                user = USER,
                expiresAt = OffsetDateTime.now().plusMinutes(5),
            ),
        ).`when`(userSessions).findByJti(jwt.sessionId)

        mvc.perform(post("/api/logout").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)

        verify(userSessions).findByJti(jwt.sessionId)
        verify(backendService).logout()
    }

    @Test
    fun `repeated logout is accepted for an already revoked current session`() {
        val mvc = authMvc()
        val (token, jwt) = jwtTokenService.issue(USER.userId, USER.id)
        doReturn(
            UserSessionEntity(
                id = 1,
                jti = jwt.sessionId,
                tokenHash = jwtTokenService.tokenHash(token),
                user = USER,
                expiresAt = OffsetDateTime.now().plusMinutes(5),
                revokedAt = OffsetDateTime.now().minusSeconds(1),
            ),
        ).`when`(userSessions).findByJti(jwt.sessionId)

        mvc.perform(post("/api/logout").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)

        verify(userSessions).findByJti(jwt.sessionId)
        verify(backendService).logout()
    }

    @Test
    fun `real backend logout revokes bearer and filter rejects it on the next protected request`() {
        val sessions = statefulSessions()
        val tokenService = JwtTokenService(AuthProperties(jwtSecret = SECRET), objectMapper)
        val (token, jwt) = tokenService.issue(USER.userId, USER.id)
        sessions.put(
            UserSessionEntity(
                id = 7,
                jti = jwt.sessionId,
                tokenHash = tokenService.tokenHash(token),
                user = USER,
                expiresAt = OffsetDateTime.now().plusMinutes(5),
            ),
        )
        val realBackend = realBackend(sessions, tokenService)
        val mvc = builder(AuthController(realBackend, IpThrottleService(limit = 10000), mock(AccountService::class.java)), sessions.repository, tokenService).build()

        mvc.perform(post("/api/logout").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)
        mvc.perform(get("/api/profile").header("Authorization", "Bearer $token"))
            .andExpect(status().isUnauthorized)
        check(sessions.get(jwt.sessionId)?.revokedAt != null) { "stateful repository did not persist revoke" }
    }

    @Test
    fun `register endpoint bypasses auth filter and returns login response`() {
        val mvc = authMvc()
        doReturn(LoginResponse(token = "jwt-new", userId = "u2001")).`when`(backendService)
            .register("new@test.com", "password1", "新用户", 25, 175, 70f)

        mvc.perform(
            post("/api/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"account":"new@test.com","password":"password1","name":"新用户","age":25,"height":175,"weight":70}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").value("jwt-new"))
            .andExpect(jsonPath("$.userId").value("u2001"))

        verify(backendService).register("new@test.com", "password1", "新用户", 25, 175, 70f)
        verifyNoInteractions(userSessions)
    }

    @Test
    fun `update profile requires bearer token`() {
        profileMvc().perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"新名字"}"""),
        )
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(backendService, userSessions)
    }

    @Test
    fun `update profile succeeds with valid session`() {
        val mvc = profileMvc()
        val (token, jwt) = jwtTokenService.issue(USER.userId, USER.id)
        doReturn(
            UserSessionEntity(
                id = 1,
                jti = jwt.sessionId,
                tokenHash = jwtTokenService.tokenHash(token),
                user = USER,
                expiresAt = OffsetDateTime.now().plusMinutes(5),
            ),
        ).`when`(userSessions).findByJti(jwt.sessionId)
        doReturn(
            UserProfileResponse(
                userId = USER.userId, name = "新名字", age = USER.age, height = USER.height,
                weight = USER.weight, historyDiseases = emptyList(), records = emptyList(),
            ),
        ).`when`(backendService).updateProfile("新名字", null, null, null)

        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"新名字"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("新名字"))
    }

    @Test
    fun `password change requires bearer token`() {
        authMvc().perform(
            post("/api/password/change")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"oldPassword":"old","newPassword":"new123456"}"""),
        )
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(backendService, userSessions)
    }

    @Test
    fun `password change succeeds with valid session`() {
        val mvc = authMvc()
        val (token, jwt) = jwtTokenService.issue(USER.userId, USER.id)
        doReturn(
            UserSessionEntity(
                id = 1,
                jti = jwt.sessionId,
                tokenHash = jwtTokenService.tokenHash(token),
                user = USER,
                expiresAt = OffsetDateTime.now().plusMinutes(5),
            ),
        ).`when`(userSessions).findByJti(jwt.sessionId)

        mvc.perform(
            post("/api/password/change")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"oldPassword":"old","newPassword":"new123456"}"""),
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `cross user or jti mismatch cannot revoke a victim session`() {
        val sessions = statefulSessions()
        val tokenService = JwtTokenService(AuthProperties(jwtSecret = SECRET), objectMapper)
        val (attackerToken, attackerJwt) = tokenService.issue(USER.userId, USER.id)
        val victim = USER.copyForTest(id = 2, userId = "u2002")
        val victimSession = UserSessionEntity(
            id = 8,
            jti = attackerJwt.sessionId,
            tokenHash = tokenService.tokenHash(attackerToken),
            user = victim,
            expiresAt = OffsetDateTime.now().plusMinutes(5),
        )
        sessions.put(victimSession)
        val realBackend = realBackend(sessions, tokenService)
        val mvc = builder(AuthController(realBackend, IpThrottleService(limit = 10000), mock(AccountService::class.java)), sessions.repository, tokenService).build()

        mvc.perform(post("/api/logout").header("Authorization", "Bearer $attackerToken"))
            .andExpect(status().isUnauthorized)
        check(sessions.get(attackerJwt.sessionId)?.revokedAt == null) { "cross-user session was revoked" }

        val (unknownJtiToken, unknownJti) = tokenService.issue(USER.userId, USER.id)
        mvc.perform(post("/api/logout").header("Authorization", "Bearer $unknownJtiToken"))
            .andExpect(status().isUnauthorized)
        check(sessions.get(unknownJti.sessionId) == null)
    }

    private fun profileMvc(): MockMvc {
        return builder(ProfileController(backendService)).build()
    }

    private fun authMvc(): MockMvc {
        return builder(AuthController(backendService, IpThrottleService(limit = 10000), accountService)).build()
    }

    private fun builder(controller: Any): StandaloneMockMvcBuilder = builder(controller, userSessions, jwtTokenService)

    private fun builder(controller: Any, sessions: UserSessionRepository, tokens: JwtTokenService): StandaloneMockMvcBuilder =
        MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(GlobalExceptionHandler())
        .addFilters(AuthTokenFilter(sessions, tokens, objectMapper))

    private fun statefulSessions(): StatefulSessions {
        val state = linkedMapOf<String, UserSessionEntity>()
        val repository = mock(UserSessionRepository::class.java) { invocation ->
            when (invocation.method.name) {
                "findByJti" -> state[invocation.getArgument<String>(0)]
                "revokeByJtiAndUserId" -> {
                    val session = state[invocation.getArgument<String>(0)]
                    if (session != null && session.user.id == invocation.getArgument<Long>(1)) {
                        session.revokedAt = invocation.getArgument<OffsetDateTime>(2)
                        1
                    } else 0
                }
                else -> null
            }
        }
        return StatefulSessions(repository, state)
    }

    private fun realBackend(sessions: StatefulSessions, tokens: JwtTokenService): BackendService = BackendService(
        PasswordHasher(), tokens, mock(AppUserRepository::class.java), sessions.repository,
        mock(HealthProfileDiseaseRepository::class.java), mock(HealthProfileRecordRepository::class.java),
        LoginThrottleService(),
    )

    private data class StatefulSessions(val repository: UserSessionRepository, val state: MutableMap<String, UserSessionEntity>) {
        fun put(session: UserSessionEntity) { state[session.jti] = session }
        fun get(jti: String): UserSessionEntity? = state[jti]
    }

    companion object {
        private const val SECRET = "01234567890123456789012345678901"
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

private fun AppUserEntity.copyForTest(id: Long, userId: String) = AppUserEntity(
    id = id,
    userId = userId,
    account = "${userId}@doctorwang.test",
    passwordHash = "bcrypt-hash",
    name = "Test User",
    age = age,
    height = height,
    weight = weight,
)
