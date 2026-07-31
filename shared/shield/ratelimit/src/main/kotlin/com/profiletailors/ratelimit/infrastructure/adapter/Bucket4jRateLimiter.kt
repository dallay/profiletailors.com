package com.profiletailors.ratelimit.infrastructure.adapter

import com.profiletailors.ratelimit.domain.RateLimitResult
import com.profiletailors.ratelimit.domain.RateLimitStrategy
import com.profiletailors.ratelimit.domain.RateLimiter
import com.profiletailors.ratelimit.infrastructure.config.BucketConfigurationFactory
import com.profiletailors.ratelimit.infrastructure.metrics.RateLimitMetrics
import com.profiletailors.ratelimit.infrastructure.store.LocalCaffeineRateLimitStore
import com.profiletailors.ratelimit.infrastructure.store.RateLimitStore
import io.github.bucket4j.Bucket
import io.github.bucket4j.ConsumptionProbe
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Adapter that implements the RateLimiter port using Bucket4j.
 * This class is responsible for the actual rate limiting logic using the Bucket4j library.
 *
 * This implementation supports multiple strategies:
 * - AUTH: For authentication endpoints, using time-based limits (per-minute, per-hour)
 * - BUSINESS: For business endpoints, using pricing plan-based limits
 * - RESUME: For resume generation endpoints, using fixed rate limits per user
 * - WAITLIST: For waitlist endpoints, using fixed rate limits per IP
 *
 * Bucket state is delegated to a pluggable [RateLimitStore].
 * - Local deployments use Caffeine in-process buckets.
 * - Distributed deployments can use Redis-backed Bucket4j storage.
 *
 * @property configurationFactory Factory for creating bucket configurations.
 * @property apiKeyParser Parser for extracting subscription tier from API keys.
 * @property metrics Metrics collector for rate limiting operations.
 * @property rateLimitStore Pluggable bucket-state storage (local or distributed).
 * @since 2.0.0
 */
@Component
class Bucket4jRateLimiter(
    private val configurationFactory: BucketConfigurationFactory,
    private val apiKeyParser: ApiKeyParser,
    private val metrics: RateLimitMetrics,
    private val rateLimitStore: RateLimitStore,
    private val clock: Clock = Clock.systemUTC(),
) : RateLimiter {

    private val logger = LoggerFactory.getLogger(Bucket4jRateLimiter::class.java)

    init {
        logger.info("Initialized Bucket4jRateLimiter with store source={}", rateLimitStore.source)
    }

    override suspend fun consumeToken(identifier: String): RateLimitResult =
        consumeToken(identifier, RateLimitStrategy.BUSINESS)

    /**
     * Consumes a token with a specific rate limiting strategy.
     *
     * @param identifier The identifier to rate limit (e.g., API key or IP address).
     * @param strategy The rate limiting strategy to apply.
     * @return A [RateLimitResult] indicating if the request was allowed or denied.
     */
    override suspend fun consumeToken(identifier: String, strategy: RateLimitStrategy): RateLimitResult =
        consumeToken(identifier, strategy, identifier)

    override suspend fun consumeToken(
        identifier: String,
        strategy: RateLimitStrategy,
        bucketIdentity: String,
    ): RateLimitResult = metrics.recordTokenConsumption(strategy, rateLimitStore.source) {
        val cacheKey = "${strategy.name}:$bucketIdentity"
        val configuration = getBucketConfiguration(identifier, strategy)
        val limitCapacity = configuration.bandwidths.minOf { it.capacity }
        val refillDurationNanos = configuration.bandwidths
            .filter { it.capacity == limitCapacity }
            .minOfOrNull { it.refillPeriodNanos }
            ?: error("Bucket configuration must have at least one bandwidth")

        val bucket: Bucket = rateLimitStore.resolveBucket(cacheKey, configuration)
        val probe: ConsumptionProbe = bucket.tryConsumeAndReturnRemaining(1)

        val result = if (probe.isConsumed) {
            val resetTime = calculateResetTime(refillDurationNanos)
            logger.debug(
                "Token consumed for identifier: {}, strategy: {}, source: {}, remaining: {}, limit: {}, reset: {}",
                identifier,
                strategy,
                rateLimitStore.source,
                probe.remainingTokens,
                limitCapacity,
                resetTime,
            )
            RateLimitResult.Allowed(
                remainingTokens = probe.remainingTokens,
                limitCapacity = limitCapacity,
                resetTime = resetTime,
            )
        } else {
            val retryAfter = Duration.ofNanos(probe.nanosToWaitForRefill)
            logger.warn(
                "Rate limit exceeded for identifier: {}, strategy: {}, source: {}, retry after: {}, limit: {}",
                identifier,
                strategy,
                rateLimitStore.source,
                retryAfter,
                limitCapacity,
            )
            RateLimitResult.Denied(
                retryAfter = retryAfter,
                limitCapacity = limitCapacity,
                windowDuration = Duration.ofNanos(refillDurationNanos),
            )
        }

        metrics.recordRateLimitCheck(strategy, result, rateLimitStore.source)
        result
    }

    /**
     * Gets the bucket configuration for the given identifier and strategy.
     * This method is extracted to allow reuse for metadata extraction.
     *
     * @param identifier The identifier to rate limit.
     * @param strategy The rate limiting strategy to apply.
     * @return A [io.github.bucket4j.BucketConfiguration] instance.
     */
    private fun getBucketConfiguration(
        identifier: String,
        strategy: RateLimitStrategy,
    ): io.github.bucket4j.BucketConfiguration = when (strategy) {
        RateLimitStrategy.AUTH ->
            configurationFactory.createConfiguration(RateLimitStrategy.AUTH)

        RateLimitStrategy.BUSINESS -> {
            val planName = resolvePlanNameFromApiKey(identifier)
            logger.debug(
                "Resolved plan: {} for identifier: {}",
                planName,
                identifier,
            )
            configurationFactory.createConfiguration(RateLimitStrategy.BUSINESS, planName)
        }

        RateLimitStrategy.RESUME ->
            configurationFactory.createConfiguration(RateLimitStrategy.RESUME)

        RateLimitStrategy.WAITLIST ->
            configurationFactory.createConfiguration(RateLimitStrategy.WAITLIST)
    }

    /**
     * Calculates the reset time based on the refill duration.
     * This is an approximation since Bucket4j doesn't expose the exact refill schedule.
     *
     * @param refillPeriodNanos The refill period in nanoseconds.
     * @return The [java.time.Instant] when the bucket will be refilled.
     */
    private fun calculateResetTime(refillPeriodNanos: Long): Instant {
        val refillDuration = Duration.ofNanos(refillPeriodNanos)
        return Instant.now(clock).plus(refillDuration)
    }

    /**
     * Resolves the pricing plan name from an API key.
     * This method delegates to [ApiKeyParser] to extract the subscription tier
     * based on configured API key prefixes.
     *
     * The prefix-to-tier mapping is externalized to configuration, making the system
     * more maintainable and following the Open/Closed Principle.
     *
     * @param apiKey The API key.
     * @return The plan name in lowercase (e.g., "free", "basic", "professional").
     * @see ApiKeyParser.extractTierName
     */
    private fun resolvePlanNameFromApiKey(apiKey: String): String = apiKeyParser.extractTierName(apiKey)

    /**
     * Returns the current cache size (estimated).
     * Useful for monitoring and testing.
     */
    fun getCacheSize(): Long = (rateLimitStore as? LocalCaffeineRateLimitStore)?.estimatedSize() ?: 0

    /**
     * Returns cache statistics including hit rate, eviction count, and load times.
     * Useful for monitoring cache performance and tuning configuration.
     */
    fun getCacheStats(): com.github.benmanes.caffeine.cache.stats.CacheStats =
        (rateLimitStore as? LocalCaffeineRateLimitStore)?.stats()
            ?: com.github.benmanes.caffeine.cache.stats.CacheStats.empty()

    /**
     * Triggers Caffeine's async cleanup process to execute pending maintenance tasks.
     * This forces evictions to be processed immediately, which is useful for testing.
     *
     * In production, Caffeine handles cleanup asynchronously for performance,
     * but in tests we need deterministic behavior to verify eviction counts.
     */
    fun triggerCacheCleanup() {
        (rateLimitStore as? LocalCaffeineRateLimitStore)?.triggerCleanup()
    }

    /**
     * Clears all cached buckets.
     * Useful for testing and dynamic configuration reloading.
     */
    fun clearCache() {
        (rateLimitStore as? LocalCaffeineRateLimitStore)?.clear()
    }
}
