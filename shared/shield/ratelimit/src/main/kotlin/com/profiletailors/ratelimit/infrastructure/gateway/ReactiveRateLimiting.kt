package com.profiletailors.ratelimit.infrastructure.gateway

import com.profiletailors.ratelimit.application.RateLimitingService
import com.profiletailors.ratelimit.domain.RateLimitResult
import com.profiletailors.ratelimit.domain.RateLimitStrategy
import kotlinx.coroutines.reactor.mono
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * Adapter that exposes the [RateLimitingService] as a reactive component,
 * converting suspend function calls to [Mono] publishers.
 *
 * This component acts as an entry point for Spring WebFlux controllers or other
 * reactive clients that need to interact with the core rate limiting logic
 * without being aware of Kotlin coroutines.
 */
@Component
class ReactiveRateLimiting(private val rateLimitingService: RateLimitingService) {

    /**
     * Consumes a token for a given identifier using the default BUSINESS strategy,
     * returning a [Mono] of [RateLimitResult].
     *
     * @param identifier The identifier to rate limit (e.g., API key or IP address).
     * @param endpoint The endpoint being accessed.
     * @return A [Mono] of [RateLimitResult] indicating if the request was allowed or denied.
     */
    fun consumeToken(identifier: String, endpoint: String): Mono<RateLimitResult> =
        mono { rateLimitingService.consumeToken(identifier, endpoint) }

    /**
     * Consumes a token for a given identifier using a specific strategy,
     * returning a [Mono] of [RateLimitResult].
     *
     * @param identifier The identifier to rate limit (e.g., API key or IP address).
     * @param endpoint The endpoint being accessed.
     * @param strategy The rate limiting strategy to apply.
     * @return A [Mono] of [RateLimitResult] indicating if the request was allowed or denied.
     */
    fun consumeToken(identifier: String, endpoint: String, strategy: RateLimitStrategy): Mono<RateLimitResult> =
        mono { rateLimitingService.consumeToken(identifier, endpoint, strategy) }
}
