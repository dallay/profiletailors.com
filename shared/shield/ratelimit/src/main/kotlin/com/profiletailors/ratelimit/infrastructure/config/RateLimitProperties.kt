package com.profiletailors.ratelimit.infrastructure.config

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for rate limiting.
 * These properties can be configured in application.yml under the prefix "application.rate-limit".
 *
 * @since 1.0.0
 *
 * Example configuration:
 * ```yaml
 * application:
 *   rate-limit:
 *     enabled: true
 *     api-key-prefixes:
 *       professional: "PX001-"
 *       basic: "BX001-"
 *     auth:
 *       enabled: true
 *       limits:
 *         - name: per-minute
 *           capacity: 10
 *           refill-tokens: 10
 *           refill-duration: 1m
 *         - name: per-hour
 *           capacity: 100
 *           refill-tokens: 100
 *           refill-duration: 1h
 *     business:
 *       enabled: true
 *       pricing-plans:
 *         free:
 *           capacity: 20
 *           refill-tokens: 20
 *           refill-duration: 1h
 *         basic:
 *           capacity: 40
 *           refill-tokens: 40
 *           refill-duration: 1h
 *         professional:
 *           capacity: 100
 *           refill-tokens: 100
 *           refill-duration: 1h
 * ```
 */
@ConfigurationProperties(prefix = "application.rate-limit")
data class RateLimitProperties(
    /**
     * Whether rate limiting is enabled globally.
     */
    val enabled: Boolean = true,

    /**
     * Configuration for the rate limiter cache.
     * Controls bounded size and TTL-based eviction to prevent unbounded memory growth.
     */
    val cache: CacheConfig = CacheConfig(),

    /**
     * Configuration for API key prefix mapping to subscription tiers.
     * Used to determine the tier from an API key prefix.
     */
    val apiKeyPrefixes: ApiKeyPrefixConfig = ApiKeyPrefixConfig(),

    /**
     * Configuration for authentication endpoints rate limiting.
     */
    val auth: AuthRateLimitConfig = AuthRateLimitConfig(),

    /**
     * Configuration for business endpoints rate limiting.
     */
    val business: BusinessRateLimitConfig = BusinessRateLimitConfig(),

    /**
     * Configuration for resume generation endpoints rate limiting.
     */
    val resume: ResumeRateLimitConfig = ResumeRateLimitConfig(),

    /**
     * Configuration for waitlist endpoints rate limiting.
     */
    val waitlist: WaitlistRateLimitConfig = WaitlistRateLimitConfig()
) {

    companion object {
        const val TIER_FREE = "free"
        const val TIER_BASIC = "basic"
        const val TIER_PROFESSIONAL = "professional"
    }

    /**
     * Configuration for the rate limiter bucket cache.
     * Prevents unbounded memory growth by limiting cache size and using TTL-based eviction.
     *
     * The cache uses Caffeine with:
     * - Maximum size limit (LRU eviction when full)
     * - TTL-based eviction (removes idle entries)
     * - Asynchronous eviction (optimized for throughput)
     *
     * Capacity Planning:
     * - Small API (< 1K users): 2,000 entries
     * - Medium API (1-10K users): 10,000 entries (default)
     * - Large API (10-100K users): 50,000 entries
     * - Enterprise (> 100K users): Consider distributed cache (Redis)
     */
    data class CacheConfig(
        /**
         * Maximum number of cached rate limit buckets.
         * When the cache reaches this size, least-recently-used entries are evicted.
         *
         * Default: 10,000 entries (~1-2MB memory footprint)
         */
        val maxSize: Long = 10_000,

        /**
         * Time-to-live in minutes for idle cache entries.
         * Entries not accessed within this duration are automatically evicted.
         *
         * Default: 60 minutes (1 hour)
         */
        val ttlMinutes: Long = 60
    ) {
        init {
            require(maxSize > 0) { "maxSize must be positive" }
            require(ttlMinutes > 0) { "ttlMinutes must be positive" }
        }
    }

    /**
     * Configuration for API key prefix to subscription tier mapping.
     * Allows externalization of the prefix detection logic.
     */
    data class ApiKeyPrefixConfig(
        /**
         * Prefix for professional tier API keys.
         * Example: "PX001-abc123..." indicates a professional tier key.
         */
        val professional: String = "PX001-",

        /**
         * Prefix for basic tier API keys.
         * Example: "BX001-xyz789..." indicates a basic tier key.
         */
        val basic: String = "BX001-"
    )

    /**
     * Configuration for authentication endpoint rate limiting.
     * These limits are applied per IP/identifier to prevent brute force attacks.
     */
    data class AuthRateLimitConfig(
        /**
         * Whether authentication rate limiting is enabled.
         */
        val enabled: Boolean = true,

        /**
         * List of endpoints that should be rate limited as authentication endpoints.
         */
        val endpoints: List<String> = listOf(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/password/reset",
            "/api/auth/refresh-token",
            "/api/auth/token/refresh",
            "/api/auth/federated",
        ),

        /**
         * Multiple limits that can be applied simultaneously (e.g., per-minute + per-hour).
         * This allows for protection against both burst attacks and sustained attacks.
         */
        val limits: List<BandwidthLimit> = listOf(
            BandwidthLimit(
                name = "per-minute",
                capacity = 10,
                refillTokens = 10,
                refillDuration = Duration.ofMinutes(1),
            ),
            BandwidthLimit(
                name = "per-hour",
                capacity = 100,
                refillTokens = 100,
                refillDuration = Duration.ofHours(1),
            ),
        )
    )

    /**
     * Configuration for business endpoint rate limiting.
     * These limits are applied based on pricing plans.
     */
    data class BusinessRateLimitConfig(
        /**
         * Whether business rate limiting is enabled.
         */
        val enabled: Boolean = true,

        /**
         * Pricing plans configuration.
         * Keys are plan names (e.g., "free", "basic", "professional").
         */
        val pricingPlans: Map<String, BandwidthLimit> = mapOf(
            TIER_FREE to BandwidthLimit(
                name = "free-plan",
                capacity = 20,
                refillTokens = 20,
                refillDuration = Duration.ofHours(1),
            ),
            TIER_BASIC to BandwidthLimit(
                name = "basic-plan",
                capacity = 40,
                refillTokens = 40,
                refillDuration = Duration.ofHours(1),
            ),
            TIER_PROFESSIONAL to BandwidthLimit(
                name = "professional-plan",
                capacity = 100,
                refillTokens = 100,
                refillDuration = Duration.ofHours(1),
            ),
        )
    )

    /**
     * Configuration for resume generation endpoint rate limiting.
     * Enforces a fixed rate limit of 10 requests per minute per user.
     */
    data class ResumeRateLimitConfig(
        /**
         * Whether resume rate limiting is enabled.
         */
        val enabled: Boolean = true,

        /**
         * List of endpoints that should be rate limited as resume endpoints.
         */
        val endpoints: List<String> = listOf(
            "/api/resume/generate",
        ),

        /**
         * Rate limit configuration: 10 requests per minute per user.
         */
        val limit: BandwidthLimit = BandwidthLimit(
            name = "resume-per-minute",
            capacity = 10,
            refillTokens = 10,
            refillDuration = Duration.ofMinutes(1),
        )
    )

    /**
     * Configuration for waitlist endpoint rate limiting.
     * Enforces a fixed rate limit of 10 requests per minute per IP to prevent spam.
     */
    data class WaitlistRateLimitConfig(
        /**
         * Whether waitlist rate limiting is enabled.
         */
        val enabled: Boolean = true,

        /**
         * List of endpoints that should be rate limited as waitlist endpoints.
         */
        val endpoints: List<String> = listOf(
            "/api/waitlist",
        ),

        /**
         * Rate limit configuration: 10 requests per minute per IP.
         */
        val limit: BandwidthLimit = BandwidthLimit(
            name = "waitlist-per-minute",
            capacity = 10,
            refillTokens = 10,
            refillDuration = Duration.ofMinutes(1),
        )
    )

    /**
     * Represents a single bandwidth limit configuration.
     */
    data class BandwidthLimit(
        /**
         * Unique name/identifier for this limit (useful for logging and debugging).
         */
        val name: String,

        /**
         * Maximum number of tokens the bucket can hold.
         */
        val capacity: Long,

        /**
         * Number of tokens to add during each refill.
         */
        val refillTokens: Long,

        /**
         * Duration between refills.
         */
        val refillDuration: Duration,

        /**
         * Initial number of tokens in the bucket. Defaults to capacity if not specified.
         */
        val initialTokens: Long? = null
    )
}
