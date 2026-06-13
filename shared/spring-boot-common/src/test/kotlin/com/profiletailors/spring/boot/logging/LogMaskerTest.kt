package com.profiletailors.spring.boot.logging

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LogMaskerTest {

    @Test
    fun `should return unknown for blank values`() {
        assertEquals("unknown", LogMasker.mask(""))
        assertEquals("unknown", LogMasker.mask("   "))
    }

    @Test
    fun `should mask sensitive values deterministically`() {
        val first = LogMasker.mask("user-123")
        val second = LogMasker.mask("user-123")

        assertEquals(first, second)
        assertEquals(12, first.length)
        assertTrue(first.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `should produce different masks for different values`() {
        assertNotEquals(LogMasker.mask("user-123"), LogMasker.mask("user-456"))
    }

    @Test
    fun `should mask UUID values`() {
        val uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

        assertEquals(LogMasker.mask(uuid.toString()), LogMasker.mask(uuid))
    }
}
