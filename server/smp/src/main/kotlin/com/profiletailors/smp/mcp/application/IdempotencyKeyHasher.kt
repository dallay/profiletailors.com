package com.profiletailors.smp.mcp.application

import java.security.MessageDigest

object IdempotencyKeyHasher {

    private const val SHA_256_HEX_LENGTH = 64

    fun hash(plaintext: String): String {
        require(plaintext.isNotBlank()) { "Idempotency key plaintext must not be blank." }
        val bytes = MessageDigest.getInstance("SHA-256").digest(plaintext.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isValidHash(candidate: String): Boolean = candidate.length == SHA_256_HEX_LENGTH &&
        candidate.all { it.isDigit() || it in 'a'..'f' }
}
