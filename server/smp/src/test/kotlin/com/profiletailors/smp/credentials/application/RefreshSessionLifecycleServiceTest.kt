package com.profiletailors.smp.credentials.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RefreshSessionLifecycleServiceTest {

    private val now = Instant.parse("2026-05-22T10:15:30Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val gateway = FakeRefreshSessionGateway()
    private val tokenService = FixedRefreshSessionTokenService()
    private val properties = RefreshSessionProperties(
        cookieName = "pt_refresh",
        cookiePath = "/api/auth",
        sameSite = "Lax",
        secure = false,
        ttlSeconds = 604800,
    )
    private val service = RefreshSessionLifecycleService(gateway, tokenService, properties, clock)

    @Test
    fun `issues refresh session with configured ttl`() = kotlinx.coroutines.test.runTest {
        val session = service.issue("user-1")

        assertEquals("user-1", session.principalId)
        assertEquals(now.plusSeconds(604800), session.expiresAt)
    }

    @Test
    fun `rotates and revokes using parsed token`() = kotlinx.coroutines.test.runTest {
        gateway.activeSession = ActiveRefreshSession(
            id = "session-1",
            principalId = "user-1",
            lookupKey = "lookup-1",
            tokenVerifier = "verifier",
            expiresAt = now.plusSeconds(3600),
            createdAt = now,
            lastUsedAt = null,
        )

        val rotated = service.rotate("lookup-1.secret-1")
        service.revoke("lookup-1.secret-1")

        assertEquals("session-1", rotated.previousSessionId)
        assertEquals("session-1", gateway.revokedSessionId)
    }

    private class FakeRefreshSessionGateway : RefreshSessionGateway {
        var activeSession: ActiveRefreshSession? = null
        var revokedSessionId: String? = null

        override suspend fun create(
            principalId: String,
            refreshToken: RefreshSessionToken,
            expiresAt: Instant,
        ): CreatedRefreshSession = CreatedRefreshSession(
            id = "created-session",
            principalId = principalId,
            refreshToken = refreshToken,
            expiresAt = expiresAt,
        )

        override suspend fun requireActive(refreshToken: RefreshSessionToken, now: Instant): ActiveRefreshSession =
            activeSession ?: error("No active session configured")

        override suspend fun rotate(
            currentSessionId: String,
            replacementToken: RefreshSessionToken,
            expiresAt: Instant,
            now: Instant,
        ): CreatedRefreshSession = CreatedRefreshSession(
            id = "rotated-session",
            principalId = "user-1",
            refreshToken = replacementToken,
            expiresAt = expiresAt,
        )

        override suspend fun revoke(currentSessionId: String, now: Instant) {
            revokedSessionId = currentSessionId
        }
    }

    private class FixedRefreshSessionTokenService : RefreshSessionTokenService() {
        private var counter = 0

        override fun issue(): RefreshSessionToken {
            counter += 1
            return RefreshSessionToken("lookup-$counter", "secret-$counter")
        }
    }
}
