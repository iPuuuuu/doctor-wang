package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.ApiException
import com.ipuuuuu.doctorwang.mock.model.AiRagResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AiSafetyGateTest {
    @Test fun `high risk remains review required`() {
        val out = AiSafetyGate.validate(AiRagResponse("r", "SAFETY_BLOCKED", "HIGH", "就医分流", traceId = "t"), 1)
        assertEquals(true, out.needsReview)
    }
    @Test fun `insufficient evidence remains review required`() {
        val out = AiSafetyGate.validate(AiRagResponse("r", "INSUFFICIENT_EVIDENCE", "LOW", "资料不足", traceId = "t"), 1)
        assertEquals(true, out.needsReview)
    }
    @Test fun `ok without citations is rejected`() {
        assertThrows(ApiException::class.java) { AiSafetyGate.validate(AiRagResponse("r", "OK", "LOW", "answer", traceId = "t"), 1) }
    }
}
