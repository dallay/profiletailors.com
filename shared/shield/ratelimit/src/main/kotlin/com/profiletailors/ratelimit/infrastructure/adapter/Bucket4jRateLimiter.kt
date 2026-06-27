package com.profiletailors.ratelimit.infrastructure.adapter

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.profiletailors.ratelimit.domain.RateLimitResult
import com.profiletailors.ratelimit.domain.RateLimitStrategy
import com.profiletailors.ratelimit.domain.RateLimiter
import com.profiletailors.ratelimit.infrastructure.config.BucketConfigurationFactory
import com.profiletailors.ratelimit.infrastructure.config.RateLimitProperties
import com.profiletailors.ratelimit.infrastructure.metrics.RateLimitMetrics
import io.github.bucket4j.Bucket
import io.github.bucket4j.ConsumptionProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * **Cache Strategy:**
 * Uses Caffeine cache with bounded size and TTL-based eviction to prevent unbounded memory growth.
 * In high-traffic scenarios with many unique identifiers (IPs, API keys), an unbounded cache would
 * lead to memory exhaustion. The cache is configured with:
 * - Maximum size limit (default: 10,000 entries)
 * - TTL-based eviction (default: 1 hour after last access)
 * - Automatic cleanup of stale entries
 *
 * For horizontally-scaled deployments, consider migrating to Bucket4j distributed buckets
 * (Redis, Hazelcast, etc.) to share rate limit state across instances.
 *
 * @property configurationFactory Factory for creating bucket configurations.
 * @property apiKeyParser Parser for extracting subscription tier from API keys.
 * @property metrics Metrics collector for rate limiting operations.
 * @property properties Rate limit configuration properties.
 * @since 2.0.0
 */
@Component
class Bucket4jRateLimiter(
    private val configurationFactory: BucketConfigurationFactory,
    private val apiKeyParser: ApiKeyParser,
    private val metrics: RateLimitMetrics,
    private val properties: RateLimitProperties,
    private val clock: Clock = Clock.systemUTC(),
) : RateLimiter {

    private val logger = LoggerFactory.getLogger(Bucket4jRateLimiter::class.java)

    /**
     * Bounded, TTL-based cache for rate limit buckets.
     * Prevents unbounded memory growth in long-running services with many unique identifiers.
     */
    private data class CachedBucketEntry(val bucket: Bucket, val limitCapacity: Long, val refillPeriodNanos: Long)

    private val cache: Cache<String, CachedBucketEntry> = Caffeine.newBuilder()
        .maximumSize(properties.cache.maxSize)
        .expireAfterAccess(Duration.ofMinutes(properties.cache.ttlMinutes))
        .removalListener<String, CachedBucketEntry> { key, _, cause ->
            logger.debug("Cache eviction: key={}, cause={}", key, cause)
        }
        .recordStats()
        .build()

    init {
        logger.info(
            "Initialized Bucket4jRateLimiter with cache config: maxSize={}, ttlMinutes={}",
            properties.cache.maxSize,
            properties.cache.ttlMinutes,
        )
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
    override suspend fun consumeToken(identifier: String, strategy: RateLimitStrategy): RateLimitResult {
        // Record token consumption time and update cache size metric
        return metrics.recordTokenConsumption(strategy) {
            // Caffeine synchronous lookups and Bucket4j operations are blocking
            // We wrap them in Dispatchers.IO to prevent starving the coroutine dispatcher
            withContext(Dispatchers.IO) {
                val cacheKey = "${strategy.name}:$identifier"
                val entry = cache.get(cacheKey) { createCachedBucketEntry(identifier, strategy) }
                val bucket = entry.bucket
                val limitCapacity = entry.limitCapacity
                val refillDuration = entry.refillPeriodNanos

                // Update cache size and stats metrics after potential cache insertion
                metrics.updateCacheSize(cache.estimatedSize().toInt())

                val probe: ConsumptionProbe = bucket.tryConsumeAndReturnRemaining(1)

                // Metadata already extracted from entry above
                val result = if (probe.isConsumed) {
                    val resetTime = calculateResetTime(refillDuration)
                    logger.debug(
                        "Token consumed for identifier: {}, strategy: {}, remaining: {}, limit: {}, reset: {}",
                        identifier,
                        strategy,
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
                        "Rate limit exceeded for identifier: {}, strategy: {}, retry after: {}, limit: {}",
                        identifier,
                        strategy,
                        retryAfter,
                        limitCapacity,
                    )
                    RateLimitResult.Denied(
                        retryAfter = retryAfter,
                        limitCapacity = limitCapacity,
                        windowDuration = Duration.ofNanos(refillDuration),
                    )
                }

                // Record metrics for this rate limit check
                metrics.recordRateLimitCheck(strategy, result)

                result
            }
        }
    }

    /**
     * Creates a new bucket for the given identifier and strategy.
     *
     * @param identifier The identifier to rate limit.
     * @param strategy The rate limiting strategy to apply.
     * @return A configured [Bucket] instance.
     */
    private fun createCachedBucketEntry(identifier: String, strategy: RateLimitStrategy): CachedBucketEntry {
        logger.debug("Creating {} bucket for identifier: {}", strategy, identifier)

        val configuration = getBucketConfiguration(identifier, strategy)
        val builder = Bucket.builder()
        configuration.bandwidths.forEach { bandwidth ->
            builder.addLimit(bandwidth)
        }
        val bucket = builder.build()
        val limitCapacity = configuration.bandwidths.minOf { it.capacity }
        val refillPeriodNanos = configuration.bandwidths
            .filter { it.capacity == limitCapacity }
            .minOfOrNull { it.refillPeriodNanos }
            ?: error("Bucket configuration must have at least one bandwidth")
        return CachedBucketEntry(bucket, limitCapacity, refillPeriodNanos)
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
    fun getCacheSize(): Long = cache.estimatedSize()

    /**
     * Returns cache statistics including hit rate, eviction count, and load times.
     * Useful for monitoring cache performance and tuning configuration.
     */
    fun getCacheStats(): com.github.benmanes.caffeine.cache.stats.CacheStats = cache.stats()

    /**
     * Triggers Caffeine's async cleanup process to execute pending maintenance tasks.
     * This forces evictions to be processed immediately, which is useful for testing.
     *
     * In production, Caffeine handles cleanup asynchronously for performance,
     * but in tests we need deterministic behavior to verify eviction counts.
     */
    fun triggerCacheCleanup() {
        cache.cleanUp()
    }

    /**
     * Clears all cached buckets.
     * Useful for testing and dynamic configuration reloading.
     */
    fun clearCache() {
        val statsBeforeClear = cache.stats()
        cache.invalidateAll()
        cache.cleanUp()
        metrics.updateCacheSize(0)
        logger.info("Cleared all cached bucket entries. Cache stats before clear: {}", statsBeforeClear)
    }
}
