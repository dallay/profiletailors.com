package com.profiletailors.smp.identity.domain

import com.profiletailors.smp.identity.application.EmailVerificationTokenHasher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class EmailVerificationTokenHasherTest {

    @Test
    fun `generates unique token with hash and expiry`() {
        val result = EmailVerificationTokenHasher.generate()

        assertNotNull(result.rawToken)
        assertNotNull(result.tokenHash)
        assertNotNull(result.expiresAt)
        assertTrue(result.expiresAt.isAfter(Instant.now()))
        assertTrue(result.tokenHash.length == 64) // SHA-256 hex digest is 64 chars
    }

    @Test
    fun `hash produces deterministic output for same input`() {
        val hash1 = EmailVerificationTokenHasher.hash("test-token")
        val hash2 = EmailVerificationTokenHasher.hash("test-token")

        assertEquals(hash1, hash2)
    }

    @Test
    fun `hash produces different output for different inputs`() {
        val hash1 = EmailVerificationTokenHasher.hash("token-1")
        val hash2 = EmailVerificationTokenHasher.hash("token-2")

        assertTrue(hash1 != hash2)
    }

    @Test
    fun `raw token can be verified against its hash`() {
        val generated = EmailVerificationTokenHasher.generate()
        val recomputedHash = EmailVerificationTokenHasher.hash(generated.rawToken)

        assertEquals(generated.tokenHash, recomputedHash)
    }

    @Test
    fun `generated tokens are unique`() {
        val tokens = (1..100).map { EmailVerificationTokenHasher.generate().rawToken }

        assertEquals(tokens.size, tokens.toSet().size)
    }
}
