package com.profiletailors.common.domain.vo

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UsernameTest {
    @Test
    fun `should create valid username`() {
        val username = Username("johndoe")
        assertEquals("johndoe", username.value)
    }

    @Test
    fun `should throw on empty username`() {
        assertThrows<IllegalArgumentException> { Username("") }
        assertThrows<IllegalArgumentException> { Username("  ") }
    }

    @Test
    fun `should throw on too short or too long username`() {
        assertThrows<IllegalArgumentException> { Username("jo") }
        assertThrows<IllegalArgumentException> { Username("a".repeat(101)) }
    }

    @Test
    fun `of should return null for invalid input`() {
        assertNull(Username.of(""))
        assertNull(Username.of("jo"))
        assertEquals("johndoe", Username.of("johndoe")?.value)
    }
}
