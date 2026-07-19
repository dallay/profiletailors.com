package com.profiletailors.smp.privacy.infrastructure.http

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * Rate limiter interface for DSAR request throttling.
 *
 * Uses a per-requester counter that resets daily.
 */
fun interface RateLimiter {
    /**
     * Try to acquire a permit for the given [requesterId].
     *
     * @return `true` if the request is allowed, `false` if rate-limited.
     */
    fun tryAcquire(requesterId: String): Boolean
}

/**
 * Default in-memory rate limiter that allows up to [MAX_REQUESTS_PER_DAY]
 * requests per requester per calendar day (UTC).
 *
 * Implementation uses a synchronized concurrent map. Counters are
 * automatically cleaned up as old entries expire. Not suitable for
 * multi-instance deployments — use a Redis-based limiter for scale.
 */
@Component
class DefaultRateLimiter(private val clock: Clock = Clock.systemUTC()) : RateLimiter {

    private val requestCounts = ConcurrentHashMap<String, MutableList<Instant>>()

    @Synchronized
    override fun tryAcquire(requesterId: String): Boolean {
        val now = clock.instant()
        val todayStart = now.atZone(ZoneId.of("UTC")).toLocalDate()
            .atStartOfDay(ZoneId.of("UTC"))
            .toInstant()

        val counts = requestCounts.getOrPut(requesterId) { mutableListOf() }
        // Remove entries from previous days
        counts.removeAll { it.isBefore(todayStart) }

        if (counts.size >= MAX_REQUESTS_PER_DAY) return false
        counts.add(now)
        return true
    }

    companion object {
        /** Maximum DSAR requests per user per calendar day. */
        const val MAX_REQUESTS_PER_DAY = 3
    }
}
