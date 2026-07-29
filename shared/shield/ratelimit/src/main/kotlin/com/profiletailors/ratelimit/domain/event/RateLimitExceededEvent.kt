package com.profiletailors.ratelimit.domain.event

import com.profiletailors.common.domain.bus.event.BaseDomainEvent
import com.profiletailors.ratelimit.domain.RateLimitStrategy
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Domain event emitted when a rate limit is exceeded.
 *
 * This event is published for security auditing and monitoring purposes,
 * enabling detection of potential brute force attacks or abuse patterns.
 *
 * **Requirements**:
 * - FR-030: Rate limiting violations must generate events
 * - FR-029: Log all authentication events for security auditing
 * - data-model.md: RATE_LIMIT_EXCEEDED event type
 *
 * @property identifier The identifier that exceeded the rate limit (IP address, email, etc.)
 * @property endpoint The endpoint path that was rate limited
 * @property attemptCount The number of attempts made within the window
 * @property maxAttempts The maximum attempts allowed
 * @property windowDuration The time window for the rate limit
 * @property strategy The rate limiting strategy that was applied (AUTH or BUSINESS)
 * @property timestamp When the rate limit was exceeded
 * @property resetTime When the rate limit will reset
 *
 * @since 1.0.0
 */
data class RateLimitExceededEvent(
    val identifier: String,
    val endpoint: String,
    val attemptCount: Int?,
    val maxAttempts: Int?,
    val windowDuration: Duration,
    val strategy: RateLimitStrategy,
    val timestamp: Instant = Instant.now(),
    val resetTime: Instant? = null,
) : BaseDomainEvent(LocalDateTime.ofInstant(timestamp, ZoneOffset.UTC)) {
    init {
        require(identifier.isNotBlank()) { "Identifier cannot be blank" }
        require(endpoint.isNotBlank()) { "Endpoint cannot be blank" }
        if (attemptCount != null && maxAttempts != null) {
            require(attemptCount > 0) { "Attempt count must be positive" }
            require(maxAttempts > 0) { "Max attempts must be positive" }
            require(attemptCount >= maxAttempts) { "Attempt count must be >= max attempts for exceeded event" }
        }
    }

    /**
     * Returns a human-readable description of the rate limit violation.
     */
    fun describe(): String {
        val attemptsInfo = if (attemptCount != null && maxAttempts != null) {
            ": $attemptCount/$maxAttempts attempts"
        } else {
            ""
        }
        return "Rate limit exceeded for $identifier on $endpoint$attemptsInfo"
    }

    /**
     * Calculates the time remaining until reset.
     */
    fun timeUntilReset(): Duration? = resetTime?.let { Duration.between(timestamp, it) }
}
