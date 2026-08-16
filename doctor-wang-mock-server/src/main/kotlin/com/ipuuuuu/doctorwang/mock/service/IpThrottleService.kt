package com.ipuuuuu.doctorwang.mock.service

import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * IP 级频率限制（注册防垃圾/防滥用）。
 *
 * 规则（默认）：同一 IP 在 1 小时内最多 limit 次调用；超限拒绝。
 * 客户端 IP 取 nginx 注入的 X-Real-IP（后端不对外直连，头部不可伪造）。
 * 基于内存实现（单实例部署）。
 */
@Component
class IpThrottleService(
    private val window: Duration = Duration.ofHours(1),
    private val limit: Int = 10,
) {
    private data class Window(var count: Int, var startedAt: Instant)

    private val counters = ConcurrentHashMap<String, Window>()

    /** 尝试一次调用；返回是否允许（false = 超限拒绝）。 */
    fun tryAcquire(key: String): Boolean {
        val now = Instant.now()
        counters.compute(key) { _, existing ->
            val w = existing ?: Window(0, now)
            if (Duration.between(w.startedAt, now) > window) Window(1, now) else w.also { it.count += 1 }
        }
        val current = counters[key] ?: return true
        return current.count <= limit
    }

    /** 测试/运维：重置。 */
    fun reset() = counters.clear()
}
