package com.profiletailors.smp.identity.application

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

object PasswordResetTokenHasher {

    private const val TOKEN_BYTE_SIZE = 32
    private const val TOKEN_TTL_MINUTES = 30L
    private val secureRandom = SecureRandom()

    fun generate(now: Instant = Instant.now()): GeneratedPasswordResetToken {
        val rawBytes = ByteArray(TOKEN_BYTE_SIZE)
        secureRandom.nextBytes(rawBytes)
        val rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes)
        val tokenHash = hash(rawToken)
        val expiresAt = now.plus(TOKEN_TTL_MINUTES, ChronoUnit.MINUTES)
        return GeneratedPasswordResetToken(rawToken, tokenHash, expiresAt)
    }

    fun hash(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(rawToken.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

data class GeneratedPasswordResetToken(val rawToken: String, val tokenHash: String, val expiresAt: Instant)
