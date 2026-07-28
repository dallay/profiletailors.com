package com.profiletailors.smp.identity.application

import com.profiletailors.smp.identity.domain.PasswordResetToken
import java.time.Instant

interface PasswordResetTokenRepository {

    /**
     * Marks every active (unused, unexpired) password reset token for the
     * principal as used at the given [invalidatedAt] timestamp. Tokens already
     * consumed are left untouched.
     */
    suspend fun invalidateActiveTokens(principalId: String, invalidatedAt: Instant)

    /**
     * Persists a new password reset token for the principal. The raw token is
     * never stored — only the SHA-256 hash in [tokenHash].
     */
    suspend fun create(principalId: String, tokenHash: String, requestedAt: Instant, expiresAt: Instant)

    suspend fun findByTokenHash(tokenHash: String): PasswordResetToken?

    /**
     * Atomically consumes the matching token and updates the principal's
     * password credential in a single database transaction. Returns true iff
     * exactly one row was consumed AND the password credential was updated.
     * On any failure the entire transaction rolls back.
     */
    suspend fun consumeAndUpdatePassword(tokenHash: String, now: Instant, newPasswordHash: String): Boolean
}
