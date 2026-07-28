package com.profiletailors.smp.identity.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

class PasswordResetTokenHasherTest {

    private val fixedNow: Instant = Instant.parse("2026-07-27T12:00:00Z")

    @Test
    fun `generate produces 256-bit URL-safe token without padding`() {
        val generated = PasswordResetTokenHasher.generate(fixedNow)

        // 32 bytes -> 43 characters of URL-safe Base64 (no padding)
        assertEquals(43, generated.rawToken.length)
        assertTrue(generated.rawToken.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertFalse(generated.rawToken.contains("="))
    }

    @Test
    fun `generate sets expiresAt to 30 minutes in the future`() {
        val generated = PasswordResetTokenHasher.generate(fixedNow)
        assertEquals(fixedNow.plus(30, ChronoUnit.MINUTES), generated.expiresAt)
    }

    @Test
    fun `generate returns SHA-256 hex digest matching an external hash of the raw token`() {
        val generated = PasswordResetTokenHasher.generate(fixedNow)

        val expected = MessageDigest.getInstance("SHA-256")
            .digest(generated.rawToken.toByteArray())
            .joinToString("") { "%02x".format(it) }

        assertEquals(expected, generated.tokenHash)
    }

    @Test
    fun `hash is deterministic for the same raw token`() {
        val token = "fixed-raw-token"
        assertEquals(PasswordResetTokenHasher.hash(token), PasswordResetTokenHasher.hash(token))
    }

    @Test
    fun `hash produces different digests for different raw tokens`() {
        val tokenA = "raw-token-a"
        val tokenB = "raw-token-b"
        assertNotEquals(PasswordResetTokenHasher.hash(tokenA), PasswordResetTokenHasher.hash(tokenB))
    }

    @Test
    fun `hash output is 64 hex characters`() {
        val hash = PasswordResetTokenHasher.hash("raw-token")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `10000 generated tokens are unique`() {
        val iterations = 10_000
        val rawTokens = mutableSetOf<String>()
        val tokenHashes = mutableSetOf<String>()

        repeat(iterations) {
            val generated = PasswordResetTokenHasher.generate(fixedNow)
            rawTokens.add(generated.rawToken)
            tokenHashes.add(generated.tokenHash)
        }

        assertEquals(iterations, rawTokens.size, "Raw tokens must be unique")
        assertEquals(iterations, tokenHashes.size, "Token hashes must be unique")
    }

    @Test
    fun `hash never matches a different raw token`() {
        val a = PasswordResetTokenHasher.generate(fixedNow)
        val b = PasswordResetTokenHasher.generate(fixedNow)
        assertNotEquals(a.tokenHash, b.tokenHash)
        assertNotEquals(PasswordResetTokenHasher.hash(a.rawToken), PasswordResetTokenHasher.hash(b.rawToken))
    }

    @Test
    fun `decode test confirms URL-safe Base64 without padding`() {
        val bytes = ByteArray(32) { it.toByte() }
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        assertEquals(43, encoded.length)
        assertFalse(encoded.contains("="))
    }
}
