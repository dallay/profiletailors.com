package com.profiletailors.smp.identity.infrastructure

import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCrypt
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BCryptPasswordHasherTest {

    private val hasher = BCryptPasswordHasher()

    @Test
    fun `hash uses BCrypt cost factor 12`() {
        val hash = hasher.hash("test-password-123")

        assertTrue(hash.startsWith("\$2a\$12\$"), "New hashes must use cost 12, got: $hash")
    }

    @Test
    fun `matches returns true for a freshly hashed password`() {
        val raw = "another-secure-password"
        val hash = hasher.hash(raw)

        assertTrue(hasher.matches(raw, hash))
    }

    @Test
    fun `matches returns false for wrong password`() {
        val hash = hasher.hash("correct-password-here")

        assertTrue(!hasher.matches("wrong-password-here", hash))
    }

    @Test
    fun `matches verifies legacy hashes with cost 10 (backward compatible)`() {
        val raw = "legacy-user-password"
        val legacyHash = BCrypt.hashpw(raw, BCrypt.gensalt(10))

        assertTrue(hasher.matches(raw, legacyHash), "Cost-10 hashes must still verify after upgrade to cost 12")
    }

    @Test
    fun `matches returns false for malformed hash`() {
        assertTrue(!hasher.matches("any-password", "not-a-valid-bcrypt-hash"))
    }

    @Test
    fun `hash truncates passwords longer than 72 bytes via SHA-256 pre-hash`() {
        val longPassword = "x".repeat(200)
        val hash = hasher.hash(longPassword)

        assertTrue(hasher.matches(longPassword, hash))
    }

    @Test
    fun `algorithm property returns bcrypt`() {
        assertEquals("bcrypt", hasher.algorithm)
    }
}
