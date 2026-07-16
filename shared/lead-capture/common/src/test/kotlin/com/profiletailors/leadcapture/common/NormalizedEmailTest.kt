package com.profiletailors.leadcapture.common

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

internal class NormalizedEmailTest {

    @Test
    fun `normalization lowercases already-trimmed email`() {
        val original = EmailAddress("User@Example.COM")
        val normalized = NormalizedEmail.from(original)
        assertEquals("user@example.com", normalized.value)
    }

    @Test
    fun `normalization does not change gmail dots`() {
        val original = EmailAddress("u.s.e.r@gmail.com")
        val normalized = NormalizedEmail.from(original)
        assertEquals("u.s.e.r@gmail.com", normalized.value)
    }

    @Test
    fun `normalization does not strip plus addressing`() {
        val original = EmailAddress("user+tag@gmail.com")
        val normalized = NormalizedEmail.from(original)
        assertEquals("user+tag@gmail.com", normalized.value)
    }

    @Test
    fun `original email is unchanged after normalization`() {
        val original = EmailAddress("User@Example.com")
        val normalized = NormalizedEmail.from(original)
        assertEquals("User@Example.com", original.value)
        assertEquals("user@example.com", normalized.value)
        assertNotEquals(original.value, normalized.value)
    }

    @Test
    fun `already normalized email stays the same`() {
        val original = EmailAddress("user@example.com")
        val normalized = NormalizedEmail.from(original)
        assertEquals("user@example.com", normalized.value)
    }

    @Test
    fun `equality is value-based`() {
        val n1 = NormalizedEmail.from(EmailAddress("User@Example.com"))
        val n2 = NormalizedEmail.from(EmailAddress("user@example.com"))
        assertEquals(n1, n2)
        assertEquals(n1.hashCode(), n2.hashCode())
    }
}
