package com.profiletailors.smp.identity.application

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

/**
 * Utility for generating and hashing email verification tokens.
 *
 * Follows the same SHA-256 pattern as refresh token hashing.
 * Raw tokens are 32 bytes (256 bits) from a CSPRNG, then Base64-encoded.
 */
object EmailVerificationTokenHasher {

    private const val TOKEN_BYTE_SIZE = 32
    private const val TOKEN_TTL_HOURS = 24L
    private val secureRandom = SecureRandom()

    /**
     * Generates a raw verification token and its SHA-256 hash.
     *
     * @param now the instant used to calculate [expiresAt]
     * @return [GeneratedToken] containing the raw token, hash, and expiry
     */
    fun generate(now: Instant = Instant.now()): GeneratedToken {
        val rawBytes = ByteArray(TOKEN_BYTE_SIZE)
        secureRandom.nextBytes(rawBytes)
        val rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes)
        val tokenHash = hash(rawToken)
        val expiresAt = now.plus(TOKEN_TTL_HOURS, ChronoUnit.HOURS)
        return GeneratedToken(rawToken = rawToken, tokenHash = tokenHash, expiresAt = expiresAt)
    }

    /**
     * Computes the SHA-256 hex digest of [rawToken].
     */
    fun hash(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(rawToken.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

data class GeneratedToken(val rawToken: String, val tokenHash: String, val expiresAt: Instant)
