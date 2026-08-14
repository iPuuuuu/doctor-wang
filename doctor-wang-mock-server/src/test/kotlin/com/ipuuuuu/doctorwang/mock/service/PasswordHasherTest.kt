package com.ipuuuuu.doctorwang.mock.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PasswordHasherTest {
    private val hasher = PasswordHasher()

    @Test
    fun `hash verifies without storing raw password`() {
        val hash = hasher.hash("123456")
        assertTrue(hasher.matches("123456", hash))
        assertFalse(hasher.matches("wrong", hash))
        assertFalse(hash.contains("123456"))
    }
}
