package com.profiletailors.smp.identity.infrastructure

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Argon2idPasswordHasherTest {

    private val hasher = Argon2idPasswordHasher()

    @Test
    fun `algorithm returns argon2id`() {
        assertEquals("argon2id", hasher.algorithm)
    }

    @Test
    fun `hash produces an argon2id format hash`() {
        val hash = hasher.hash("securePassword123")
        assertTrue(hash.startsWith("\$argon2id\$"), "Hash should start with Argon2id prefix, got: $hash")
    }

    @Test
    fun `matches verifies correct password against its own hash`() {
        val password = "myPassword42!"
        val hash = hasher.hash(password)
        assertTrue(hasher.matches(password, hash))
    }

    @Test
    fun `matches rejects incorrect password`() {
        val hash = hasher.hash("realPassword")
        assertFalse(hasher.matches("wrongPassword", hash))
    }

    @Test
    fun `matches returns false not throws for malformed hash`() {
        assertFalse(hasher.matches("anyPassword", "not-a-valid-hash"))
        assertFalse(hasher.matches("anyPassword", ""))
        assertFalse(hasher.matches("anyPassword", "invalid-hash-format"))
    }
}
