package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.NotificationCategory
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class PublishingApiTest {

    private val now = Instant.parse("2026-06-15T12:00:00Z")

    @Test
    fun `CreatePublicationCommand stores all fields`() {
        val command = CreatePublicationCommand(
            socialAccountId = "acc-1",
            title = "My Post",
            bodyText = "Hello world",
            assetIds = listOf("asset-1", "asset-2"),
            scheduleMode = ScheduleMode.SCHEDULED_AT,
            scheduledFor = now,
            nextSlotAfter = now,
            priority = true,
        )

        assertEquals("acc-1", command.socialAccountId)
        assertEquals("My Post", command.title)
        assertEquals("Hello world", command.bodyText)
        assertEquals(listOf("asset-1", "asset-2"), command.assetIds)
        assertEquals(ScheduleMode.SCHEDULED_AT, command.scheduleMode)
        assertEquals(now, command.scheduledFor)
        assertEquals(now, command.nextSlotAfter)
        assertTrue(command.priority)
    }

    @Test
    fun `CreatePublicationCommand defaults`() {
        val command = CreatePublicationCommand(
            socialAccountId = "acc-1",
            scheduleMode = ScheduleMode.NOW,
        )

        assertNull(command.title)
        assertNull(command.bodyText)
        assertEquals(emptyList<String>(), command.assetIds)
        assertNull(command.scheduledFor)
        assertNull(command.nextSlotAfter)
        assertEquals(false, command.priority)
    }

    @Test
    fun `EditPublicationCommand stores all fields`() {
        val command = EditPublicationCommand(
            publicationId = "pub-1",
            title = "Updated",
            bodyText = "New body",
            assetIds = listOf("a-1"),
            scheduleMode = ScheduleMode.SCHEDULED_AT,
            scheduledFor = now,
            nextSlotAfter = now,
            priority = true,
        )

        assertEquals("pub-1", command.publicationId)
        assertEquals("Updated", command.title)
        assertEquals("New body", command.bodyText)
        assertEquals(ScheduleMode.SCHEDULED_AT, command.scheduleMode)
        assertTrue(command.priority)
    }

    @Test
    fun `CancelPublicationCommand stores publicationId`() {
        val command = CancelPublicationCommand(publicationId = "pub-1")
        assertEquals("pub-1", command.publicationId)
    }

    @Test
    fun `RetryPublicationCommand stores all fields with nullable overrides`() {
        val command = RetryPublicationCommand(
            publicationId = "pub-1",
            scheduleMode = ScheduleMode.NOW,
            scheduledFor = now,
            nextSlotAfter = now,
            priority = true,
        )

        assertEquals("pub-1", command.publicationId)
        assertEquals(ScheduleMode.NOW, command.scheduleMode)
        assertEquals(now, command.scheduledFor)
        assertEquals(now, command.nextSlotAfter)
        assertEquals(true, command.priority)
    }

    @Test
    fun `RetryPublicationCommand defaults`() {
        val command = RetryPublicationCommand(publicationId = "pub-1")
        assertNull(command.scheduleMode)
        assertNull(command.scheduledFor)
        assertNull(command.nextSlotAfter)
        assertNull(command.priority)
    }

    @Test
    fun `ReschedulePublicationCommand stores all fields`() {
        val command = ReschedulePublicationCommand(
            publicationId = "pub-1",
            scheduleMode = ScheduleMode.SCHEDULED_AT,
            scheduledFor = now,
            nextSlotAfter = now,
            priority = false,
        )

        assertEquals("pub-1", command.publicationId)
        assertEquals(ScheduleMode.SCHEDULED_AT, command.scheduleMode)
        assertEquals(now, command.scheduledFor)
        assertEquals(false, command.priority)
    }

    @Test
    fun `PublicationResult stores all fields`() {
        val result = PublicationResult(
            publicationId = "pub-1",
            workspaceId = "ws-1",
            socialAccountId = "acc-1",
            status = PublicationStatus.QUEUED,
            scheduleMode = ScheduleMode.NOW,
            priority = true,
            title = "Title",
            bodyText = "Body",
            assetIds = listOf("a-1"),
            scheduledFor = now,
            nextSlotAfter = now,
        )

        assertEquals("pub-1", result.publicationId)
        assertEquals("ws-1", result.workspaceId)
        assertEquals("acc-1", result.socialAccountId)
        assertEquals(PublicationStatus.QUEUED, result.status)
        assertEquals(ScheduleMode.NOW, result.scheduleMode)
        assertTrue(result.priority)
        assertEquals("Title", result.title)
        assertEquals("Body", result.bodyText)
        assertEquals(listOf("a-1"), result.assetIds)
        assertEquals(now, result.scheduledFor)
        assertEquals(now, result.nextSlotAfter)
    }

    @Test
    fun `SocialConnectionResult stores all fields`() {
        val result = SocialConnectionResult(
            connectionId = "conn-1",
            workspaceId = "ws-1",
            provider = SocialProvider.LINKEDIN,
            status = SocialConnectionStatus.ACTIVE,
            account = SocialAccountSummary(
                accountId = "acc-1",
                providerAccountId = "linkedin-123",
                displayName = "Test User",
                kind = SocialAccountKind.PERSONAL_PROFILE,
                profileUrn = "urn:li:person:123",
            ),
        )

        assertEquals("conn-1", result.connectionId)
        assertEquals(SocialProvider.LINKEDIN, result.provider)
        assertEquals(SocialConnectionStatus.ACTIVE, result.status)
        assertEquals("acc-1", result.account.accountId)
        assertEquals(SocialAccountKind.PERSONAL_PROFILE, result.account.kind)
    }

    @Test
    fun `CreateAssetCommand stores all fields`() {
        val command = CreateAssetCommand(
            mediaType = "image/jpeg",
            sourceType = AssetSourceType.UPLOADED,
            externalUrl = "https://example.com/img.jpg",
            originalFilename = "photo.jpg",
        )

        assertEquals("image/jpeg", command.mediaType)
        assertEquals(AssetSourceType.UPLOADED, command.sourceType)
        assertEquals("https://example.com/img.jpg", command.externalUrl)
        assertEquals("photo.jpg", command.originalFilename)
    }

    @Test
    fun `CreateAssetResult stores all fields`() {
        val result = CreateAssetResult(
            assetId = "pa-1",
            workspaceId = "ws-1",
            sourceType = AssetSourceType.EXTERNAL_URL,
            mediaType = "IMAGE/JPEG",
            status = PublicationAssetStatus.READY,
        )

        assertEquals("pa-1", result.assetId)
        assertEquals("ws-1", result.workspaceId)
        assertEquals(AssetSourceType.EXTERNAL_URL, result.sourceType)
        assertEquals("IMAGE/JPEG", result.mediaType)
        assertEquals(PublicationAssetStatus.READY, result.status)
    }

    @Test
    fun `ListPublicationsQuery stores all fields`() {
        val query = ListPublicationsQuery(
            status = PublicationStatus.SCHEDULED,
            socialAccountId = "acc-1",
            from = now,
            to = now,
            limit = 25,
            offset = 5,
        )

        assertEquals(PublicationStatus.SCHEDULED, query.status)
        assertEquals("acc-1", query.socialAccountId)
        assertEquals(now, query.from)
        assertEquals(now, query.to)
        assertEquals(25, query.limit)
        assertEquals(5, query.offset)
    }

    @Test
    fun `ListPublicationsQuery defaults`() {
        val query = ListPublicationsQuery()

        assertNull(query.status)
        assertNull(query.socialAccountId)
        assertNull(query.from)
        assertNull(query.to)
        assertEquals(50, query.limit)
        assertEquals(0, query.offset)
    }

    @Test
    fun `ListPublicationItem stores all fields`() {
        val item = ListPublicationItem(
            id = "pub-1",
            workspaceId = "ws-1",
            socialAccountId = "acc-1",
            provider = SocialProvider.LINKEDIN,
            status = PublicationStatus.PUBLISHED,
            title = "Published",
            bodyText = "Body",
            scheduledFor = now,
            publishedAt = now,
            publicUrl = "https://linkedin.com/post/123",
            externalPublicationId = "ext-1",
            failedAt = null,
            lastErrorCode = null,
            lastErrorMessage = null,
            blockedReason = null,
            createdAt = now,
        )

        assertEquals("pub-1", item.id)
        assertEquals(PublicationStatus.PUBLISHED, item.status)
        assertEquals("https://linkedin.com/post/123", item.publicUrl)
        assertEquals("ext-1", item.externalPublicationId)
        assertNull(item.failedAt)
        assertNull(item.blockedReason)
    }

    @Test
    fun `ListPublicationsResponse stores publications and total`() {
        val response = ListPublicationsResponse(
            publications = emptyList(),
            total = 0,
        )

        assertEquals(emptyList<ListPublicationItem>(), response.publications)
        assertEquals(0, response.total)
    }

    @Test
    fun `GetCalendarPublicationsQuery stores all fields`() {
        val query = GetCalendarPublicationsQuery(
            from = now,
            to = now,
            status = PublicationStatus.SCHEDULED,
            socialAccountId = "acc-1",
            timezone = "Europe/Madrid",
        )

        assertEquals(now, query.from)
        assertEquals(now, query.to)
        assertEquals(PublicationStatus.SCHEDULED, query.status)
        assertEquals("acc-1", query.socialAccountId)
        assertEquals("Europe/Madrid", query.timezone)
    }

    @Test
    fun `CalendarResponse stores publications conflicts and activity`() {
        val response = CalendarResponse(
            publications = emptyList(),
            conflicts = emptyList(),
            activity = emptyList(),
        )

        assertEquals(emptyList<CalendarPublicationResult>(), response.publications)
        assertEquals(emptyList<ConflictEntry>(), response.conflicts)
        assertEquals(emptyList<ActivityEntry>(), response.activity)
    }

    @Test
    fun `CalendarPublicationResult stores all fields`() {
        val result = CalendarPublicationResult(
            id = "pub-1",
            workspaceId = "ws-1",
            socialAccountId = "acc-1",
            provider = SocialProvider.LINKEDIN,
            status = PublicationStatus.SCHEDULED,
            scheduleMode = ScheduleMode.SCHEDULED_AT,
            priority = true,
            title = "Title",
            bodyText = "Body",
            assetIds = listOf("asset-1"),
            scheduledFor = now,
            hasConflict = true,
            conflictingPublicationIds = listOf("pub-2"),
        )

        assertEquals("pub-1", result.id)
        assertTrue(result.hasConflict)
        assertEquals(listOf("asset-1"), result.assetIds)
        assertEquals(listOf("pub-2"), result.conflictingPublicationIds)
    }

    @Test
    fun `ConflictEntry stores all fields with default reason`() {
        val entry = ConflictEntry(
            publicationId = "pub-1",
            conflictingPublicationIds = listOf("pub-2"),
        )

        assertEquals("pub-1", entry.publicationId)
        assertEquals("OVERLAPPING_SCHEDULE", entry.reason)
    }

    @Test
    fun `ActivityEntry stores all fields`() {
        val entry = ActivityEntry(
            date = LocalDate.parse("2026-06-15"),
            density = com.profiletailors.smp.publishing.domain.ActivityDensity.HIGH,
            count = 10,
        )

        assertEquals(LocalDate.parse("2026-06-15"), entry.date)
        assertEquals(com.profiletailors.smp.publishing.domain.ActivityDensity.HIGH, entry.density)
        assertEquals(10, entry.count)
    }

    @Test
    fun `ListNotificationEventsQuery stores all fields`() {
        val query = ListNotificationEventsQuery(
            socialAccountId = "acc-1",
            publicationId = "pub-1",
            categories = setOf(NotificationCategory.PUBLICATION_FAILED),
            limit = 10,
        )

        assertEquals("acc-1", query.socialAccountId)
        assertEquals("pub-1", query.publicationId)
        assertEquals(setOf(NotificationCategory.PUBLICATION_FAILED), query.categories)
        assertEquals(10, query.limit)
    }

    @Test
    fun `ListNotificationEventsQuery defaults`() {
        val query = ListNotificationEventsQuery()

        assertNull(query.socialAccountId)
        assertNull(query.publicationId)
        assertNull(query.categories)
        assertEquals(50, query.limit)
    }

    @Test
    fun `NotificationEventsResponse stores events`() {
        val response = NotificationEventsResponse(events = emptyList())
        assertEquals(emptyList<NotificationEventItem>(), response.events)
    }

    @Test
    fun `NotificationEventItem stores all fields`() {
        val item = NotificationEventItem(
            id = "ne-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "acc-1",
            publicationId = "pub-1",
            category = NotificationCategory.PUBLICATION_SUCCEEDED,
            message = "Published successfully",
            suggestedAction = "View on LinkedIn",
            publicUrl = "https://linkedin.com/post/123",
            occurredAt = now,
        )

        assertEquals("ne-1", item.id)
        assertEquals(SocialProvider.LINKEDIN, item.provider)
        assertEquals(NotificationCategory.PUBLICATION_SUCCEEDED, item.category)
        assertEquals("Published successfully", item.message)
        assertEquals("https://linkedin.com/post/123", item.publicUrl)
    }

    @Test
    fun `NotificationEventItem nullable fields default to null`() {
        val item = NotificationEventItem(
            id = "ne-1",
            provider = SocialProvider.LINKEDIN,
            socialAccountId = "acc-1",
            publicationId = null,
            category = NotificationCategory.RECONNECT_REQUIRED,
            message = "Reconnect needed",
            suggestedAction = null,
            publicUrl = null,
            occurredAt = now,
        )

        assertNull(item.publicationId)
        assertNull(item.suggestedAction)
        assertNull(item.publicUrl)
    }

    @Test
    fun `ConnectedSocialChannelSummary stores all fields`() {
        val summary = ConnectedSocialChannelSummary(
            socialAccountId = "acc-1",
            connectionId = "conn-1",
            provider = SocialProvider.LINKEDIN,
            accountKind = SocialAccountKind.PERSONAL_PROFILE,
            displayName = "Test",
            status = SocialConnectionStatus.ACTIVE,
            avatarUrl = "https://example.com/avatar.jpg",
            connectedAt = now,
            lastSyncedAt = now,
        )

        assertEquals("acc-1", summary.socialAccountId)
        assertEquals("conn-1", summary.connectionId)
        assertEquals(SocialProvider.LINKEDIN, summary.provider)
        assertEquals(SocialAccountKind.PERSONAL_PROFILE, summary.accountKind)
        assertEquals("Test", summary.displayName)
        assertEquals(SocialConnectionStatus.ACTIVE, summary.status)
        assertEquals("https://example.com/avatar.jpg", summary.avatarUrl)
        assertEquals(now, summary.connectedAt)
        assertEquals(now, summary.lastSyncedAt)
    }

    @Test
    fun `ConnectedSocialChannelSummary nullable avatarUrl defaults to null`() {
        val summary = ConnectedSocialChannelSummary(
            socialAccountId = "acc-1",
            connectionId = "conn-1",
            provider = SocialProvider.LINKEDIN,
            accountKind = SocialAccountKind.PERSONAL_PROFILE,
            displayName = "Test",
            status = SocialConnectionStatus.ACTIVE,
            connectedAt = now,
            lastSyncedAt = null,
        )

        assertNull(summary.avatarUrl)
        assertNull(summary.lastSyncedAt)
    }

    @Test
    fun `LinkedInConnectionInitiationResult stores all fields`() {
        val result = LinkedInConnectionInitiationResult(
            authorizationUrl = "https://linkedin.com/auth",
            state = "state-abc",
            expiresAt = now,
        )

        assertEquals("https://linkedin.com/auth", result.authorizationUrl)
        assertEquals("state-abc", result.state)
        assertEquals(now, result.expiresAt)
    }

    @Test
    fun `ConnectedChannelsResponse stores channels`() {
        val response = ConnectedChannelsResponse(channels = emptyList())
        assertEquals(emptyList<ConnectedSocialChannelSummary>(), response.channels)
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
            "AMBIGUOUS_OUTCOME",
        )
        assertEquals(expected, NotificationCategory.entries.map { it.name }.toSet())
    }
}
