package com.ipuuuuu.doctorwang.mock.service

import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 登录暴力破解防护：账号级失败计数 + 临时锁定。
 *
 * 规则（默认）：15 分钟内同一账号失败 5 次 → 锁定 15 分钟（锁定期间登录直接拒绝）。
 * 成功登录清零。基于内存实现（单实例部署；多实例需换 Redis 等共享存储）。
 */
@Component
class LoginThrottleService(
    private val window: Duration = Duration.ofMinutes(15),
    private val maxFailures: Int = 5,
    private val lockDuration: Duration = Duration.ofMinutes(15),
) {
    private data class Counter(var failures: Int, var firstFailure: Instant, var lockedUntil: Instant? = null)

    private val counters = ConcurrentHashMap<String, Counter>()

    /** 是否处于锁定状态（锁定过期自动解除）。 */
    fun isLocked(account: String): Boolean {
        val counter = counters[account] ?: return false
        val lockedUntil = counter.lockedUntil ?: return false
        if (Instant.now().isAfter(lockedUntil)) {
            counters.remove(account, counter)
            return false
        }
        return true
    }

    /** 记录一次失败；返回是否因此触发锁定（供上层给出不同提示）。 */
    fun recordFailure(account: String): Boolean {
        val now = Instant.now()
        val counter = counters.compute(account) { _, existing ->
            val c = existing ?: Counter(0, now, null)
            if (existing == null || Duration.between(c.firstFailure, now) > window) {
                Counter(1, now, null)
            } else {
                c.failures += 1
                c
            }
        } ?: Counter(1, now, null)
        if (counter.failures >= maxFailures) {
            counter.lockedUntil = now.plus(lockDuration)
            counter.failures = 0 // 锁定期满后重新计数
            return true
        }
        return false
    }

    /** 登录成功后清除该账号的失败记录。 */
    fun clear(account: String) {
        counters.remove(account)
    }

    /** 测试/运维：重置全部状态。 */
    fun reset() = counters.clear()
}
