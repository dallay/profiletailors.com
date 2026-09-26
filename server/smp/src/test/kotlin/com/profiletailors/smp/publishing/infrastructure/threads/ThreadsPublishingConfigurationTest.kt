package com.profiletailors.smp.publishing.infrastructure.threads

import com.profiletailors.smp.publishing.domain.SocialProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThreadsPublishingConfigurationTest {
    @Test
    fun `threads authorization registry exposes Threads builder`() {
        val properties = ThreadsPublishingProperties(
            enabled = true,
            clientId = "client",
            clientSecret = "secret",
            redirectUri = "https://app.example.com/callback",
        )
        val builder = ThreadsAuthorizationUrlBuilder(properties)
        val registry = com.profiletailors.smp.publishing.domain.ProviderAuthorizationRegistry.from(
            SocialProvider.THREADS to builder,
        )

        assertEquals(builder, registry.builder(SocialProvider.THREADS))
    }
}
