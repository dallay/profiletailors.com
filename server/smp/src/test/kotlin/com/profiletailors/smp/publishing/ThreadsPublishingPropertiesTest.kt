package com.profiletailors.smp.publishing

import com.profiletailors.smp.publishing.infrastructure.threads.ThreadsPublishingProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration

class ThreadsPublishingPropertiesTest {
    @Test
    fun `disabled threads remains unconfigured and does not fail validation`() {
        val properties = ThreadsPublishingProperties()

        properties.isConfigured() shouldBe false
        properties.validate()
    }

    @Test
    fun `enabled threads rejects incomplete configuration`() {
        val properties = ThreadsPublishingProperties(enabled = true)

        properties.isConfigured() shouldBe false
        shouldThrow<IllegalStateException> { properties.validate() }
    }

    @Test
    fun `should accept only approved scopes and safe redirects when threads is enabled`() {
        val properties = ThreadsPublishingProperties(
            enabled = true,
            clientId = "client-id",
            clientSecret = "client-secret",
            redirectUri = "https://app.example.test/integrations/threads/callback",
        )

        properties.isConfigured() shouldBe true
        properties.isAllowedRedirectUri(properties.redirectUri) shouldBe true
        properties.isAllowedRedirectUri("https://attacker.example.test/callback") shouldBe false
    }

    @Test
    fun `should reject non-https oauth endpoints when threads is enabled`() {
        val baseProperties = ThreadsPublishingProperties(
            enabled = true,
            clientId = "client-id",
            clientSecret = "client-secret",
            redirectUri = "https://app.example.test/integrations/threads/callback",
        )

        baseProperties.copy(authorizationBaseUrl = "http://threads.example/oauth/authorize").isConfigured() shouldBe
            false
        baseProperties.copy(tokenExchangeUrl = "http://threads.example/oauth/access_token").isConfigured() shouldBe
            false
        baseProperties.copy(longLivedTokenUrl = "http://threads.example/access_token").isConfigured() shouldBe false
    }

    @Test
    fun `should reject oauth endpoints with query components when threads is enabled`() {
        val baseProperties = ThreadsPublishingProperties(
            enabled = true,
            clientId = "client-id",
            clientSecret = "client-secret",
            redirectUri = "https://app.example.test/integrations/threads/callback",
        )

        baseProperties.copy(
            authorizationBaseUrl = "https://threads.example/oauth/authorize?mode=test",
        ).isConfigured() shouldBe
            false
        baseProperties.copy(
            tokenExchangeUrl = "https://threads.example/oauth/access_token?mode=test",
        ).isConfigured() shouldBe
            false
        baseProperties.copy(
            longLivedTokenUrl = "https://threads.example/access_token?mode=test",
        ).isConfigured() shouldBe
            false
    }

    @Test
    fun `should reject unsupported scopes and invalid workflow budgets when threads is enabled`() {
        val invalidScopes = ThreadsPublishingProperties(
            enabled = true,
            clientId = "client-id",
            clientSecret = "client-secret",
            redirectUri = "https://app.example.test/integrations/threads/callback",
            requiredScopes = setOf("threads_basic"),
        )
        val invalidBudget = invalidScopes.copy(
            requiredScopes = ThreadsPublishingProperties.APPROVED_SCOPES,
            mediaUrlTtl = Duration.ofSeconds(1),
        )

        invalidScopes.isConfigured() shouldBe false
        invalidBudget.isConfigured() shouldBe false
    }
}
