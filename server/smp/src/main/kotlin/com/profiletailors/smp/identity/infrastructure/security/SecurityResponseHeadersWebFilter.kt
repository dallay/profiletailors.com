package com.profiletailors.smp.identity.infrastructure.security

import org.springframework.http.HttpHeaders
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * Adds baseline browser hardening headers to every backend response.
 */
class SecurityResponseHeadersWebFilter : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        exchange.response.beforeCommit {
            val headers = exchange.response.headers
            headers.putIfAbsent("Content-Security-Policy", listOf(CONTENT_SECURITY_POLICY))
            headers.putIfAbsent("X-Frame-Options", listOf("DENY"))
            headers.putIfAbsent("X-Content-Type-Options", listOf("nosniff"))
            headers.putIfAbsent("Referrer-Policy", listOf("strict-origin-when-cross-origin"))
            headers.putIfAbsent("Permissions-Policy", listOf(PERMISSIONS_POLICY))
            if (isSecureRequest(exchange)) {
                headers.putIfAbsent("Strict-Transport-Security", listOf(STRICT_TRANSPORT_SECURITY))
            }
            Mono.empty<Void>()
        }
        return chain.filter(exchange)
    }

    private fun isSecureRequest(exchange: ServerWebExchange): Boolean {
        val forwardedProto = exchange.request.headers.getFirst("X-Forwarded-Proto")
            ?.substringBefore(',')
            ?.trim()
        return forwardedProto.equals("https", ignoreCase = true) ||
            exchange.request.uri.scheme.equals("https", ignoreCase = true)
    }

    private fun HttpHeaders.putIfAbsent(name: String, value: List<String>) {
        if (this[name] == null) {
            put(name, value)
        }
    }

    private companion object {
        private const val CONTENT_SECURITY_POLICY =
            "default-src 'self'; base-uri 'none'; frame-ancestors 'none'; object-src 'none'"
        private const val PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=()"
        private const val STRICT_TRANSPORT_SECURITY = "max-age=31536000; includeSubDomains; preload"
    }
}
