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

    suspend fun rotate(
        currentSessionId: String,
        replacementToken: RefreshSessionToken,
        expiresAt: Instant,
        now: Instant,
    ): CreatedRefreshSession

    /**
 * Revokes the specified refresh session.
 *
 * @param currentSessionId The identifier of the session to revoke.
 * @param now The time at which the revocation occurs.
 */
suspend fun revoke(currentSessionId: String, now: Instant)

    /**
 * Revokes all refresh sessions belonging to a principal.
 *
 * @param principalId The principal whose sessions should be revoked.
 * @param now The time at which the revocation occurs.
 */
suspend fun revokeAllForPrincipal(principalId: String, now: Instant) = Unit

    /**
 * Revokes all refresh sessions for a principal except the specified session.
 *
 * @param principalId The principal whose sessions are revoked.
 * @param excludeSessionId The session to keep active.
 * @param now The time at which revocation is evaluated.
 */
suspend fun revokeOthersForPrincipal(principalId: String, excludeSessionId: String, now: Instant) = Unit
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
