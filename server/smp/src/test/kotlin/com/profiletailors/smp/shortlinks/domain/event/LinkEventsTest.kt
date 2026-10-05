package com.profiletailors.smp.shortlinks.domain.event

import com.profiletailors.smp.shortlinks.domain.DomainId
import com.profiletailors.smp.shortlinks.domain.LinkId
import com.profiletailors.smp.shortlinks.domain.LinkStatus
import com.profiletailors.smp.shortlinks.domain.ShortCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

internal class LinkEventsTest {
    private val linkId = LinkId(UUID.fromString("0199b1ca-0000-7000-8000-000000000001"))
    private val domainId = DomainId(UUID.fromString("0199b1ca-0000-7000-8000-000000000002"))
    private val occurredAt = Instant.parse("2026-10-01T09:00:00Z")

    @Test
    fun `created event carries identity`() {
        val event = LinkCreatedEvent(linkId, domainId, ShortCode("AbC123"), 1, occurredAt)

        assertEquals(linkId, event.linkId)
        assertEquals(ShortCode("AbC123"), event.copy(version = 2).shortCode)
    }

    @Test
    fun `changed event carries new status`() {
        val event = LinkChangedEvent(linkId, domainId, ShortCode("AbC123"), LinkStatus.DISABLED, 2, occurredAt)

        assertEquals(LinkStatus.DISABLED, event.newStatus)
        assertEquals(2, event.version)
    }

    @Test
    fun `click event carries optional attribution`() {
        val event = ClickRecordedEvent("evt-1", linkId, occurredAt, "example.com", null)

        assertEquals("evt-1", event.eventId)
        assertEquals("example.com", event.copy(userAgent = "agent").referrerDomain)
    }
}
