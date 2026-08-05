package com.profiletailors.ratelimit.infrastructure.store

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.profiletailors.ratelimit.infrastructure.config.RateLimitProperties
import io.github.bucket4j.Bucket
import io.github.bucket4j.BucketConfiguration
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Local in-process implementation using Caffeine.
 */
class LocalCaffeineRateLimitStore(private val properties: RateLimitProperties) : RateLimitStore {
    override val source: BucketSource = BucketSource.LOCAL

    private val logger = LoggerFactory.getLogger(LocalCaffeineRateLimitStore::class.java)

    private val cache: Cache<String, Bucket> = Caffeine.newBuilder()
        .maximumSize(properties.cache.maxSize)
        .expireAfterAccess(Duration.ofMinutes(properties.cache.ttlMinutes))
        .removalListener<String, Bucket> { key, _, cause ->
            logger.debug("Cache eviction: key={}, cause={}", key, cause)
        }
        .recordStats()
        .build()

    override fun resolveBucket(cacheKey: String, configuration: BucketConfiguration): Bucket = cache.get(cacheKey) {
        val builder = Bucket.builder()
        configuration.bandwidths.forEach { bandwidth ->
            builder.addLimit(bandwidth)
        }
        builder.build()
    }

    fun estimatedSize(): Long = cache.estimatedSize()

    fun stats() = cache.stats()

    fun triggerCleanup() = cache.cleanUp()

    fun clear() {
        cache.invalidateAll()
        cache.cleanUp()
    }

    override fun close() = Unit
}
