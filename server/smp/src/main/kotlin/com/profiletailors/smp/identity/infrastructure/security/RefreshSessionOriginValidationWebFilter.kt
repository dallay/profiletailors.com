package com.profiletailors.smp.identity.infrastructure.security

import com.profiletailors.smp.credentials.application.RefreshSessionProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.net.URI

/**
 * Protects cookie-authenticated refresh/logout endpoints from cross-site request forgery.
 *
 * Requests that present the refresh-session cookie must also present a trusted Origin/Referer.
 */
class RefreshSessionOriginValidationWebFilter(
    private val corsProperties: CorsConfigurationProperties,
    private val refreshSessionProperties: RefreshSessionProperties,
) : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val request = exchange.request
        if (!requiresOriginValidation(request.method, request.path.pathWithinApplication().value(), request.headers)) {
            return chain.filter(exchange)
        }

        val sourceOrigin = extractSourceOrigin(request.headers)
        return if (sourceOrigin != null && isTrustedOrigin(sourceOrigin, exchange)) {
            chain.filter(exchange)
        } else {
            exchange.response.statusCode = HttpStatus.FORBIDDEN
            exchange.response.setComplete()
        }
    }

    private fun requiresOriginValidation(method: HttpMethod?, path: String, headers: HttpHeaders): Boolean {
        if (method != HttpMethod.POST || path !in protectedPaths) return false
        return headers.getOrEmpty(HttpHeaders.COOKIE).any {
            it.contains("${refreshSessionProperties.cookieName}=", ignoreCase = false)
        }
    }

    private fun extractSourceOrigin(headers: HttpHeaders): String? {
        headers.getFirst(HttpHeaders.ORIGIN)
            ?.takeIf(String::isNotBlank)
            ?.let(::normalizeOrigin)
            ?.let { return it }

        val referer = headers.getFirst(HttpHeaders.REFERER)?.takeIf(String::isNotBlank) ?: return null
        val refererUri = runCatching { URI.create(referer) }.getOrNull() ?: return null
        val scheme = refererUri.scheme ?: return null
        val host = refererUri.host ?: return null
        val port = refererUri.port
        return if (port >= 0) "$scheme://$host:$port" else "$scheme://$host"
    }

    private fun isTrustedOrigin(origin: String, exchange: ServerWebExchange): Boolean {
        val requestOrigin = requestOrigin(exchange)
        if (requestOrigin != null && origin == requestOrigin) return true
        return allowedOrigins.contains(origin)
    }

    private fun requestOrigin(exchange: ServerWebExchange): String? {
        val forwardedProto = exchange.request.headers.getFirst("X-Forwarded-Proto")
            ?.substringBefore(',')
            ?.trim()
            ?.takeIf(String::isNotBlank)
        val forwardedHost = exchange.request.headers.getFirst("X-Forwarded-Host")
            ?.substringBefore(',')
            ?.trim()
            ?.takeIf(String::isNotBlank)
        val host = forwardedHost ?: exchange.request.headers.host?.toString() ?: return null
        val scheme = forwardedProto ?: exchange.request.uri.scheme ?: return null
        return normalizeOrigin("$scheme://$host")
    }

    private fun normalizeOrigin(value: String): String? {
        val uri = runCatching { URI.create(value) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val port = uri.port
        return if (port >= 0) "$scheme://$host:$port" else "$scheme://$host"
    }

    private val allowedOrigins: Set<String>
        get() = corsProperties.allowedOrigins.asSequence()
            .mapNotNull(::normalizeOrigin)
            .toSet()

    private companion object {
        val protectedPaths = setOf(
            "/api/auth/refresh",
            "/api/auth/logout",
        )
    }
}
