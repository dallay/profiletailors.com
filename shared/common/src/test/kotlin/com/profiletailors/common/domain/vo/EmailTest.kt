package com.profiletailors.common.domain.vo.email

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class EmailTest {
    @Test
    fun `should create valid email`() {
        val email = Email("test@example.com")
        assertEquals("test@example.com", email.value)
    }

    @Test
    fun `should throw on invalid email format`() {
        assertThrows<IllegalArgumentException> { Email("invalid-email") }
        assertThrows<IllegalArgumentException> { Email("test@") }
        assertThrows<IllegalArgumentException> { Email("@example.com") }
    }
}
