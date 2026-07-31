package com.profiletailors.ratelimit.infrastructure.store

import io.github.bucket4j.Bucket
import io.github.bucket4j.BucketConfiguration

/**
 * Abstraction for bucket state storage.
 *
 * Implementations may keep buckets in-process (local) or in a distributed backend.
 */
interface RateLimitStore {
    val source: BucketSource

    suspend fun resolveBucket(cacheKey: String, configuration: BucketConfiguration): Bucket
}
