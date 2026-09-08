package com.profiletailors.smp.publishing.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class NotificationEventTest {

    private val now = Instant.parse("2026-06-15T12:00:00Z")
    private val createdAt = Instant.parse("2026-06-15T12:01:00Z")

    @Test
    fun `NotificationEvent stores all fields`() {
        val event = NotificationEvent(
            id = "ne-1",
            workspaceId = "ws-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "acc-1",
            publicationId = "pub-1",
            category = NotificationCategory.PUBLICATION_SUCCEEDED,
            message = "Published successfully",
            suggestedAction = "View on LinkedIn",
            publicUrl = "https://linkedin.com/post/123",
            occurredAt = now,
            createdAt = createdAt,
        )

        assertEquals("ne-1", event.id)
        assertEquals("ws-1", event.workspaceId)
        assertEquals(SocialProvider.LINKEDIN, event.provider)
        assertEquals("acc-1", event.socialAccountId)
        assertEquals("pub-1", event.publicationId)
        assertEquals(NotificationCategory.PUBLICATION_SUCCEEDED, event.category)
        assertEquals("Published successfully", event.message)
        assertEquals("View on LinkedIn", event.suggestedAction)
        assertEquals("https://linkedin.com/post/123", event.publicUrl)
        assertEquals(now, event.occurredAt)
        assertEquals(createdAt, event.createdAt)
    }

    @Test
    fun `NotificationEvent nullable fields default to null`() {
        val event = NotificationEvent(
            id = "ne-2",
            workspaceId = "ws-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "acc-1",
            category = NotificationCategory.RECONNECT_REQUIRED,
            message = "Reconnect required",
            occurredAt = now,
        )

        assertNull(event.publicationId)
        assertNull(event.suggestedAction)
        assertNull(event.publicUrl)
        assertNull(event.createdAt)
    }

    @Test
    fun `NotificationEvent works with PUBLICATION_FAILED category`() {
        val event = NotificationEvent(
            id = "ne-3",
            workspaceId = "ws-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "acc-1",
            publicationId = "pub-2",
            category = NotificationCategory.PUBLICATION_FAILED,
            message = "Post failed to publish",
            occurredAt = now,
        )

        assertEquals(NotificationCategory.PUBLICATION_FAILED, event.category)
        assertEquals("pub-2", event.publicationId)
    }

    @Test
    fun `NotificationEvent works with PUBLICATION_BLOCKED category`() {
        val event = NotificationEvent(
            id = "ne-4",
            workspaceId = "ws-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "acc-1",
            publicationId = "pub-3",
            category = NotificationCategory.PUBLICATION_BLOCKED,
            message = "Post blocked by LinkedIn",
            suggestedAction = "Review content policy",
            occurredAt = now,
        )

        assertEquals(NotificationCategory.PUBLICATION_BLOCKED, event.category)
        assertEquals("Review content policy", event.suggestedAction)
    }

    @Test
    fun `NotificationEvent works with CAPABILITY_DENIED category`() {
        val event = NotificationEvent(
            id = "ne-5",
            workspaceId = "ws-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "acc-1",
            category = NotificationCategory.CAPABILITY_DENIED,
            message = "Feature not available for this account type",
            occurredAt = now,
        )

        assertEquals(NotificationCategory.CAPABILITY_DENIED, event.category)
        assertNull(event.publicationId)
    }

    @Test
    fun `NotificationEvent works with MEDIA_PROCESSING_FAILED category`() {
        val event = NotificationEvent(
            id = "ne-6",
            workspaceId = "ws-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "acc-1",
            publicationId = "pub-4",
            category = NotificationCategory.MEDIA_PROCESSING_FAILED,
            message = "Image processing failed",
            occurredAt = now,
        )

        assertEquals(NotificationCategory.MEDIA_PROCESSING_FAILED, event.category)
    }

    @Test
    fun `NotificationEvent works with AMBIGUOUS_OUTCOME category`() {
        val event = NotificationEvent(
            id = "ne-7",
            workspaceId = "ws-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "acc-1",
            publicationId = "pub-5",
            category = NotificationCategory.AMBIGUOUS_OUTCOME,
            message = "Could not determine publication status",
            suggestedAction = "Check LinkedIn manually",
            publicUrl = "https://linkedin.com/post/456",
            occurredAt = now,
        )

        assertEquals(NotificationCategory.AMBIGUOUS_OUTCOME, event.category)
        assertEquals("https://linkedin.com/post/456", event.publicUrl)
    }

    @Test
    fun `NotificationCategory has all expected values`() {
        val expected = setOf(
            "PUBLICATION_SUCCEEDED",
            "PUBLICATION_FAILED",
            "PUBLICATION_BLOCKED",
            "RECONNECT_REQUIRED",
            "CAPABILITY_DENIED",
            "MEDIA_PROCESSING_FAILED",
            "RECURRENCE_PAUSED",
            "AMBIGUOUS_OUTCOME",
        )
        assertEquals(expected, NotificationCategory.entries.map { it.name }.toSet())
    }

    @Test
    fun `NotificationEvent equality is based on all fields`() {
        val event1 = NotificationEvent(
            id = "ne-1",
            workspaceId = "ws-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "acc-1",
            category = NotificationCategory.PUBLICATION_SUCCEEDED,
            message = "Success",
            occurredAt = now,
        )
        val event2 = event1.copy()
        val event3 = event1.copy(id = "ne-2")

        assertEquals(event1, event2)
        assertEquals(event1.hashCode(), event2.hashCode())
        assertTrue(event1 != event3)
    }

    @Test
    fun `NotificationEvent copy creates independent instance`() {
        val original = NotificationEvent(
            id = "ne-1",
            workspaceId = "ws-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "acc-1",
            category = NotificationCategory.PUBLICATION_SUCCEEDED,
            message = "Original",
            occurredAt = now,
        )
        val copy = original.copy(message = "Modified")

        assertEquals("Original", original.message)
        assertEquals("Modified", copy.message)
    }

    @Test
    fun `NotificationEvent rejects blank workspaceId`() {
        assertThrows(IllegalArgumentException::class.java) {
            NotificationEvent(
                id = "ne-1",
                workspaceId = "   ",
                provider = SocialProvider.LINKEDIN,
                socialAccountId = "acc-1",
                category = NotificationCategory.PUBLICATION_SUCCEEDED,
                message = "Published",
                occurredAt = now,
            )
        }
    }
}
