package com.profiletailors.ratelimit.domain

/**
 * Type of rate limiting strategy.
 */
enum class RateLimitStrategy {
    /**
     * Authentication strategy: uses strict per-minute/per-hour limits to prevent brute force attacks.
     */
    AUTH,

    /**
     * Business strategy: uses pricing plan-based limits for API usage quotas.
     */
    BUSINESS,

    /**
     * Waitlist strategy: uses fixed rate limit for waitlist endpoints (10 req/min per IP to prevent spam).
     */
    WAITLIST
}
