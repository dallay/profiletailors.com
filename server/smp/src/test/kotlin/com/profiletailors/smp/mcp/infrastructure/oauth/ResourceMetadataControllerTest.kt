package com.profiletailors.smp.mcp.infrastructure.oauth

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * TDD test for RFC 9728 Resource Metadata endpoint.
 *
 * This test drives the implementation of the public
 * `/.well-known/oauth-protected-resource/api/mcp` endpoint that returns
 * OAuth resource metadata WITHOUT requiring authentication.
 */
@Tag("fast")
class ResourceMetadataControllerTest {

    private val webTestClient = WebTestClient
        .bindToController(ResourceMetadataController())
        .build()

    @Test
    fun `GET well-known oauth-protected-resource api-mcp returns RFC 9728 JSON`() {
        webTestClient.get()
            .uri("/.well-known/oauth-protected-resource/api/mcp")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.resource").isEqualTo("https://api.profiletailors.com/api/mcp")
            .jsonPath("$.authorization_servers[0]").isEqualTo("https://auth.profiletailors.com/realms/profiletailors")
            .jsonPath("$.scopes_supported[0]").isEqualTo("mcp:channels:read")
            .jsonPath("$.scopes_supported[1]").isEqualTo("mcp:publications:read")
            .jsonPath("$.bearer_methods_supported[0]").isEqualTo("header")
    }

    @Test
    fun `GET well-known oauth-protected-resource root path returns same JSON`() {
        webTestClient.get()
            .uri("/.well-known/oauth-protected-resource")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.resource").isEqualTo("https://api.profiletailors.com/api/mcp")
    }

    @Test
    fun `endpoint is publicly accessible without authentication`() {
        // No Authorization header — should still return 200
        webTestClient.get()
            .uri("/.well-known/oauth-protected-resource/api/mcp")
            .exchange()
            .expectStatus().isOk
    }
}
