package com.profiletailors.smp.identity.application

import com.profiletailors.smp.identity.domain.PasswordResetToken
import java.time.Instant

/**
 * Signals the password credential row was not found during a password reset
 * consume operation. Callers MUST NOT catch this exception; re-throw it so
 * the surrounding transaction rolls back.
 */
class PasswordResetCredentialMissingException :
    RuntimeException("Password credential row not found for password reset token.")

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
     * password credential in a single database transaction. Returns without
     * exception iff exactly one row was consumed AND the password credential
     * was updated. Throws [PasswordResetCredentialMissingException] when the
     * credential row does not exist — callers MUST NOT catch this exception;
     * it signals that the surrounding transaction should roll back.
     */
    suspend fun consumeAndUpdatePassword(tokenHash: String, now: Instant, newPasswordHash: String)
}
