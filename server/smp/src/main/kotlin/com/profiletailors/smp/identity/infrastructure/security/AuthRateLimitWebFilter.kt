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
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight in-process rate limit for authentication endpoints.
 *
 * Prevents credential stuffing / brute-force against login, register, refresh,
 * resend-verification, and verify-email. Limits are per remote socket address.
 *
 * Windows are stored in an in-process map with opportunistic eviction to prevent
 * unbounded growth from many distinct remote addresses. When the live identifier
 * count reaches [maxTrackedWindows], expired entries are removed before a new
 * request is admitted.
 *
 * This filter is intentionally independent of the optional `shared:shield:ratelimit`
 * module so SMP always has auth abuse protection without requiring extra wiring.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = ["app.security.auth-rate-limit.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class AuthRateLimitWebFilter internal constructor(
    private val clock: Clock = Clock.systemUTC(),
    private val maxTrackedWindows: Int = MAX_TRACKED_WINDOWS,
) : WebFilter {

    private val windows = ConcurrentHashMap<String, Window>()

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.pathWithinApplication().value().trimEnd('/')
        if (!isAuthEndpoint(path)) {
            return chain.filter(exchange)
        }

        val policy = policyFor(path)
        val identifier = "${policy.bucket}:${clientIdentifier(exchange)}"
        val now = currentTimeMillis()
        if (windows.size >= maxTrackedWindows) {
            evictExpiredEntries(now)
        }
        val window = requireNotNull(
            windows.compute(identifier) { _, existing ->
                if (existing == null || now - existing.startedAtMs >= policy.windowMs) {
                    Window(startedAtMs = now, count = AtomicInteger(1), windowMs = policy.windowMs)
                } else {
                    existing.count.incrementAndGet()
                    existing
                }
            },
        ) { "Rate-limit window must be present after compute()." }

        if (window.count.get() > policy.maxRequests) {
            return reject(exchange, window, now)
        }
        return chain.filter(exchange)
    }

    private fun isAuthEndpoint(path: String): Boolean = AUTH_ENDPOINTS.any { path == it || path.startsWith("$it/") }

    private fun clientIdentifier(exchange: ServerWebExchange): String {
        val remote = exchange.request.remoteAddress?.address?.hostAddress
            ?.replace(IP_SANITIZE_REGEX, "")
            ?.take(MAX_IP_LENGTH)
        return remote ?: "unknown"
    }

    private fun policyFor(path: String): Policy = when (path) {
        "/api/auth/forgot-password" -> Policy(
            "password-reset-request-ip",
            PASSWORD_RESET_REQUEST_MAX_REQUESTS,
            FIFTEEN_MINUTES_MS,
        )
        "/api/auth/reset-password" -> Policy(
            "password-reset-attempt-ip",
            PASSWORD_RESET_ATTEMPT_MAX_REQUESTS,
            FIFTEEN_MINUTES_MS,
        )
        else -> Policy("auth-ip", MAX_REQUESTS_PER_WINDOW, WINDOW_MS)
    }

    private fun evictExpiredEntries(now: Long) {
        val iterator = windows.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.startedAtMs >= entry.value.windowMs) {
                iterator.remove()
            }
        }
    }

    @Suppress("S6508") // Mono<Void> is correct Reactor idiom for completion-without-value
    private fun reject(exchange: ServerWebExchange, window: Window, now: Long): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.TOO_MANY_REQUESTS
        response.headers.contentType = MediaType.APPLICATION_PROBLEM_JSON
        val retryAfterSeconds = ((window.startedAtMs + window.windowMs - now) / MILLIS_PER_SECOND)
            .coerceAtLeast(1L)
        response.headers.set("Retry-After", retryAfterSeconds.toString())
        val body =
            """{"title":"Too Many Requests","status":429,"detail":"Authentication rate limit exceeded. Try again later.","code":"AUTH_RATE_LIMIT_EXCEEDED"}"""
        val buffer = response.bufferFactory().wrap(body.toByteArray(Charsets.UTF_8))
        return response.writeWith(Mono.just(buffer)).then()
    }

    internal fun trackedWindowCount(): Int = windows.size

    fun clear() = windows.clear()

    private fun currentTimeMillis(): Long = clock.millis()

    private data class Window(val startedAtMs: Long, val count: AtomicInteger, val windowMs: Long)
    private data class Policy(val bucket: String, val maxRequests: Int, val windowMs: Long)

    private companion object {
        const val MAX_REQUESTS_PER_WINDOW = 20
        const val WINDOW_MS = 60_000L
        const val FIFTEEN_MINUTES_MS = 15 * 60_000L
        const val PASSWORD_RESET_REQUEST_MAX_REQUESTS = 5
        const val PASSWORD_RESET_ATTEMPT_MAX_REQUESTS = 10
        const val MILLIS_PER_SECOND = 1_000L
        const val MAX_IP_LENGTH = 64
        const val MAX_TRACKED_WINDOWS = 4_096
        val IP_SANITIZE_REGEX = Regex("[^A-Za-z0-9.:-]")
        val AUTH_ENDPOINTS = setOf(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/resend-verification",
            "/api/auth/verify-email",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
        )
    }
}
