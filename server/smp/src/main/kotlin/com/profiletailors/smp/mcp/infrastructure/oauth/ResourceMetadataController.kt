package com.profiletailors.smp.mcp.infrastructure.oauth

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * RFC 9728 OAuth 2.0 Protected Resource Metadata endpoint.
 *
 * Publicly accessible endpoint (no authentication required) that advertises
 * the OAuth 2.0 protected resource metadata for the MCP API.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9728.html">RFC 9728</a>
 */
@RestController
class ResourceMetadataController {

    @GetMapping(
        "/.well-known/oauth-protected-resource/api/mcp",
        "/.well-known/oauth-protected-resource",
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    fun getResourceMetadata(): Mono<ResourceMetadata> = Mono.just(
        ResourceMetadata(
            resource = "https://api.profiletailors.com/api/mcp",
            authorizationServers = listOf("https://auth.profiletailors.com/realms/profiletailors"),
            scopesSupported = listOf("mcp:channels:read", "mcp:publications:read"),
            bearerMethodsSupported = listOf("header"),
        ),
    )
}

/**
 * RFC 9728 Resource Metadata response.
 */
data class ResourceMetadata(
    val resource: String,
    @JsonProperty("authorization_servers")
    val authorizationServers: List<String>,
    @JsonProperty("scopes_supported")
    val scopesSupported: List<String>,
    @JsonProperty("bearer_methods_supported")
    val bearerMethodsSupported: List<String>,
)
