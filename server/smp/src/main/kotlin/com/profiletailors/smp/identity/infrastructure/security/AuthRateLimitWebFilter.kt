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

    /**
     * Applies authentication request rate limiting and delegates non-authentication requests unchanged.
     *
     * @param exchange The current web exchange.
     * @param chain The filter chain for continuing request processing.
     * @return Completion signal for the request processing.
     */
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.pathWithinApplication().value().trimEnd('/')
        if (!isAuthEndpoint(path)) {
            return chain.filter(exchange)
        }

        val identifier = clientIdentifier(exchange)
        val now = currentTimeMillis()
        if (windows.size >= maxTrackedWindows) {
            evictExpiredEntries(now)
        }
        val window = requireNotNull(
            windows.compute(identifier) { _, existing ->
                if (existing == null || now - existing.startedAtMs >= WINDOW_MS) {
                    Window(startedAtMs = now, count = AtomicInteger(1))
                } else {
                    existing.count.incrementAndGet()
                    existing
                }
            },
        ) { "Rate-limit window must be present after compute()." }

        if (window.count.get() > MAX_REQUESTS_PER_WINDOW) {
            return reject(exchange, window, now)
        }
        return chain.filter(exchange)
    }

    private fun isAuthEndpoint(path: String): Boolean = AUTH_ENDPOINTS.any { path == it || path.startsWith("$it/") }

    /**
     * Creates a sanitized, length-limited identifier for the client's remote address.
     *
     * @param exchange The web exchange containing the client's remote address.
     * @return An identifier prefixed with `auth-ip:`, using `unknown` when the address is unavailable.
     */
    private fun clientIdentifier(exchange: ServerWebExchange): String {
        val remote = exchange.request.remoteAddress?.address?.hostAddress
            ?.replace(IP_SANITIZE_REGEX, "")
            ?.take(MAX_IP_LENGTH)
        return "auth-ip:${remote ?: "unknown"}"
    }

    /**
     * Removes tracked rate-limit windows that have reached the configured window duration.
     *
     * @param now The current time in milliseconds.
     */
    private fun evictExpiredEntries(now: Long) {
        val iterator = windows.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.startedAtMs >= WINDOW_MS) {
                iterator.remove()
            }
        }
    }

    /**
     * Configures and writes the authentication rate-limit response.
     *
     * @param exchange The current server exchange.
     * @param window The active rate-limit window.
     * @param now The current time in milliseconds.
     * @return Completion after the response body has been written.
     */
    @Suppress("S6508") // Mono<Void> is correct Reactor idiom for completion-without-value
    private fun reject(exchange: ServerWebExchange, window: Window, now: Long): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.TOO_MANY_REQUESTS
        response.headers.contentType = MediaType.APPLICATION_PROBLEM_JSON
        val retryAfterSeconds = ((window.startedAtMs + WINDOW_MS - now) / MILLIS_PER_SECOND)
            .coerceAtLeast(1L)
        response.headers.set("Retry-After", retryAfterSeconds.toString())
        val body =
            """{"title":"Too Many Requests","status":429,"detail":"Authentication rate limit exceeded. Try again later."}"""
        val buffer = response.bufferFactory().wrap(body.toByteArray(Charsets.UTF_8))
        return response.writeWith(Mono.just(buffer)).then()
    }

    /**
 * Reports the number of currently tracked client windows.
 *
 * @return The number of tracked windows.
 */
internal fun trackedWindowCount(): Int = windows.size

    /**
 * Gets the current time in milliseconds from the configured clock.
 *
 * @return The current time in milliseconds.
 */
private fun currentTimeMillis(): Long = clock.millis()

    private data class Window(val startedAtMs: Long, val count: AtomicInteger)

    private companion object {
        const val MAX_REQUESTS_PER_WINDOW = 20
        const val WINDOW_MS = 60_000L
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
        )
    }
}
