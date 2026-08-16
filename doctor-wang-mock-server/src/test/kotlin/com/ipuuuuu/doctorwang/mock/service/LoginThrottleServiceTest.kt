package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.ApiException
import com.ipuuuuu.doctorwang.mock.persistence.entity.AppUserEntity
import com.ipuuuuu.doctorwang.mock.persistence.repository.AppUserRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.HealthProfileDiseaseRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.HealthProfileRecordRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.UserSessionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.time.Duration

/**
 * 登录暴力破解防护（LoginThrottleService）：失败计数、锁定、解锁、成功清零。
 */
class LoginThrottleServiceTest {

    private val throttle = LoginThrottleService(window = Duration.ofMinutes(15), maxFailures = 3, lockDuration = Duration.ofMinutes(15))

    @Test
    fun `locks after max failures and rejects while locked`() {
        assertFalse(throttle.isLocked("a@b.com"))
        repeat(2) { throttle.recordFailure("a@b.com") }
        assertFalse(throttle.isLocked("a@b.com"))
        val locked = throttle.recordFailure("a@b.com") // 第 3 次触发锁定
        assertTrue(locked)
        assertTrue(throttle.isLocked("a@b.com"))
    }

    @Test
    fun `clear resets failure state`() {
        repeat(2) { throttle.recordFailure("c@d.com") }
        throttle.clear("c@d.com")
        assertFalse(throttle.isLocked("c@d.com"))
        assertFalse(throttle.recordFailure("c@d.com"))
    }

    @Test
    fun `backend login locked throws LOGIN_LOCKED`() {
        val user = AppUserEntity(id = 1, userId = "u1", account = "lock@t.local", passwordHash = "h", name = "n", age = 30, height = 170, weight = 60f)
        val appRepo = mock(AppUserRepository::class.java)
        `when`(appRepo.findByAccount("lock@t.local")).thenReturn(user)
        val shared = LoginThrottleService(maxFailures = 3, lockDuration = Duration.ofMinutes(15))
        val service = BackendService(
            PasswordHasher(), mock(JwtTokenService::class.java), appRepo,
            mock(UserSessionRepository::class.java), mock(HealthProfileDiseaseRepository::class.java),
            mock(HealthProfileRecordRepository::class.java), shared,
        )
        repeat(3) { shared.recordFailure("lock@t.local") }
        assertTrue(shared.isLocked("lock@t.local"))
        val error = assertThrows(ApiException::class.java) { service.login("lock@t.local", "x") }
        assertEquals("LOGIN_LOCKED", error.code)
    }

    @Test
    fun `reset clears all`() {
        throttle.recordFailure("x@y.com"); throttle.recordFailure("x@y.com"); throttle.recordFailure("x@y.com")
        assertTrue(throttle.isLocked("x@y.com"))
        throttle.reset()
        assertFalse(throttle.isLocked("x@y.com"))
    }
}
