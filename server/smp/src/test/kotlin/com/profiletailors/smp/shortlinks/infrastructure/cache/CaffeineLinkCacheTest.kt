package com.profiletailors.smp.shortlinks.infrastructure.cache

import com.profiletailors.smp.shortlinks.domain.CacheLookup
import com.profiletailors.smp.shortlinks.domain.DestinationUrl
import com.profiletailors.smp.shortlinks.domain.LinkId
import com.profiletailors.smp.shortlinks.domain.LinkStatus
import com.profiletailors.smp.shortlinks.domain.RedirectEntry
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

internal class CaffeineLinkCacheTest {
    private val now = Instant.parse("2026-10-01T09:00:00Z")

    @Test
    fun `misses on empty cache`() = runBlocking {
        val cache = CaffeineLinkCache(Clock.fixed(now, ZoneOffset.UTC))

        assertEquals(CacheLookup.Miss, cache.get("short.example", "AbC123"))
    }

    @Test
    fun `roundtrips a present entry`() = runBlocking {
        val cache = CaffeineLinkCache(Clock.fixed(now, ZoneOffset.UTC))
        val entry = entry(expiresAt = now.plusSeconds(60))

        cache.put("short.example", "AbC123", entry)

        assertEquals(CacheLookup.Present(entry), cache.get("short.example", "AbC123"))
    }

    @Test
    fun `invalidates present entry once it expires`() = runBlocking {
        val clock = mockk<Clock>()
        every { clock.instant() } returnsMany listOf(now, now.plusSeconds(61))
        val cache = CaffeineLinkCache(clock)

        cache.put("short.example", "AbC123", entry(expiresAt = now.plusSeconds(60)))

        assertEquals(CacheLookup.Miss, cache.get("short.example", "AbC123"))
    }

    @Test
    fun `does not cache entries that already expired`() = runBlocking {
        val cache = CaffeineLinkCache(Clock.fixed(now, ZoneOffset.UTC))

        cache.put("short.example", "AbC123", entry(expiresAt = now.minusSeconds(1)))

        assertEquals(CacheLookup.Miss, cache.get("short.example", "AbC123"))
    }

    @Test
    fun `roundtrips a negative entry`() = runBlocking {
        val cache = CaffeineLinkCache(Clock.fixed(now, ZoneOffset.UTC))

        cache.putNegative("short.example", "Missing1")

        assertEquals(CacheLookup.Negative, cache.get("short.example", "Missing1"))
    }

    @Test
    fun `evicts present entries`() = runBlocking {
        val cache = CaffeineLinkCache(Clock.fixed(now, ZoneOffset.UTC))
        cache.put("short.example", "AbC123", entry(expiresAt = null))

        cache.evict("short.example", "AbC123")

        assertEquals(CacheLookup.Miss, cache.get("short.example", "AbC123"))
    }

    @Test
    fun `evicts negative entries`() = runBlocking {
        val cache = CaffeineLinkCache(Clock.fixed(now, ZoneOffset.UTC))
        cache.putNegative("short.example", "Missing1")

        cache.evict("short.example", "Missing1")

        assertEquals(CacheLookup.Miss, cache.get("short.example", "Missing1"))
    }

    @Test
    fun `present entry overwrites negative entry`() = runBlocking {
        val cache = CaffeineLinkCache(Clock.fixed(now, ZoneOffset.UTC))
        val entry = entry(expiresAt = null)
        cache.putNegative("short.example", "AbC123")

        cache.put("short.example", "AbC123", entry)

        assertTrue(cache.get("short.example", "AbC123") is CacheLookup.Present)
    }

    private fun entry(expiresAt: Instant?) = RedirectEntry(
        linkId = LinkId(UUID.fromString("0199b1ca-0000-7000-8000-000000000001")),
        destination = DestinationUrl("https://example.com/path"),
        status = LinkStatus.ACTIVE,
        expiresAt = expiresAt,
        version = 1,
    )
}
