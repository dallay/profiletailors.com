package com.profiletailors.smp.identity.domain

import java.time.Instant

/**
 * Value object representing an email verification token stored in the database.
 *
 * The token is stored as a SHA-256 hash (tokenHash). The raw token value is
 * only held in-memory during generation and is never persisted.
 *
 * @property email the email address being verified
 * @property tokenHash SHA-256 hash of the raw verification token
 * @property expiresAt instant after which the token is no longer valid
 * @property usedAt instant when the token was consumed, or null if still valid
 */
data class EmailVerificationToken(
    val email: String,
    val tokenHash: String,
    val expiresAt: Instant,
    val usedAt: Instant? = null,
) {
    /**
     * Returns true if the token is valid at the given [now].
     *
     * A token is valid when it has not been used yet and has not expired.
     */
    fun isValid(now: Instant): Boolean =
        usedAt == null && now.isBefore(expiresAt)
}
