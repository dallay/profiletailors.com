package com.profiletailors.ratelimit.domain

import java.time.Duration
import java.time.Instant

/**
 * Represents the result of a rate limit consumption attempt.
 * This is a framework-agnostic model, part of the application layer.
 */
sealed class RateLimitResult {
    /**
     * The request was allowed.
     *
     * @property remainingTokens The number of tokens left in the bucket.
     * @property limitCapacity The maximum number of tokens the bucket can hold (for X-RateLimit-Limit header).
     * @property resetTime An estimated timestamp when the bucket will be fully refilled (for X-RateLimit-Reset header).
     *
     * NOTE: resetTime is a best-effort estimate. It is computed as a conservative approximation
     * (for example: now + refill duration) and does not account for the bucket creation time,
     * partial refills, clock skew across distributed systems, or the exact internal refill
     * schedule. Clients MUST treat this value as advisory only and NOT rely on it for precise
     * retry timing. When strict retry timing is required, prefer the standard `Retry-After`
     * header included by the server when a request is denied (HTTP 429). Clients should
     * implement conservative retry/backoff strategies (exponential backoff or respect Retry-After)
     * instead of depending on resetTime for exact behavior.
     */
    data class Allowed(val remainingTokens: Long, val limitCapacity: Long, val resetTime: Instant) : RateLimitResult()

    /**
     * The request was denied.
     *
     * @property retryAfter The duration until the next token is available.
     * @property limitCapacity The maximum number of tokens the bucket can hold (for X-RateLimit-Limit header).
     * @property windowDuration The configured time window for the rate limit (e.g., 1 minute, 1 hour).
     */
    data class Denied(val retryAfter: Duration, val limitCapacity: Long, val windowDuration: Duration) :
        RateLimitResult()
}
