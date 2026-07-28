package com.profiletailors.smp.credentials.application

import com.profiletailors.smp.credentials.domain.CredentialException
import java.time.Instant

interface RefreshSessionGateway {
    suspend fun create(
        principalId: String,
        refreshToken: RefreshSessionToken,
        expiresAt: Instant,
    ): CreatedRefreshSession

    suspend fun requireActive(refreshToken: RefreshSessionToken, now: Instant): ActiveRefreshSession

    /**
     * Rotates an existing refresh session to a replacement session.
     *
     * @param currentSessionId The identifier of the session being rotated.
     * @param replacementToken The token for the replacement session.
     * @param expiresAt The expiration timestamp of the replacement session.
     * @param now The current timestamp used for time-based rotation logic.
     * @return The created replacement refresh session.
     */
    suspend fun rotate(
        currentSessionId: String,
        replacementToken: RefreshSessionToken,
        expiresAt: Instant,
        now: Instant,
    ): CreatedRefreshSession

    /**
 * Revokes the specified refresh session.
 *
 * @param currentSessionId The identifier of the refresh session to revoke.
 * @param now The timestamp used to apply time-based revocation logic.
 */
suspend fun revoke(currentSessionId: String, now: Instant)
}

class RefreshSessionNotActiveException(
    val lookupKey: String,
    val principalId: String? = null,
    val reason: RefreshSessionFailureReason,
) : CredentialException(
    when (reason) {
        RefreshSessionFailureReason.MISSING -> "Refresh session was not found."
        RefreshSessionFailureReason.INVALID -> "Refresh session is invalid."
        RefreshSessionFailureReason.REVOKED -> "Refresh session is revoked."
        RefreshSessionFailureReason.ROTATED -> "Refresh session has been rotated."
        RefreshSessionFailureReason.EXPIRED -> "Refresh session is expired."
    },
)
