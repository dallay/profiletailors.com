package com.profiletailors.smp.mcp.infrastructure

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
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * Minimal security chain for the MCP endpoint shipped in PR 1.
 *
 * Scope: this class exists solely so the PR 1 acceptance test can prove the
 * MCP endpoint exists and rejects unauthenticated traffic with `401
 * Unauthorized` plus a placeholder `WWW-Authenticate: Bearer …` header. PR 2
 * will replace this bean with the full resource-server chain — JWT audience +
 * `workspace_id` validation, plus the RFC 9728 `resource_metadata` URL in the
 * entry point.
 *
 * Why is this NOT a WebFilter? Spring AI's transport mounts at the route
 * declared by `spring.ai.mcp.server.streamable-http.mcp-endpoint`. Routing
 * auth at that path through `SecurityWebFilterChain` keeps the body-parsing
 * invariant intact: the security chain never inspects the JSON-RPC body.
 */
@Configuration
@ConditionalOnProperty(
    prefix = "spring.ai.mcp.server",
    name = ["enabled"],
    havingValue = "true",
)
internal class McpSecurityConfiguration {

    private val mcpPathMatcher: ServerWebExchangeMatcher =
        ServerWebExchangeMatchers.pathMatchers("/api/mcp", "/api/mcp/**")

    private val cookiePresentMatcher = ServerWebExchangeMatcher { exchange ->
        if (exchange.request.headers.getFirst(HttpHeaders.COOKIE).isNullOrBlank()) {
            ServerWebExchangeMatcher.MatchResult.notMatch()
        } else {
            ServerWebExchangeMatcher.MatchResult.match()
        }
    }

    private val placeholderWwwAuthenticateFilter: WebFilter = PlaceholderWwwAuthenticateFilter()

    /**
     * Configures the security filter chain for MCP endpoints.
     *
     * @param http The HTTP security configuration to customize.
     * @return The configured security filter chain.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 50)
    fun mcpSecurityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain = http
        .securityMatcher(mcpPathMatcher)
        // MCP clients authenticate with an Authorization Bearer token; this chain does not use
        // browser cookies. Requests that carry cookies retain CSRF protection as defense in depth.
        .csrf { it.requireCsrfProtectionMatcher(cookiePresentMatcher) }
        .authorizeExchange { it.anyExchange().authenticated() }
        .exceptionHandling { exceptions ->
            // PR 1 emits a placeholder Bearer challenge. PR 2 replaces this with
            // the full Bearer challenge carrying the RFC 9728 resource_metadata URL.
            exceptions.authenticationEntryPoint(HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED))
        }
        .addFilterBefore(placeholderWwwAuthenticateFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        .build()

    /**
     * Stamps `WWW-Authenticate: Bearer realm="mcp"` onto every 401 response
     * from the chain. Runs after the entry point, so the header is appended
     * just before the response is committed.
     */
    private class PlaceholderWwwAuthenticateFilter : WebFilter {
        /**
         * Applies the filter chain and adds MCP authentication response headers to unauthorized responses
         * that do not already include a `WWW-Authenticate` header.
         *
         * @param exchange The current server exchange.
         * @param chain The remaining web filter chain.
         * @return A completion signal for the filter operation.
         */
        override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> =
            chain.filter(exchange).then(
                Mono.fromRunnable {
                    if (exchange.response.statusCode == HttpStatus.UNAUTHORIZED &&
                        exchange.response.headers.getFirst("WWW-Authenticate").isNullOrEmpty()
                    ) {
                        exchange.response.headers.add("WWW-Authenticate", "Bearer realm=\"mcp\"")
                        exchange.response.headers.contentType = MediaType.APPLICATION_JSON
                    }
                },
            )
    }
}
