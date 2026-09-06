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

    suspend fun revoke(currentSessionId: String, now: Instant)

    suspend fun revokeAllForPrincipal(principalId: String, now: Instant) = Unit

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
