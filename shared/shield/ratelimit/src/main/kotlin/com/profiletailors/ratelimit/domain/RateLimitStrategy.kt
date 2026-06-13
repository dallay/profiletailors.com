package com.profiletailors.ratelimit.domain

/**
 * Defines the available rate limiting strategies for different endpoint categories.
 *
 * Each strategy corresponds to a distinct rate limit configuration, allowing
 * different parts of the system to be rate-limited independently with tailored
 * thresholds, refill rates, and endpoint mappings.
 *
 * **Strategies:**
 * - [AUTH]: Strict time-based limits (per-minute and per-hour) to prevent brute-force
 *   attacks on authentication endpoints like login, registration, and password reset.
 * - [BUSINESS]: Pricing-plan-based limits for API usage quotas. Tier is resolved from
 *   the API key prefix (free/basic/professional).
 * - [RESUME]: Fixed rate limit (10 req/min per user) for resume generation endpoints.
 * - [WAITLIST]: Fixed rate limit (10 req/min per IP) for waitlist sign-up endpoints.
 *
 * @since 1.0.0
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
     * Resume strategy: uses a fixed rate limit for resume generation endpoints.
     * See [com.profiletailors.ratelimit.infrastructure.config.RateLimitProperties.ResumeRateLimitConfig].
     */
    RESUME,

    /**
     * Waitlist strategy: uses fixed rate limit for waitlist endpoints (10 req/min per IP to prevent spam).
     */
    WAITLIST
}
