package com.profiletailors.common.infrastructure.security

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class HasherImplementationsTest {

    @Test
    fun `Sha256Hasher should produce hex digest`() {
        val hasher = Sha256Hasher()
        val hash = hasher.hash("test-input")

        assertEquals(64, hash.length)
        assertEquals(hash, hasher.hash("test-input"))
        assertNotEquals(hash, hasher.hash("different-input"))
    }

    @Test
    fun `HmacHasher should produce hex digest with secret`() {
        val hasher = HmacHasher("secret-key")
        val hash = hasher.hash("test-input")

        assertEquals(64, hash.length)
        assertEquals(hash, hasher.hash("test-input"))

        val differentHasher = HmacHasher("different-secret")
        assertNotEquals(hash, differentHasher.hash("test-input"))
    }

    @Test
    fun `HmacHasher should throw if secret is blank`() {
        assertThrows<IllegalArgumentException> {
            HmacHasher("   ").hash("input")
        }
    }
}
