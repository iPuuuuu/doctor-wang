package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.persistence.repository.ReportProcessingJobRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.ReportUploadIdempotencyRepository
import com.ipuuuuu.doctorwang.mock.persistence.repository.UserSessionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.never
import java.time.OffsetDateTime

/**
 * 数据维护（DataMaintenanceService）：过期会话/幂等键清理 + 终态任务剪枝。
 */
class DataMaintenanceServiceTest {

    private val sessions = mock(UserSessionRepository::class.java)
    private val idempotency = mock(ReportUploadIdempotencyRepository::class.java)
    private val jobs = mock(ReportProcessingJobRepository::class.java)

    private fun service(enabled: Boolean = true) = DataMaintenanceService(sessions, idempotency, jobs, enabled)

    // Kotlin 非空参数 + Mockito any() 需 elvis 回退（项目既有模式）
    private fun anyTime() = any(OffsetDateTime::class.java) ?: OffsetDateTime.now()

    @Test
    fun `scheduled maintenance deletes expired sessions idempotency and prunes jobs`() {
        `when`(sessions.deleteExpired(anyTime())).thenReturn(3)
        `when`(sessions.deleteRevokedBefore(anyTime())).thenReturn(2)
        `when`(idempotency.deleteExpired(anyTime())).thenReturn(5)
        `when`(jobs.pruneTerminalBefore(anyTime())).thenReturn(4)

        service().scheduledMaintenance()

        verify(sessions).deleteExpired(anyTime())
        verify(sessions).deleteRevokedBefore(anyTime())
        verify(idempotency).deleteExpired(anyTime())
        verify(jobs).pruneTerminalBefore(anyTime())
    }

    @Test
    fun `disabled maintenance does nothing`() {
        service(enabled = false).scheduledMaintenance()
        verify(sessions, never()).deleteExpired(anyTime())
        verify(jobs, never()).pruneTerminalBefore(anyTime())
    }

    @Test
    fun `zero deletions is tolerated`() {
        `when`(sessions.deleteExpired(anyTime())).thenReturn(0)
        `when`(sessions.deleteRevokedBefore(anyTime())).thenReturn(0)
        `when`(idempotency.deleteExpired(anyTime())).thenReturn(0)
        `when`(jobs.pruneTerminalBefore(anyTime())).thenReturn(0)
        // 不抛异常即可
        service().scheduledMaintenance()
        assertEquals(1, 1)
    }
}
