package com.profiletailors.smp.identity.infrastructure

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCrypt

class BCryptPasswordHasherTest {

    private val hasher = BCryptPasswordHasher()

    @Test
    fun `should use BCrypt cost factor 12 when hashing a password`() {
        val hash = hasher.hash("test-password-123")

        hash shouldStartWith "\$2a\$12\$"
    }

    @Test
    fun `should return true when matching a freshly hashed password`() {
        val raw = "another-secure-password"
        val hash = hasher.hash(raw)

        hasher.matches(raw, hash).shouldBeTrue()
    }

    @Test
    fun `should return false when matching a wrong password`() {
        val hash = hasher.hash("correct-password-here")

        hasher.matches("wrong-password-here", hash).shouldBeFalse()
    }

    @Test
    fun `should verify legacy hashes with cost 10 when ensuring backward compatibility`() {
        val raw = "legacy-user-password"
        val legacyHash = BCrypt.hashpw(raw, BCrypt.gensalt(10))

        hasher.matches(raw, legacyHash).shouldBeTrue()
    }

    @Test
    fun `should return false when matching against a malformed hash`() {
        hasher.matches("any-password", "not-a-valid-bcrypt-hash").shouldBeFalse()
    }

    @Test
    fun `should truncate passwords longer than 72 bytes via SHA-256 pre-hash when hashing`() {
        val longPassword = "x".repeat(200)
        val hash = hasher.hash(longPassword)

        hasher.matches(longPassword, hash).shouldBeTrue()
    }

    @Test
    fun `should return bcrypt when querying the algorithm property`() {
        hasher.algorithm shouldBe "bcrypt"
    }
}
