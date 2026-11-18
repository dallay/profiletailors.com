package com.profiletailors.smp.identity.infrastructure.security

import org.springframework.http.HttpStatus
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class MixedAuthenticationCredentialsWebFilter : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val hasSessionCookie = exchange.request.cookies.containsKey(
            IdentitySecurityConfiguration.BFF_SESSION_COOKIE_NAME,
        )
        val hasBearerCredential = exchange.request.headers.getFirst(AUTHORIZATION_HEADER)
            ?.startsWith(BEARER_PREFIX, ignoreCase = true) == true
        val hasApiKeyCredential = exchange.request.headers.getFirst(API_KEY_HEADER)?.isNotBlank() == true

        if (hasSessionCookie && (hasBearerCredential || hasApiKeyCredential)) {
            exchange.response.statusCode = HttpStatus.UNAUTHORIZED
            return exchange.response.setComplete()
        }

        return chain.filter(exchange)
    }

    private companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        const val API_KEY_HEADER = "X-API-Key"
        const val BEARER_PREFIX = "Bearer "
    }
}
