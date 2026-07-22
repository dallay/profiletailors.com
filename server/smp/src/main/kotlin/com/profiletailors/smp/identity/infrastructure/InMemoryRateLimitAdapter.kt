package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.RateLimitPort
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory rate-limit adapter using atomic conditional-write semantics.
 *
 * Thread-safe within a single instance. For multi-instance deployments,
 * replace with a shared store (e.g. Redis compare-and-set).
 */
class InMemoryRateLimitAdapter(
/** Leeway to absorb clock-skew between instances. */
    private val clockSkewLeeway: Duration = CLOCK_SKEW_LEEWAY,
) : RateLimitPort {

    /** Keyed by principal ID → most recent attempt timestamp. */
    private val store: MutableMap<String, Instant> = ConcurrentHashMap()

    override fun tryAcquire(key: String, window: Duration, now: Instant): Boolean {
        val adjustedNow = now.plus(clockSkewLeeway)
        val deadline = adjustedNow.minus(window)

        // Atomic conditional write: only update if the stored value is older than the window.
        // Using compute to avoid TOCTOU race between get/put.
        val result = store.compute(key) { _, lastAttempt ->
            if (lastAttempt != null && lastAttempt.isAfter(deadline)) {
                // Still within the window — reject
                lastAttempt
            } else {
                // Outside the window — accept and stamp
                adjustedNow
            }
        }
        // If the returned value equals adjustedNow, we acquired the permit.
        return result == adjustedNow
    }

    companion object {
        private val CLOCK_SKEW_LEEWAY: Duration = Duration.ofSeconds(5)
    }
}
