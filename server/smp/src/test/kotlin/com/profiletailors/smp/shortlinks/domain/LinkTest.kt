package com.profiletailors.smp.shortlinks.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

internal class LinkTest {
    private val now = Instant.parse("2026-10-01T09:00:00Z")
    private val link = Link.create(
        id = LinkId(UUID.fromString("0199b1ca-0000-7000-8000-000000000001")),
        ownerId = OwnerId.from("0199b1ca-0000-7000-8000-000000000002"),
        domainId = DomainId(UUID.fromString("0199b1ca-0000-7000-8000-000000000003")),
        shortCode = ShortCode("a91Kd8QaZ2"),
        destinationUrl = DestinationUrl("https://example.com/path?b=2&a=1#part"),
        expiresAt = null,
        now = now,
    )

    @Test
    fun `expiration is inclusive at expiresAt`() {
        val expiringLink = link.copy(expiresAt = now)

        assertTrue(expiringLink.isExpired(now))
        assertEquals(LinkStatus.EXPIRED, expiringLink.effectiveStatus(now))
        assertFalse(expiringLink.isExpired(now.minusNanos(1)))
    }

    @Test
    fun `cannot enable a link after its expiration time`() {
        val disabled = link.copy(expiresAt = now, status = LinkStatus.DISABLED)

        assertThrows(LinkStateTransitionException::class.java) { disabled.enable(now.plusSeconds(1)) }
    }

    @Test
    fun `expired disabled link cannot be reactivated by extending expiration`() {
        val disabled = link.copy(expiresAt = now, status = LinkStatus.DISABLED)

        assertThrows(LinkStateTransitionException::class.java) {
            disabled.updateDestination(
                newDestinationUrl = disabled.destinationUrl,
                newExpiresAt = now.plusSeconds(3600),
                now = now.plusSeconds(1),
            )
        }
    }

    @Test
    fun `destination update preserves identity and short code while advancing version`() {
        val updated = link.updateDestination(
            newDestinationUrl = DestinationUrl("https://example.com/new"),
            newExpiresAt = now.plusSeconds(60),
            now = now.plusSeconds(1),
        )

        assertEquals(link.id, updated.id)
        assertEquals(link.shortCode, updated.shortCode)
        assertEquals(2, updated.version)
        assertEquals(now.plusSeconds(1), updated.updatedAt)
    }

    @Test
    fun `create starts active at version one`() {
        assertEquals(LinkStatus.ACTIVE, link.status)
        assertEquals(1, link.version)
        assertEquals(LinkStatus.ACTIVE, link.effectiveStatus(now))
    }

    @Test
    fun `disabled link keeps disabled effective status when expired`() {
        val disabled = link.copy(status = LinkStatus.DISABLED, expiresAt = now)

        assertEquals(LinkStatus.DISABLED, disabled.effectiveStatus(now.plusSeconds(1)))
    }

    @Test
    fun `disable activates disabled transition`() {
        val disabled = link.disable(now)

        assertEquals(LinkStatus.DISABLED, disabled.status)
        assertEquals(2, disabled.version)
    }

    @Test
    fun `cannot disable twice`() {
        val disabled = link.disable(now)

        assertThrows(LinkStateTransitionException::class.java) { disabled.disable(now.plusSeconds(1)) }
    }

    @Test
    fun `enable reactivates disabled link`() {
        val enabled = link.disable(now).enable(now.plusSeconds(1))

        assertEquals(LinkStatus.ACTIVE, enabled.status)
        assertEquals(3, enabled.version)
    }

    @Test
    fun `cannot enable active link`() {
        assertThrows(LinkStateTransitionException::class.java) { link.enable(now) }
    }

    @Test
    fun `quarantine suspends active link`() {
        val quarantined = link.quarantine(now)

        assertEquals(LinkStatus.QUARANTINED, quarantined.status)
    }

    @Test
    fun `cannot quarantine disabled link`() {
        val disabled = link.disable(now)

        assertThrows(LinkStateTransitionException::class.java) { disabled.quarantine(now.plusSeconds(1)) }
    }

    @Test
    fun `soft delete records deletion`() {
        val deleted = link.softDelete(now)

        assertEquals(LinkStatus.DELETED, deleted.status)
        assertEquals(now, deleted.deletedAt)
    }

    @Test
    fun `cannot delete twice`() {
        val deleted = link.softDelete(now)

        assertThrows(LinkStateTransitionException::class.java) { deleted.softDelete(now.plusSeconds(1)) }
    }

    @Test
    fun `disabled link accepts destination update`() {
        val disabled = link.disable(now)
        val updated = disabled.updateDestination(
            newDestinationUrl = DestinationUrl("https://example.com/other"),
            newExpiresAt = null,
            now = now.plusSeconds(1),
        )

        assertEquals("https://example.com/other", updated.destinationUrl.value)
    }
}
