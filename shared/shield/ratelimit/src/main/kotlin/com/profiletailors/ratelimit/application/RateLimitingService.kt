package com.profiletailors.ratelimit.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.ratelimit.domain.RateLimitResult
import com.profiletailors.ratelimit.domain.RateLimitStrategy
import com.profiletailors.ratelimit.domain.RateLimiter
import com.profiletailors.ratelimit.domain.event.RateLimitExceededEvent
import java.time.Duration
import java.time.Instant

/**
 * Application service for handling rate limiting logic.
 * This service acts as a use case handler, orchestrating the interaction
 * between the incoming request (port in) and the rate limiting implementation (port out).
 * All operations are non-blocking using suspend functions.
 */
@Service
class RateLimitingService(
    private val rateLimiter: RateLimiter,
    private val eventPublisher: EventPublisher<RateLimitExceededEvent>,
) {

    /**
     * Consumes a token for a given identifier using the default BUSINESS strategy.
     *
     * @param identifier The identifier to rate limit (e.g., API key or IP address).
     * @param endpoint The endpoint being accessed.
     * @return A [RateLimitResult] indicating if the request was allowed or denied.
     */
    suspend fun consumeToken(identifier: String, endpoint: String): RateLimitResult =
        consumeToken(identifier, endpoint, RateLimitStrategy.BUSINESS)

    /**
     * Consumes a token for a given identifier using a specific strategy and publishes an event
     * if the rate limit is exceeded.
     *
     * @param identifier The identifier to rate limit (e.g., API key or IP address).
     * @param endpoint The endpoint being accessed.
     * @param strategy The rate limiting strategy to apply.
     * @return A [RateLimitResult] indicating if the request was allowed or denied.
     */
    suspend fun consumeToken(identifier: String, endpoint: String, strategy: RateLimitStrategy): RateLimitResult {
        val result = if (strategy == RateLimitStrategy.WAITLIST) {
            rateLimiter.consumeToken(identifier, strategy, bucketIdentity(identifier, endpoint))
        } else {
            rateLimiter.consumeToken(identifier, strategy)
        }
        if (result is RateLimitResult.Denied) {
            try {
                publishRateLimitExceededEvent(
                    identifier = identifier,
                    endpoint = endpoint,
                    retryAfter = result.retryAfter,
                    windowDuration = result.windowDuration,
                    strategy = strategy,
                )
            } catch (_: Exception) {
                // We don't rethrow to ensure the Denied result is still returned to the caller
            }
        }
        return result
    }

    private fun bucketIdentity(identifier: String, endpoint: String): String = "$identifier:$endpoint"

    private suspend fun publishRateLimitExceededEvent(
        identifier: String,
        endpoint: String,
        retryAfter: Duration,
        windowDuration: Duration,
        strategy: RateLimitStrategy,
    ) {
        val event = RateLimitExceededEvent(
            identifier = identifier,
            endpoint = endpoint,
            attemptCount = null, // Bucket4j doesn't track individual attempts
            maxAttempts = null, // Bucket4j doesn't track individual attempts
            windowDuration = windowDuration,
            strategy = strategy,
            resetTime = Instant.now().plus(retryAfter),
        )
        eventPublisher.publish(event)
    }
}
