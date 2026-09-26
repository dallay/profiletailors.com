package com.profiletailors.smp.publishing.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

class PublicationEventContractsTest {

    private val now = Instant.parse("2026-09-25T12:00:00Z")

    @Test
    fun `PublicationEvent stores all fields`() {
        val event = PublicationEvent(
            type = PublicationEventType.STATUS_CHANGED,
            workspaceId = "ws-1",
            publicationId = "pub-1",
            socialAccountId = "acc-1",
            occurredAt = now,
        )

        assertEquals(PublicationEventType.STATUS_CHANGED, event.type)
        assertEquals("ws-1", event.workspaceId)
        assertEquals("pub-1", event.publicationId)
        assertEquals("acc-1", event.socialAccountId)
        assertEquals(now, event.occurredAt)
    }

    @Test
    fun `PublicationEvent socialAccountId defaults to null`() {
        val event = PublicationEvent(
            type = PublicationEventType.CREATED,
            workspaceId = "ws-1",
            publicationId = "pub-1",
            occurredAt = now,
        )

        assertNull(event.socialAccountId)
    }

    @Test
    fun `PublicationEventType covers the frozen emitter matrix`() {
        assertEquals(
            setOf(
                PublicationEventType.CREATED,
                PublicationEventType.UPDATED,
                PublicationEventType.RESCHEDULED,
                PublicationEventType.DELETED,
                PublicationEventType.STATUS_CHANGED,
            ),
            PublicationEventType.entries.toSet(),
        )
    }

    @Test
    fun `PublicationEventType maps to dotted wire names`() {
        assertEquals("publication.created", PublicationEventType.CREATED.wireName())
        assertEquals("publication.updated", PublicationEventType.UPDATED.wireName())
        assertEquals("publication.rescheduled", PublicationEventType.RESCHEDULED.wireName())
        assertEquals("publication.deleted", PublicationEventType.DELETED.wireName())
        assertEquals("publication.status-changed", PublicationEventType.STATUS_CHANGED.wireName())
    }

    @Test
    fun `PublicationEvent rejects blank workspaceId`() {
        assertThrows(IllegalArgumentException::class.java) {
            PublicationEvent(
                type = PublicationEventType.UPDATED,
                workspaceId = "  ",
                publicationId = "pub-1",
                occurredAt = now,
            )
        }
    }

    @Test
    fun `PublicationEvent rejects blank publicationId`() {
        assertThrows(IllegalArgumentException::class.java) {
            PublicationEvent(
                type = PublicationEventType.UPDATED,
                workspaceId = "ws-1",
                publicationId = "",
                occurredAt = now,
            )
        }
    }

    @Test
    fun `PublicationEventPublisher port exposes publish`() {
        val received = mutableListOf<PublicationEvent>()
        val publisher = PublicationEventPublisher { received.add(it) }
        val event = PublicationEvent(
            type = PublicationEventType.DELETED,
            workspaceId = "ws-1",
            publicationId = "pub-9",
            occurredAt = now,
        )

        publisher.publish(event)

        assertEquals(listOf(event), received)
    }
}
