package com.ipuuuuu.doctorwang.mock.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * IP 级频率限制（IpThrottleService）：窗口计数、超限拒绝、窗口重置。
 */
class IpThrottleServiceTest {

    @Test
    fun `allows up to limit then rejects`() {
        val service = IpThrottleService(window = Duration.ofHours(1), limit = 3)
        assertTrue(service.tryAcquire("1.2.3.4"))
        assertTrue(service.tryAcquire("1.2.3.4"))
        assertTrue(service.tryAcquire("1.2.3.4"))
        assertFalse(service.tryAcquire("1.2.3.4"))
    }

    @Test
    fun `different keys are independent`() {
        val service = IpThrottleService(window = Duration.ofHours(1), limit = 2)
        service.tryAcquire("ip-a"); service.tryAcquire("ip-a")
        assertFalse(service.tryAcquire("ip-a"))
        assertTrue(service.tryAcquire("ip-b"))
    }

    @Test
    fun `reset clears counters`() {
        val service = IpThrottleService(window = Duration.ofHours(1), limit = 1)
        service.tryAcquire("ip-a")
        assertFalse(service.tryAcquire("ip-a"))
        service.reset()
        assertTrue(service.tryAcquire("ip-a"))
    }
}
