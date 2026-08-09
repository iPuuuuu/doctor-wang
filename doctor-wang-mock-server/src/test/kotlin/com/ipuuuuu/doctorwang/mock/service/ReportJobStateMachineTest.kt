package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.ApiException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ReportJobStateMachineTest {
    private val machine = ReportJobStateMachine()

    @Test
    fun `allows stored to processing and processing to failed`() {
        assertEquals("PROCESSING" to "EXTRACTING", machine.transition("STORED", "STORED", "PROCESSING", "EXTRACTING"))
        assertEquals("FAILED" to "EXTRACTING", machine.transition("PROCESSING", "EXTRACTING", "FAILED", "EXTRACTING"))
    }

    @Test
    fun `rejects completed from stored`() {
        assertThrows(ApiException::class.java) { machine.transition("STORED", "STORED", "COMPLETED", "FINISHED") }
    }
}
