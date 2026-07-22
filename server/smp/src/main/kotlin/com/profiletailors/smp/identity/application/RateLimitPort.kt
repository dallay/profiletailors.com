package com.profiletailors.smp.identity.application

import java.time.Duration
import java.time.Instant

/**
 * Port for rate-limiting destructive operations.
 *
 * Implementations must be thread-safe and support atomic conditional-write
 * semantics so concurrent requests and multiple application instances share
 * the same rate-limit state.
 */
fun interface RateLimitPort {
    /**
     * Attempt to acquire a rate-limit permit for [key].
     *
     * @return true if the permit was acquired (within the window), false if rate-limited.
     */
    fun tryAcquire(key: String, window: Duration, now: Instant): Boolean
}
