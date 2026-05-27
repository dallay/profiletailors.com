package com.profiletailors.ratelimit.infrastructure.config

import com.profiletailors.ratelimit.domain.RateLimitStrategy
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.BucketConfiguration
import java.time.Duration
import org.slf4j.LoggerFactory

class BucketConfigurationFactory(
    private val properties: RateLimitProperties
) {
    private val logger = LoggerFactory.getLogger(BucketConfigurationFactory::class.java)

    /**
     * Creates a bucket configuration for the specified rate limiting strategy.
     *
     * @param strategy The rate limiting strategy to apply.
     * @param planName Optional plan name for BUSINESS strategy (defaults to "free" if not provided).
     * @return A [BucketConfiguration] configured for the specified strategy.
     * @throws IllegalArgumentException if the strategy is BUSINESS and the plan name is invalid.
     */
    fun createConfiguration(
        strategy: RateLimitStrategy,
        planName: String? = null
    ): BucketConfiguration {
        return when (strategy) {
            RateLimitStrategy.AUTH -> createAuthConfiguration()
            RateLimitStrategy.BUSINESS -> createBusinessConfiguration(planName ?: "free")
            RateLimitStrategy.BUSINESS -> createResumeConfiguration()
            RateLimitStrategy.WAITLIST -> createWaitlistConfiguration()
        }
    }

    /**
     * Creates a bucket configuration for authentication endpoints.
     * Applies multiple limits (e.g., per-minute and per-hour) to protect against different attack patterns.
     */
    private fun createAuthConfiguration(): BucketConfiguration {
        logger.debug("Creating auth bucket configuration with {} limits", properties.auth.limits.size)

        val builder = BucketConfiguration.builder()

        properties.auth.limits.forEach { limit ->
            val bandwidth = createBandwidth(limit)
            builder.addLimit(bandwidth)
            logger.debug(
                "Added auth limit: {} - capacity={}, refill={} tokens per {}",
                limit.name, limit.capacity, limit.refillTokens, limit.refillDuration,
            )
        }

        return builder.build()
    }

    /**
     * Creates a bucket configuration for business endpoints based on a pricing plan.
     *
     * @param planName The name of the pricing plan (e.g., "free", "basic", "professional").
     * @throws IllegalArgumentException if the plan name is not found.
     */
    private fun createBusinessConfiguration(planName: String): BucketConfiguration {
        val limit = properties.business.pricingPlans[planName.lowercase()]
            ?: throw IllegalArgumentException(
                "Unknown pricing plan: $planName. Available plans: ${properties.business.pricingPlans.keys}",
            )

        logger.debug(
            "Creating business bucket configuration for plan: {} - capacity={}, refill={} tokens per {}",
            planName, limit.capacity, limit.refillTokens, limit.refillDuration,
        )

        val bandwidth = createBandwidth(limit)
        return BucketConfiguration.builder()
            .addLimit(bandwidth)
            .build()
    }

    /**
     * Creates a bucket configuration for resume generation endpoints.
     * Applies a fixed rate limit of 10 requests per minute per user.
     */
    private fun createResumeConfiguration(): BucketConfiguration {
        val limit = properties.resume.limit

        logger.debug(
            "Creating resume bucket configuration - capacity={}, refill={} tokens per {}",
            limit.capacity, limit.refillTokens, limit.refillDuration,
        )

        val bandwidth = createBandwidth(limit)
        return BucketConfiguration.builder()
            .addLimit(bandwidth)
            .build()
    }

    /**
     * Creates a bucket configuration for waitlist endpoints.
     * Applies a fixed rate limit of 10 requests per minute per IP.
     */
    private fun createWaitlistConfiguration(): BucketConfiguration {
        val limit = properties.waitlist.limit

        logger.debug(
            "Creating waitlist bucket configuration - capacity={}, refill={} tokens per {}",
            limit.capacity, limit.refillTokens, limit.refillDuration,
        )

        val bandwidth = createBandwidth(limit)
        return BucketConfiguration.builder()
            .addLimit(bandwidth)
            .build()
    }

    /**
     * Creates a Bandwidth from a BandwidthLimit configuration using Bucket4j v8 builder API.
     *
     * This method supports two refill strategies:
     * - **Greedy Refill**: Used when `refillTokens` equals `capacity`.
     *   Provides greedy refill where the bucket is fully replenished at each refill interval.
     *   Example: capacity=100, refillTokens=100 per minute = 100 tokens/minute.
     *
     * - **Intervally Refill**: Used when `refillTokens` differs from `capacity`.
     *   Allows fine-grained control over refill rates independent of bucket capacity.
     *   Example: capacity=100, refillTokens=10 per minute = 10 tokens/minute (burst capacity of 100).
     *
     * @param limit The bandwidth limit configuration.
     * @return A [Bandwidth] instance configured with the specified parameters.
     * @throws IllegalArgumentException if the refill duration is invalid (e.g., Duration.ZERO).
     */
    private fun createBandwidth(limit: RateLimitProperties.BandwidthLimit): Bandwidth {
        require(limit.refillDuration > Duration.ZERO) {
            "Refill duration must be positive, got: ${limit.refillDuration}"
        }
        val builder = Bandwidth.builder().capacity(limit.capacity)

        return if (limit.refillTokens == limit.capacity) {
            builder.refillGreedy(limit.capacity, limit.refillDuration).build()
        } else {
            builder.refillIntervally(limit.refillTokens, limit.refillDuration).build()
        }
    }

    // ============================================
    // Configuration State Checks
    // ============================================

    /**
     * Checks if rate limiting is enabled for the specified strategy.
     */
    fun isRateLimitEnabled(strategy: RateLimitStrategy): Boolean {
        return properties.enabled && when (strategy) {
            RateLimitStrategy.AUTH -> properties.auth.enabled
            RateLimitStrategy.BUSINESS -> properties.business.enabled
            RateLimitStrategy.BUSINESS -> properties.resume.enabled
            RateLimitStrategy.WAITLIST -> properties.waitlist.enabled
        }
    }

    /**
     * Gets the list of endpoints that should be rate limited for the specified strategy.
     *
     * Note: BUSINESS strategy returns an empty list because business endpoint detection
     * is based on API key presence rather than specific URL paths.
     */
    fun getEndpoints(strategy: RateLimitStrategy): List<String> {
        return when (strategy) {
            RateLimitStrategy.AUTH -> properties.auth.endpoints
            RateLimitStrategy.BUSINESS -> properties.resume.endpoints
            RateLimitStrategy.WAITLIST -> properties.waitlist.endpoints
            RateLimitStrategy.BUSINESS -> emptyList() // Business endpoints are handled differently
        }
    }
}
