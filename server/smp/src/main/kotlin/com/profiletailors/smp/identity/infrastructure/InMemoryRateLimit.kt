package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.RateLimit
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory rate-limit adapter using atomic conditional-write semantics.
 *
 * Thread-safe within a single instance. For multi-instance deployments,
 * replace with a shared store (e.g. Redis compare-and-set).
 */
class InMemoryRateLimit(
/** Leeway to absorb clock-skew between instances. */
    private val clockSkewLeeway: Duration = CLOCK_SKEW_LEEWAY,
) : RateLimit {

    /** Keyed by principal ID → most recent attempt timestamp. */
    private val store: MutableMap<String, Window> = ConcurrentHashMap()

    override fun tryAcquire(key: String, window: Duration, now: Instant): Boolean = tryAcquire(key, window, now, 1)

    override fun tryAcquire(key: String, window: Duration, now: Instant, maxRequests: Int): Boolean {
        require(maxRequests > 0)
        val adjustedNow = now.plus(clockSkewLeeway)
        val deadline = adjustedNow.minus(window)

        // Atomic conditional write: only update if the stored value is older than the window.
        // Using compute to avoid TOCTOU race between get/put.
        val result = store.compute(key) { _, current ->
            if (current != null && current.lastAttempt.isAfter(deadline)) {
                if (current.count < maxRequests) Window(adjustedNow, current.count + 1) else current
            } else {
                Window(adjustedNow, 1)
            }
        }
        return result?.lastAttempt == adjustedNow && result.count <= maxRequests
    }

    fun clear() = store.clear()

    private data class Window(val lastAttempt: Instant, val count: Int)

    companion object {
        private val CLOCK_SKEW_LEEWAY: Duration = Duration.ofSeconds(5)
    }
}
