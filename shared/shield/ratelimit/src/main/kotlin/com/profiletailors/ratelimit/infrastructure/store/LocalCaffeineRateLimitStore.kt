package com.profiletailors.ratelimit.infrastructure.store

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.stats.CacheStats
import com.profiletailors.ratelimit.infrastructure.config.RateLimitProperties
import com.profiletailors.ratelimit.infrastructure.metrics.RateLimitMetrics
import io.github.bucket4j.Bucket
import io.github.bucket4j.BucketConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Duration

class LocalCaffeineRateLimitStore(private val properties: RateLimitProperties, private val metrics: RateLimitMetrics) :
    RateLimitStore {

    private val logger = LoggerFactory.getLogger(LocalCaffeineRateLimitStore::class.java)

    private val cache: Cache<String, Bucket> = Caffeine.newBuilder()
        .maximumSize(properties.cache.maxSize)
        .expireAfterAccess(Duration.ofMinutes(properties.cache.ttlMinutes))
        .removalListener<String, Bucket> { key, _, cause ->
            logger.debug("Rate limit cache eviction: key={}, cause={}", key, cause)
        }
        .recordStats()
        .build()

    override val source: BucketSource = BucketSource.LOCAL

    override suspend fun resolveBucket(cacheKey: String, configuration: BucketConfiguration): Bucket =
        withContext(Dispatchers.IO) {
            val bucket = cache.get(cacheKey) { buildBucket(configuration) }
            metrics.updateCacheSize(cache.estimatedSize().toInt(), source)
            checkNotNull(bucket) { "Bucket should not be null for key=$cacheKey" }
        }

    fun clear() {
        val statsBeforeClear = cache.stats()
        cache.invalidateAll()
        cache.cleanUp()
        metrics.updateCacheSize(0, source)
        logger.info("Cleared local rate-limit cache. Stats before clear: {}", statsBeforeClear)
    }

    fun estimatedSize(): Long = cache.estimatedSize()

    fun stats(): CacheStats = cache.stats()

    fun triggerCleanup() {
        cache.cleanUp()
    }

    private fun buildBucket(configuration: BucketConfiguration): Bucket {
        val builder = Bucket.builder()
        configuration.bandwidths.forEach { bandwidth ->
            builder.addLimit(bandwidth)
        }
        return builder.build()
    }
}
