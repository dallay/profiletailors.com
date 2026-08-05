package com.profiletailors.ratelimit.infrastructure.store

import io.github.bucket4j.Bucket
import io.github.bucket4j.BucketConfiguration

/**
 * Strategy interface for resolving and managing Bucket4j buckets.
 */
interface RateLimitStore {
    val source: BucketSource

    fun resolveBucket(cacheKey: String, configuration: BucketConfiguration): Bucket

    fun close()

    fun getStats(): RateLimitStoreStats = RateLimitStoreStats()
}

/**
 * Lightweight stats container for store implementations.
 */
data class RateLimitStoreStats(val size: Long = 0, val hitCount: Long = 0, val missCount: Long = 0)
