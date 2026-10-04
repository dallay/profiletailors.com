package com.profiletailors.smp.shortlinks.infrastructure.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import com.profiletailors.smp.shortlinks.domain.CacheLookup
import com.profiletailors.smp.shortlinks.domain.LinkCachePort
import com.profiletailors.smp.shortlinks.domain.RedirectEntry
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

private const val MAX_CACHE_ENTRIES = 10_000L
private const val DEFAULT_CACHE_TTL_SECONDS = 30L

@Component
class CaffeineLinkCache(private val clock: Clock) : LinkCachePort {
    private val cache: Cache<String, CacheValue> = Caffeine.newBuilder()
        .maximumSize(MAX_CACHE_ENTRIES)
        .expireAfter(object : Expiry<String, CacheValue> {
            override fun expireAfterCreate(key: String, value: CacheValue, currentTime: Long): Long =
                value.ttl.toNanos()

            override fun expireAfterUpdate(
                key: String,
                value: CacheValue,
                currentTime: Long,
                currentDuration: Long,
            ): Long = value.ttl.toNanos()

            override fun expireAfterRead(
                key: String,
                value: CacheValue,
                currentTime: Long,
                currentDuration: Long,
            ): Long = currentDuration
        })
        .build()

    override suspend fun get(domain: String, shortCode: String): CacheLookup {
        val value = cache.getIfPresent(key(domain, shortCode))
        return when (value) {
            is CacheValue.Present -> if (value.entry.expiresAt?.let { !clock.instant().isBefore(it) } == true) {
                cache.invalidate(key(domain, shortCode))
                CacheLookup.Miss
            } else {
                CacheLookup.Present(value.entry)
            }
            is CacheValue.Negative -> CacheLookup.Negative
            null -> CacheLookup.Miss
        }
    }

    override suspend fun put(domain: String, shortCode: String, entry: RedirectEntry) {
        val remaining = entry.expiresAt?.let { Duration.between(clock.instant(), it) }
        if (remaining != null && remaining.isNegative || remaining?.isZero == true) {
            cache.invalidate(key(domain, shortCode))
            return
        }
        val defaultTtl = Duration.ofSeconds(DEFAULT_CACHE_TTL_SECONDS)
        val lifetime = remaining?.coerceAtMost(defaultTtl) ?: defaultTtl
        cache.put(key(domain, shortCode), CacheValue.Present(entry, lifetime))
    }

    override suspend fun putNegative(domain: String, shortCode: String) {
        cache.put(key(domain, shortCode), CacheValue.Negative(Duration.ofSeconds(DEFAULT_CACHE_TTL_SECONDS)))
    }

    override suspend fun evict(domain: String, shortCode: String) {
        cache.invalidate(key(domain, shortCode))
    }

    private fun key(domain: String, shortCode: String): String = "redirect:$domain:$shortCode"

    private sealed interface CacheValue {
        val ttl: Duration

        data class Present(val entry: RedirectEntry, override val ttl: Duration) : CacheValue
        data class Negative(override val ttl: Duration) : CacheValue
    }
}
