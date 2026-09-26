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
    fun `enabled threads accepts only approved scopes and safe redirect`() {
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
    fun `enabled threads rejects unsupported scopes and invalid workflow budget`() {
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
