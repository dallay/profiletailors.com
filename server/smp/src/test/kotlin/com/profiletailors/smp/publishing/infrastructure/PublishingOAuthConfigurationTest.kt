package com.profiletailors.smp.publishing.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.publishing.domain.OAuthStatePayload
import com.profiletailors.smp.publishing.domain.OAuthStateSigner
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.linkedin.HmacOAuthStateSigner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PublishingOAuthConfigurationTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(PublishingOAuthConfiguration::class.java)
        .withBean(ObjectMapper::class.java, { ObjectMapper() })
        .withBean(Clock::class.java, { clock })

    @Test
    fun `Threads can sign and complete OAuth without LinkedIn configuration`() {
        contextRunner.withPropertyValues(
            "publishing.threads.enabled=true",
            "publishing.oauth.state-signing-secret=shared-oauth-signing-secret",
        ).run { context ->
            assertEquals(1, context.getBeansOfType(OAuthStateSigner::class.java).size)
            val signer = context.getBean(OAuthStateSigner::class.java)
            val payload = payload(SocialProvider.THREADS)
            assertEquals(payload, signer.verify(signer.sign(payload)))
        }
    }

    @Test
    fun `legacy LinkedIn signing secret remains supported`() {
        contextRunner.withPropertyValues(
            "publishing.linkedin.state-signing-secret=legacy-oauth-signing-secret",
        ).run { context ->
            val signer = context.getBean(OAuthStateSigner::class.java)
            val legacySigner = HmacOAuthStateSigner("legacy-oauth-signing-secret", ObjectMapper(), clock)
            val payload = payload(SocialProvider.LINKEDIN)
            assertEquals(payload, signer.verify(legacySigner.sign(payload)))
        }
    }

    @Test
    fun `shared signing secret takes precedence for both providers`() {
        contextRunner.withPropertyValues(
            "publishing.oauth.state-signing-secret=shared-oauth-signing-secret",
            "publishing.linkedin.state-signing-secret=legacy-oauth-signing-secret",
        ).run { context ->
            val signer = context.getBean(OAuthStateSigner::class.java)
            val sharedSigner = HmacOAuthStateSigner("shared-oauth-signing-secret", ObjectMapper(), clock)
            listOf(SocialProvider.THREADS, SocialProvider.LINKEDIN).forEach { provider ->
                val payload = payload(provider)
                assertEquals(payload, signer.verify(sharedSigner.sign(payload)))
            }
        }
    }

    private fun payload(provider: SocialProvider) = OAuthStatePayload(
        provider = provider,
        workspaceId = "workspace-1",
        principalId = "principal-1",
        redirectUri = "https://app.example.com/callback",
        nonce = "nonce-${provider.name}",
        issuedAt = now,
        expiresAt = now.plusSeconds(300),
    )
}
