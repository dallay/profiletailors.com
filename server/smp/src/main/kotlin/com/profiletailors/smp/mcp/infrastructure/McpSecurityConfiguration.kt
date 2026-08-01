package com.profiletailors.smp.mcp.infrastructure

import com.profiletailors.smp.mcp.infrastructure.security.McpJwtConverter
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * Security configuration for the MCP API.
 *
 * Configures JWT-based authentication for the MCP endpoint with:
 * - Audience validation (`https://api.profiletailors.com/api/mcp`)
 * - workspace_id claim extraction
 * - RFC 9728 metadata endpoint exemption (public endpoint)
 * - 401 responses with WWW-Authenticate header when JWT is missing or invalid
 */
@Configuration
@ConditionalOnProperty(
    prefix = "spring.ai.mcp.server",
    name = ["enabled"],
    havingValue = "true",
)
internal class McpSecurityConfiguration(
    @Value("\${spring.ai.mcp.server.streamable-http.mcp-endpoint:/api/mcp}")
    private val mcpEndpoint: String,
) {

    private val mcpPathMatcher: ServerWebExchangeMatcher =
        ServerWebExchangeMatchers.pathMatchers(mcpEndpoint, "$mcpEndpoint/**")

    private val cookiePresentMatcher = ServerWebExchangeMatcher { exchange ->
        if (exchange.request.headers.getFirst(HttpHeaders.COOKIE).isNullOrBlank()) {
            ServerWebExchangeMatcher.MatchResult.notMatch()
        } else {
            ServerWebExchangeMatcher.MatchResult.match()
        }
    }

    private val rfc9728MetadataMatcher: ServerWebExchangeMatcher =
        ServerWebExchangeMatchers.pathMatchers(
            "/.well-known/oauth-protected-resource",
            "/.well-known/oauth-protected-resource/**",
        )

    private val mcpJwtPresenceFilter: WebFilter = McpJwtPresenceFilter()



    /**
     * Configures the security filter chain for MCP endpoints.
     *
     * @param http The HTTP security configuration to customize.
     * @return The configured security filter chain.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 50)
    fun mcpSecurityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain = http
        .securityMatcher(
            OrServerWebExchangeMatcher(mcpPathMatcher, rfc9728MetadataMatcher),
        )
        // MCP clients authenticate with an Authorization Bearer token; this chain does not use
        // browser cookies. Requests that carry cookies retain CSRF protection as defense in depth.
        .csrf { it.requireCsrfProtectionMatcher(cookiePresentMatcher) }
        .authorizeExchange {
            it.matchers(rfc9728MetadataMatcher).permitAll()
                .anyExchange().authenticated()
        }
        .oauth2ResourceServer { oauth2 ->
            oauth2.jwt { jwt ->
                jwt.jwtAuthenticationConverter(
                    ReactiveJwtAuthenticationConverterAdapter(McpJwtConverter()),
                )
            }
        }
        .exceptionHandling { exceptions ->
            exceptions.authenticationEntryPoint(HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED))
        }
        .addFilterBefore(mcpJwtPresenceFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        .build()

    /**
     * Returns 401 with WWW-Authenticate header when JWT is missing.
     *
     * Runs at HIGHEST_PRECEDENCE + 40 to intercept before JWT processing.
     */
    private class McpJwtPresenceFilter : WebFilter {
        override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
            val path = exchange.request.uri.path

            // Skip JWT presence check for RFC 9728 metadata endpoint (public)
            if (path.startsWith("/.well-known/oauth-protected-resource")) {
                return chain.filter(exchange)
            }

            val authHeader = exchange.request.headers.getFirst("Authorization")

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                exchange.response.statusCode = HttpStatus.UNAUTHORIZED
                exchange.response.headers.add(
                    "WWW-Authenticate",
                    """Bearer realm="mcp", resource="https://api.profiletailors.com/api/mcp"""",
                )
                exchange.response.headers.contentType = MediaType.APPLICATION_JSON
                return exchange.response.setComplete()
            }

            return chain.filter(exchange)
        }
    }
}
