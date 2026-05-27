package com.profiletailors.ratelimit.domain

/**
 * Output port for the rate limiting service.
 * This defines the contract for any rate limiting implementation (adapter).
 */
interface RateLimiter {
    /**
     * Consumes a token for a given identifier in a non-blocking manner.
     *
     * @param identifier The identifier to rate limit (e.g., API key or IP address).
     * @return A [RateLimitResult] indicating if the request was allowed or denied.
     */
    suspend fun consumeToken(identifier: String): RateLimitResult

    /**
     * Consumes a token for a given identifier in a non-blocking manner.
     *
     * @param identifier The identifier to rate limit (e.g., API key or IP address).
     * @param strategy The rate limiting strategy to apply.
     * @return A [RateLimitResult] indicating if the request was allowed or denied.
     */
    suspend fun consumeToken(identifier: String, strategy: RateLimitStrategy): RateLimitResult
}
