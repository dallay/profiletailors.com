package com.profiletailors.smp.identity.infrastructure.security

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight in-process rate limit for authentication endpoints.
 *
 * Prevents credential stuffing / brute-force against login, register, refresh,
 * resend-verification, and verify-email. Limits are per remote socket address.
 *
 * This is intentionally independent of the optional `shared:shield:ratelimit` module
 * so SMP always has auth abuse protection without requiring extra wiring.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class AuthRateLimitWebFilter : WebFilter {

    private val windows = ConcurrentHashMap<String, Window>()

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.pathWithinApplication().value().trimEnd('/')
        if (!isAuthEndpoint(path)) {
            return chain.filter(exchange)
        }

        val identifier = clientIdentifier(exchange)
        val now = System.currentTimeMillis()
        val window = windows.compute(identifier) { _, existing ->
            when {
                existing == null || now - existing.startedAtMs >= WINDOW_MS -> {
                    Window(startedAtMs = now, count = AtomicInteger(1))
                }

                else -> {
                    existing.count.incrementAndGet()
                    existing
                }
            }
        }!!

        if (window.count.get() > MAX_REQUESTS_PER_WINDOW) {
            return reject(exchange, window)
        }
        return chain.filter(exchange)
    }

    private fun isAuthEndpoint(path: String): Boolean = AUTH_ENDPOINTS.any { path == it || path.startsWith("$it/") }

    private fun clientIdentifier(exchange: ServerWebExchange): String {
        val remote = exchange.request.remoteAddress?.address?.hostAddress
            ?.replace(IP_SANITIZE_REGEX, "")
            ?.take(MAX_IP_LENGTH)
        return "auth-ip:${remote ?: "unknown"}"
    }

    private fun reject(exchange: ServerWebExchange, window: Window): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.TOO_MANY_REQUESTS
        response.headers.contentType = MediaType.APPLICATION_PROBLEM_JSON
        val retryAfterSeconds = ((window.startedAtMs + WINDOW_MS - System.currentTimeMillis()) / MILLIS_PER_SECOND)
            .coerceAtLeast(1)
        response.headers.set("Retry-After", retryAfterSeconds.toString())
        val body =
            """{"title":"Too Many Requests","status":429,"detail":"Authentication rate limit exceeded. Try again later."}"""
        val buffer = response.bufferFactory().wrap(body.toByteArray(Charsets.UTF_8))
        return response.writeWith(Mono.just(buffer))
    }

    private data class Window(val startedAtMs: Long, val count: AtomicInteger)

    private companion object {
        const val MAX_REQUESTS_PER_WINDOW = 20
        const val WINDOW_MS = 60_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val MAX_IP_LENGTH = 64
        val IP_SANITIZE_REGEX = Regex("[^A-Za-z0-9.:-]")
        val AUTH_ENDPOINTS = setOf(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/resend-verification",
            "/api/auth/verify-email",
        )
    }
}
