package com.profiletailors.smp.publishing.infrastructure.threads

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.profiletailors.smp.publishing.domain.OAuthStatePayload
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.linkedin.HmacOAuthStateSigner
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ThreadsOAuthInitiationTest {
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
        val uri = URI.create(url)
        uri.host shouldBe "threads.com"
        uri.path shouldBe "/oauth/authorize"
        uri.scheme shouldBe "https"
        val query = uri.rawQuery.split('&').associate {
            it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), StandardCharsets.UTF_8)
        }

        query["client_id"] shouldBe "threads-client"
        query["response_type"] shouldBe "code"
        query["scope"] shouldBe properties.requiredScopes.joinToString(",")
        query["state"] shouldBe state
        query["redirect_uri"] shouldBe properties.redirectUri
        query.containsKey("code_challenge") shouldBe false
        query.containsKey("code_challenge_method") shouldBe false
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

        val signer = HmacOAuthStateSigner("unit-secret-with-enough-entropy", objectMapper, clock)
        val verified = signer.verify(signer.sign(payload))

        verified shouldBe payload
        verified.expiresAt.isAfter(verified.issuedAt) shouldBe true
    }
}
