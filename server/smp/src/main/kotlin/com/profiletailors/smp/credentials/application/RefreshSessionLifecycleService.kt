package com.profiletailors.smp.credentials.application

import java.time.Clock
import java.time.Instant

class RefreshSessionLifecycleService(
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

    /**
     * Revokes the active refresh session associated with a raw refresh token.
     *
     * @param rawRefreshToken The raw refresh token identifying the session to revoke.
     */
    suspend fun revoke(rawRefreshToken: String) {
        val parsedToken = refreshSessionTokenService.parse(rawRefreshToken)
        val activeSession = refreshSessionGateway.requireActive(parsedToken, clock.instant())
        refreshSessionGateway.revoke(activeSession.id, clock.instant())
    }

    /**
 * Calculates the refresh session expiration time.
 *
 * @return The current time plus the configured session lifetime.
 */
private fun expiresAt(): Instant = clock.instant().plusSeconds(properties.ttlSeconds)
}
