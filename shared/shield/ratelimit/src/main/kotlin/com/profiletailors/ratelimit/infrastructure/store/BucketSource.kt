package com.profiletailors.ratelimit.infrastructure.store

/**
 * Identifies whether a bucket is backed by a local cache or a distributed store.
 */
enum class BucketSource {
    LOCAL,
    DISTRIBUTED,
}
