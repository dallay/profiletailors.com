package com.profiletailors.smp.publishing.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.platform.infrastructure.http.WebFluxConfiguration
import com.profiletailors.smp.publishing.application.InitiateProviderConnectionCommand
import com.profiletailors.smp.publishing.application.ProviderConnectionInitiationResult
import com.profiletailors.smp.publishing.domain.SocialProvider
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant

class PublishingProviderPathBindingTest {
    private val mediator = mockk<Mediator>()
    private val client = WebTestClient.bindToController(PublishingConnectionController(mediator))
        .formatters { PublishingWebFluxConfiguration().addFormatters(it) }
        .apiVersioning {
            it.useVersionResolver(WebFluxConfiguration.MediaTypeVersionResolver())
                .setDefaultVersion("1")
        }
        .build()

    @Test
    fun `binds lowercase Threads path to provider-aware initiation`() {
        val commandSlot = slot<InitiateProviderConnectionCommand>()
        coEvery { mediator.send(capture(commandSlot)) } returns ProviderConnectionInitiationResult(
            authorizationUrl = "https://threads.net/oauth/authorize",
            state = "state",
            expiresAt = Instant.parse("2026-09-28T15:00:00Z"),
        )

        client.post()
            .uri("/api/publishing/threads/connections/initiate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"redirectUri":"https://app.example.com/integrations/threads/callback"}""")
            .exchange()
            .expectStatus().isOk

        assertEquals(
            InitiateProviderConnectionCommand(
                provider = SocialProvider.THREADS,
                redirectUri = "https://app.example.com/integrations/threads/callback",
            ),
            commandSlot.captured,
        )
    }
}
