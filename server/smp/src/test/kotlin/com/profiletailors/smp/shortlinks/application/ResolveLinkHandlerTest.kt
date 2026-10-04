package com.profiletailors.smp.shortlinks.application

import com.profiletailors.smp.shortlinks.domain.CacheLookup
import com.profiletailors.smp.shortlinks.domain.DestinationUrl
import com.profiletailors.smp.shortlinks.domain.DomainId
import com.profiletailors.smp.shortlinks.domain.Link
import com.profiletailors.smp.shortlinks.domain.LinkCachePort
import com.profiletailors.smp.shortlinks.domain.LinkFinderRepository
import com.profiletailors.smp.shortlinks.domain.LinkId
import com.profiletailors.smp.shortlinks.domain.LinkStatus
import com.profiletailors.smp.shortlinks.domain.OwnerId
import com.profiletailors.smp.shortlinks.domain.RedirectEntry
import com.profiletailors.smp.shortlinks.domain.ShortCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

internal class ResolveLinkHandlerTest {
    private val now = Instant.parse("2026-10-01T09:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val linkCachePort = mockk<LinkCachePort>()
    private val linkFinderRepository = mockk<LinkFinderRepository>()
    private val properties = mockk<ShortLinksConfigProperties> {
        every { publicHost } returns "short.example"
    }
    private val domain = DomainId.fromHost("short.example")
    private val linkId = LinkId(UUID.fromString("0199b1ca-0000-7000-8000-000000000001"))

    @Test
    fun `returns destination for cached active entry without hitting repository`() = runBlocking {
        val entry = entry(status = LinkStatus.ACTIVE, expiresAt = null)
        coEvery { linkCachePort.get("short.example", "AbC123") } returns CacheLookup.Present(entry)

        val result = handler().handle(ResolveLinkQuery("short.example", "AbC123"))

        assertEquals("https://example.com/path", result.destinationUrl)
        assertEquals("ACTIVE", result.status)
        coVerify(exactly = 0) {
            linkFinderRepository.findByDomainAndShortCode(domain, ShortCode("AbC123"))
        }
    }

    @Test
    fun `treats blank domain as public host`() = runBlocking {
        val entry = entry(status = LinkStatus.ACTIVE, expiresAt = null)
        coEvery { linkCachePort.get("short.example", "AbC123") } returns CacheLookup.Present(entry)

        val result = handler().handle(ResolveLinkQuery("   ", "AbC123"))

        assertEquals("https://example.com/path", result.destinationUrl)
        coVerify(exactly = 0) {
            linkFinderRepository.findByDomainAndShortCode(domain, ShortCode("AbC123"))
        }
    }

    @Test
    fun `returns not found for cached negative without hitting repository`() = runBlocking {
        coEvery { linkCachePort.get("short.example", "Missing1") } returns CacheLookup.Negative

        try {
            handler().handle(ResolveLinkQuery("short.example", "Missing1"))
            throw AssertionError("Expected link not found")
        } catch (_: LinkNotFoundApplicationException) {
            coVerify(exactly = 0) {
                linkFinderRepository.findByDomainAndShortCode(domain, ShortCode("Missing1"))
            }
        }
    }

    @Test
    fun `caches negative miss and returns not found`() = runBlocking {
        coEvery { linkCachePort.get("short.example", "Missing1") } returns CacheLookup.Miss
        coEvery {
            linkFinderRepository.findByDomainAndShortCode(domain, ShortCode("Missing1"))
        } returns null
        coEvery { linkCachePort.putNegative(any(), any()) } coAnswers { }

        try {
            handler().handle(ResolveLinkQuery("short.example", "Missing1"))
            throw AssertionError("Expected link not found")
        } catch (_: LinkNotFoundApplicationException) {
            coVerify(exactly = 1) { linkCachePort.putNegative("short.example", "Missing1") }
        }
    }

    @Test
    fun `recomputes expiry from cached entry and returns gone`() = runBlocking {
        val entry = entry(status = LinkStatus.ACTIVE, expiresAt = now.minusSeconds(1))
        coEvery { linkCachePort.get("short.example", "AbC123") } returns CacheLookup.Present(entry)

        try {
            handler().handle(ResolveLinkQuery("short.example", "AbC123"))
            throw AssertionError("Expected link expired")
        } catch (_: LinkExpiredApplicationException) {
            coVerify(exactly = 0) {
                linkFinderRepository.findByDomainAndShortCode(domain, ShortCode("AbC123"))
            }
        }
    }

    @Test
    fun `maps cached disabled entry to not found`() = runBlocking {
        coEvery { linkCachePort.get("short.example", "AbC123") } returns
            CacheLookup.Present(entry(status = LinkStatus.DISABLED, expiresAt = null))

        try {
            handler().handle(ResolveLinkQuery("short.example", "AbC123"))
            throw AssertionError("Expected link disabled")
        } catch (_: LinkDisabledApplicationException) {
            coVerify(exactly = 0) {
                linkFinderRepository.findByDomainAndShortCode(domain, ShortCode("AbC123"))
            }
        }
    }

    @Test
    fun `maps cached deleted entry to not found`() = runBlocking {
        coEvery { linkCachePort.get("short.example", "AbC123") } returns
            CacheLookup.Present(entry(status = LinkStatus.DELETED, expiresAt = null))

        try {
            handler().handle(ResolveLinkQuery("short.example", "AbC123"))
            throw AssertionError("Expected link deleted")
        } catch (_: LinkDeletedApplicationException) {
            coVerify(exactly = 0) {
                linkFinderRepository.findByDomainAndShortCode(domain, ShortCode("AbC123"))
            }
        }
    }

    @Test
    fun `maps cached quarantined entry to forbidden`() = runBlocking {
        coEvery { linkCachePort.get("short.example", "AbC123") } returns
            CacheLookup.Present(entry(status = LinkStatus.QUARANTINED, expiresAt = null))

        try {
            handler().handle(ResolveLinkQuery("short.example", "AbC123"))
            throw AssertionError("Expected link quarantined")
        } catch (_: LinkQuarantinedApplicationException) {
            coVerify(exactly = 0) {
                linkFinderRepository.findByDomainAndShortCode(domain, ShortCode("AbC123"))
            }
        }
    }

    @Test
    fun `resolves active link from repository and caches it`() = runBlocking {
        val link = link(expiresAt = null)
        coEvery { linkCachePort.get("short.example", "AbC123") } returns CacheLookup.Miss
        coEvery {
            linkFinderRepository.findByDomainAndShortCode(domain, ShortCode("AbC123"))
        } returns link
        coEvery { linkCachePort.put(any(), any(), any()) } coAnswers { }

        val result = handler().handle(ResolveLinkQuery("short.example", "AbC123"))

        assertEquals("https://example.com/path", result.destinationUrl)
        assertEquals("ACTIVE", result.status)
        assertEquals(link.id.value, result.linkId)
        coVerify(exactly = 1) {
            linkCachePort.put(
                "short.example",
                "AbC123",
                RedirectEntry(linkId, link.destinationUrl, LinkStatus.ACTIVE, null, 1),
            )
        }
    }

    @Test
    fun `maps expired database link to gone`() = runBlocking {
        coEvery { linkCachePort.get("short.example", "AbC123") } returns CacheLookup.Miss
        coEvery {
            linkFinderRepository.findByDomainAndShortCode(domain, ShortCode("AbC123"))
        } returns link(expiresAt = now.minusSeconds(1))
        coEvery { linkCachePort.put(any(), any(), any()) } coAnswers { }

        try {
            handler().handle(ResolveLinkQuery("short.example", "AbC123"))
            throw AssertionError("Expected link expired")
        } catch (_: LinkExpiredApplicationException) {
            coVerify(exactly = 1) {
                linkCachePort.put(
                    "short.example",
                    "AbC123",
                    RedirectEntry(
                        linkId,
                        DestinationUrl("https://example.com/path"),
                        LinkStatus.EXPIRED,
                        now.minusSeconds(1),
                        1,
                    ),
                )
            }
        }
    }

    @Test
    fun `maps disabled database link to not found`() = runBlocking {
        coEvery { linkCachePort.get("short.example", "AbC123") } returns CacheLookup.Miss
        coEvery {
            linkFinderRepository.findByDomainAndShortCode(domain, ShortCode("AbC123"))
        } returns link(expiresAt = null).copy(status = LinkStatus.DISABLED)
        coEvery { linkCachePort.put(any(), any(), any()) } coAnswers { }

        try {
            handler().handle(ResolveLinkQuery("short.example", "AbC123"))
            throw AssertionError("Expected link disabled")
        } catch (_: LinkDisabledApplicationException) {
            coVerify(exactly = 1) {
                linkCachePort.put(
                    "short.example",
                    "AbC123",
                    RedirectEntry(
                        linkId,
                        DestinationUrl("https://example.com/path"),
                        LinkStatus.DISABLED,
                        null,
                        1,
                    ),
                )
            }
        }
    }

    private fun entry(status: LinkStatus, expiresAt: Instant?) = RedirectEntry(
        linkId = linkId,
        destination = DestinationUrl("https://example.com/path"),
        status = status,
        expiresAt = expiresAt,
        version = 1,
    )

    private fun link(expiresAt: Instant?) = Link.create(
        id = linkId,
        ownerId = OwnerId.from("0199b1ca-0000-7000-8000-000000000002"),
        domainId = domain,
        shortCode = ShortCode("AbC123"),
        destinationUrl = DestinationUrl("https://example.com/path"),
        expiresAt = expiresAt,
        now = now,
    )

    private fun handler() = ResolveLinkHandler(linkCachePort, linkFinderRepository, clock, properties)
}
