package com.profiletailors.smp.credentials.application

import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

open class RefreshSessionTokenService {
    private val secureRandom = SecureRandom()

    open fun issue(): RefreshSessionToken = RefreshSessionToken(
        lookupKey = "rfs_${UUID.randomUUID()}",
        secret = generateSecret(),
    )

    open fun parse(rawValue: String): RefreshSessionToken {
        val segments = rawValue.split(DELIMITER, limit = 2)
        if (segments.size != 2 || segments.any { it.isBlank() }) {
            throw RefreshSessionNotActiveException(
                lookupKey = "invalid",
                reason = RefreshSessionFailureReason.INVALID,
            )
        }

        return RefreshSessionToken(
            lookupKey = segments[0],
            secret = segments[1],
        )
    }

    private fun generateSecret(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        private const val DELIMITER = "."
    }
}
