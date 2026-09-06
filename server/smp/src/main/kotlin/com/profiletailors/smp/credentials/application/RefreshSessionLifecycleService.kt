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

    open suspend fun revokeAllForPrincipal(principalId: String) {
        refreshSessionGateway.revokeAllForPrincipal(principalId, clock.instant())
    }

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

    private fun expiresAt(): Instant = clock.instant().plusSeconds(properties.ttlSeconds)
}
