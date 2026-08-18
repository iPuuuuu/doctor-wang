package com.ipuuuuu.doctorwang.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatUiStateTest {
    @Test
    fun `retry is available only for a failed message while idle`() {
        assertTrue(ChatUiState(failedMessage = "头疼三天", isSending = false).canRetry)
        assertFalse(ChatUiState(failedMessage = "头疼三天", isSending = true).canRetry)
        assertFalse(ChatUiState(failedMessage = null, isSending = false).canRetry)
        assertFalse(ChatUiState(failedMessage = "  ", isSending = false).canRetry)
    }
}
