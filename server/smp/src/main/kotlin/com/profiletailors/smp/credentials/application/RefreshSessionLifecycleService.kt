package com.profiletailors.smp.credentials.application

import java.time.Clock
import java.time.Instant

open class RefreshSessionLifecycleService(
    private val refreshSessionGateway: RefreshSessionGateway,
    private val refreshSessionTokenService: RefreshSessionTokenService,
    private val properties: RefreshSessionProperties,
    private val clock: Clock,
) {
    suspend fun issue(principalId: String): CreatedRefreshSession {
        val token = refreshSessionTokenService.issue()
        return refreshSessionGateway.create(
            principalId = principalId,
            refreshToken = token,
            expiresAt = expiresAt(),
        )
    }

    suspend fun rotate(rawRefreshToken: String): RotatedRefreshSession {
        val parsedToken = refreshSessionTokenService.parse(rawRefreshToken)
        val activeSession = refreshSessionGateway.requireActive(parsedToken, clock.instant())
        val replacementToken = refreshSessionTokenService.issue()
        val replacementSession = refreshSessionGateway.rotate(
            currentSessionId = activeSession.id,
            replacementToken = replacementToken,
            expiresAt = expiresAt(),
            now = clock.instant(),
        )

        return RotatedRefreshSession(
            previousSessionId = activeSession.id,
            current = replacementSession,
        )
    }

    suspend fun revoke(rawRefreshToken: String) {
        val parsedToken = refreshSessionTokenService.parse(rawRefreshToken)
        val activeSession = refreshSessionGateway.requireActive(parsedToken, clock.instant())
        refreshSessionGateway.revoke(activeSession.id, clock.instant())
    }

    /**
     * Revokes all refresh sessions belonging to a principal.
     *
     * @param principalId The identifier of the principal whose sessions are revoked.
     */
    open suspend fun revokeAllForPrincipal(principalId: String) {
        refreshSessionGateway.revokeAllForPrincipal(principalId, clock.instant())
    }

    /**
     * Revokes all refresh sessions for a principal except the active session identified by the optional token.
     *
     * @param principalId The identifier of the principal whose sessions are revoked.
     * @param excludeRawRefreshToken The raw refresh token identifying the session to preserve; if absent, invalid, or inactive, all sessions are revoked.
     */
    open suspend fun revokeOthersForPrincipal(principalId: String, excludeRawRefreshToken: String?) {
        val activeSessionId = if (!excludeRawRefreshToken.isNullOrBlank()) {
            try {
                val parsedToken = refreshSessionTokenService.parse(excludeRawRefreshToken)
                val activeSession = refreshSessionGateway.requireActive(parsedToken, clock.instant())
                activeSession.id
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        if (activeSessionId != null) {
            refreshSessionGateway.revokeOthersForPrincipal(principalId, activeSessionId, clock.instant())
        } else {
            refreshSessionGateway.revokeAllForPrincipal(principalId, clock.instant())
        }
    }

    /**
 * Calculates the refresh session expiration time from the current clock time and configured TTL.
 *
 * @return The calculated expiration timestamp.
 */
private fun expiresAt(): Instant = clock.instant().plusSeconds(properties.ttlSeconds)
}
