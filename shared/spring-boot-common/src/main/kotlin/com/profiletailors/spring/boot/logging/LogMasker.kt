package com.profiletailors.spring.boot.logging

import java.security.MessageDigest
import java.util.UUID

/**
 * Utility for masking sensitive information in logs.
 *
 * Uses SHA-256 hashing to create consistent, non-reversible identifiers
 * that can be used for tracing and debugging without exposing actual user data.
 */
object LogMasker {

    private const val HASH_PREVIEW_LENGTH = 12

    /**
     * Masks a sensitive value (like userId) using SHA-256 hash.
     * Returns the first 12 characters of the hash for compact logging.
     *
     * @param value The sensitive value to mask
     * @return A masked, non-reversible representation suitable for logging
     */
    fun mask(value: String): String {
        if (value.isBlank()) {
            return "unknown"
        }

        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(value.toByteArray(Charsets.UTF_8))
        val hashHex = hashBytes.joinToString("") { "%02x".format(it) }

        return hashHex.take(HASH_PREVIEW_LENGTH)
    }

    /**
     * Masks a UUID value.
     *
     * @param uuid The UUID to mask
     * @return A masked representation
     */
    fun mask(uuid: UUID): String = mask(uuid.toString())
}
