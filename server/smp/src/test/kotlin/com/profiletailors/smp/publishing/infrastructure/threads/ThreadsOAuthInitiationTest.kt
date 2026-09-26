package com.profiletailors.smp.publishing.infrastructure.threads

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.profiletailors.smp.publishing.domain.OAuthStatePayload
import com.profiletailors.smp.publishing.domain.OAuthStateReplayStore
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.linkedin.HmacOAuthStateSigner
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ThreadsOAuthInitiationTest {
    private val replayStore = mockk<OAuthStateReplayStore>(relaxed = true)
    private val clock = Clock.fixed(Instant.parse("2026-09-24T12:00:00Z"), ZoneOffset.UTC)
    private val objectMapper = ObjectMapper().findAndRegisterModules().registerModule(JavaTimeModule())
    private val properties = ThreadsPublishingProperties(
        enabled = true,
        clientId = "threads-client",
        clientSecret = "threads-secret",
        redirectUri = "https://app.example.com/integrations/threads/callback",
    )

    @Test
    fun `builds Threads authorization URL with approved scopes and no PKCE`() {
        val state = "signed-state"

        val url = ThreadsAuthorizationUrlBuilder(properties).buildAuthorizationUrl(
            state = state,
            redirectUri = properties.redirectUri,
        )
        assertEquals("threads.net", URI.create(url).host)
        val query = URI.create(url).rawQuery.split('&').associate {
            it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), StandardCharsets.UTF_8)
        }

        assertEquals("threads-client", query["client_id"])
        assertEquals("code", query["response_type"])
        assertEquals(properties.requiredScopes.joinToString(","), query["scope"])
        assertEquals(state, query["state"])
        assertEquals(properties.redirectUri, query["redirect_uri"])
        assertFalse(query.containsKey("code_challenge"))
        assertFalse(query.containsKey("code_challenge_method"))
    }

    @Test
    fun `signed state binds provider workspace principal redirect nonce and expiry`() {
        val payload = OAuthStatePayload(
            provider = SocialProvider.THREADS,
            workspaceId = "workspace-1",
            principalId = "principal-1",
            redirectUri = properties.redirectUri,
            nonce = "nonce-1",
            issuedAt = clock.instant(),
            expiresAt = clock.instant().plusSeconds(600),
        )

        val signer = HmacOAuthStateSigner("unit-secret-with-enough-entropy", objectMapper, clock, replayStore)
        val verified = signer.verify(signer.sign(payload))

        assertEquals(payload, verified)
        assertTrue(verified.expiresAt.isAfter(verified.issuedAt))
    }
}
