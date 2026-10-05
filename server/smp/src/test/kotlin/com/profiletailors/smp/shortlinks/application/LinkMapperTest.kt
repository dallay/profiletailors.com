package com.profiletailors.smp.shortlinks.application

import com.profiletailors.smp.shortlinks.domain.DestinationUrl
import com.profiletailors.smp.shortlinks.domain.DomainId
import com.profiletailors.smp.shortlinks.domain.Link
import com.profiletailors.smp.shortlinks.domain.LinkId
import com.profiletailors.smp.shortlinks.domain.LinkStatus
import com.profiletailors.smp.shortlinks.domain.OwnerId
import com.profiletailors.smp.shortlinks.domain.ShortCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

internal class LinkMapperTest {
    @Test
    fun `maps link to result with composed short url`() {
        val now = Instant.parse("2026-10-01T09:00:00Z")
        val link = Link(
            id = LinkId(UUID.fromString("0199b1ca-0000-7000-8000-000000000001")),
            ownerId = OwnerId.from("0199b1ca-0000-7000-8000-000000000002"),
            domainId = DomainId.fromHost("short.example"),
            shortCode = ShortCode("AbC123"),
            destinationUrl = DestinationUrl("https://example.com/path"),
            status = LinkStatus.ACTIVE,
            expiresAt = now.plusSeconds(60),
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            version = 3,
        )

        val result = link.toResult("https://short.example")

        assertEquals(link.id.value, result.id)
        assertEquals("AbC123", result.shortCode)
        assertEquals("https://short.example/AbC123", result.shortUrl)
        assertEquals("https://example.com/path", result.destinationUrl)
        assertEquals("ACTIVE", result.status)
        assertEquals(now.plusSeconds(60), result.expiresAt)
        assertEquals(3, result.version)
    }

    @Test
    fun `maps link without expiry`() {
        val now = Instant.parse("2026-10-01T09:00:00Z")
        val link = Link.create(
            id = LinkId(UUID.fromString("0199b1ca-0000-7000-8000-000000000001")),
            ownerId = OwnerId.from("0199b1ca-0000-7000-8000-000000000002"),
            domainId = DomainId.fromHost("short.example"),
            shortCode = ShortCode("AbC123"),
            destinationUrl = DestinationUrl("https://example.com/path"),
            expiresAt = null,
            now = now,
        )

        val result = link.toResult("https://short.example")

        assertNull(result.expiresAt)
        assertEquals(1, result.version)
    }
}
