package com.profiletailors.ratelimit.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.ratelimit.domain.RateLimitResult
import com.profiletailors.ratelimit.domain.RateLimitStrategy
import com.profiletailors.ratelimit.domain.RateLimiter
import com.profiletailors.ratelimit.domain.event.RateLimitExceededEvent
import java.security.MessageDigest
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
    private val eventPublisher: EventPublisher<RateLimitExceededEvent>
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
    suspend fun consumeToken(
        identifier: String,
        endpoint: String,
        strategy: RateLimitStrategy
    ): RateLimitResult {
        val result = rateLimiter.consumeToken(identifier, strategy)
        if (result is RateLimitResult.Denied) {
            try {
                publishRateLimitExceededEvent(
                    identifier = identifier,
                    endpoint = endpoint,
                    retryAfter = result.retryAfter,
                    windowDuration = result.windowDuration,
                    strategy = strategy,
                )
            } catch (e: Exception) {
                val hashedId = hashIdentifier(identifier)
//                log.warn("Failed to publish rate limit exceeded event for identifier: {} (hashed)", hashedId, e)
                // We don't rethrow to ensure the Denied result is still returned to the caller
            }
        }
        return result
    }

    /**
     * Creates a non-reversible hash of the identifier for safe logging of PII.
     */
    private fun hashIdentifier(identifier: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(identifier.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it.toInt() and BYTE_MASK) }.take(HASH_LOG_LENGTH) + ELLIPSIS
        } catch (e: Exception) {
            //log.warn("Failed to hash identifier for safe logging", e)
            "unknown-id-hash-failed"
        }
    }

    private suspend fun publishRateLimitExceededEvent(
        identifier: String,
        endpoint: String,
        retryAfter: Duration,
        windowDuration: Duration,
        strategy: RateLimitStrategy
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

    companion object {
        private const val HASH_LOG_LENGTH = 8
        private const val ELLIPSIS = "..."
        private const val BYTE_MASK = 0xff
    }
}
