package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.smp.publishing.domain.ActivityDensity
import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.NotificationCategory
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import java.time.Instant
import java.time.LocalDate

data class InitiateLinkedInConnectionCommand(val redirectUri: String) :
    CommandWithResult<LinkedInConnectionInitiationResult>

data class LinkedInConnectionInitiationResult(val authorizationUrl: String, val state: String, val expiresAt: Instant)

data class CompleteLinkedInConnectionCommand(
    val authorizationCode: String,
    val redirectUri: String,
    val state: String,
) : CommandWithResult<SocialConnectionResult>

data class ListConnectedChannelsQuery(val status: SocialConnectionStatus? = null) : Query<ConnectedChannelsResponse>

data class ConnectedChannelsResponse(val channels: List<ConnectedSocialChannelSummary>)

data class ConnectedSocialChannelSummary(
    val socialAccountId: String,
    val connectionId: String,
    val provider: SocialProvider,
    val accountKind: SocialAccountKind,
    val displayName: String,
    val status: SocialConnectionStatus,
    val avatarUrl: String? = null,
    val connectedAt: Instant?,
    val lastSyncedAt: Instant?,
)

data class CreatePublicationCommand(
    val socialAccountId: String,
    val title: String? = null,
    val bodyText: String? = null,
    val assetIds: List<String> = emptyList(),
    val scheduleMode: ScheduleMode,
    val scheduledFor: Instant? = null,
    val nextSlotAfter: Instant? = null,
    val priority: Boolean = false,
) : CommandWithResult<PublicationResult>

data class EditPublicationCommand(
    val publicationId: String,
    val title: String? = null,
    val bodyText: String? = null,
    val assetIds: List<String>? = null,
    val scheduleMode: ScheduleMode,
    val scheduledFor: Instant? = null,
    val nextSlotAfter: Instant? = null,
    val priority: Boolean = false,
) : CommandWithResult<PublicationResult>

data class CancelPublicationCommand(val publicationId: String) : CommandWithResult<PublicationResult>

data class DeletePublicationCommand(val publicationId: String) : CommandWithResult<PublicationResult>

data class RetryPublicationCommand(
    val publicationId: String,
    val scheduleMode: ScheduleMode? = null,
    val scheduledFor: Instant? = null,
    val nextSlotAfter: Instant? = null,
    val priority: Boolean? = null,
) : CommandWithResult<PublicationResult>

data class ReschedulePublicationCommand(
    val publicationId: String,
    val scheduleMode: ScheduleMode,
    val scheduledFor: Instant? = null,
    val nextSlotAfter: Instant? = null,
    val priority: Boolean? = null,
) : CommandWithResult<PublicationResult>

data class SocialConnectionResult(
    val connectionId: String,
    val workspaceId: String,
    val provider: SocialProvider,
    val status: SocialConnectionStatus,
    val account: SocialAccountSummary,
)

data class SocialAccountSummary(
    val accountId: String,
    val providerAccountId: String,
    val displayName: String,
    val kind: SocialAccountKind,
    val profileUrn: String?,
)

data class PublicationResult(
    val publicationId: String,
    val workspaceId: String,
    val socialAccountId: String,
    val status: PublicationStatus,
    val scheduleMode: ScheduleMode,
    val priority: Boolean,
    val title: String?,
    val bodyText: String?,
    val assetIds: List<String>,
    val scheduledFor: Instant?,
    val nextSlotAfter: Instant?,
    val externalPublicationId: String? = null,
    val publicUrl: String? = null,
    val publishedAt: Instant? = null,
)

data class PublicationAssetSummary(val assetId: String, val sourceType: AssetSourceType, val mediaType: String)

data class CreateAssetCommand(
    val mediaType: String,
    val sourceType: AssetSourceType,
    val externalUrl: String? = null,
    val originalFilename: String? = null,
) : CommandWithResult<CreateAssetResult>

data class CreateAssetResult(
    val assetId: String,
    val workspaceId: String,
    val sourceType: AssetSourceType,
    val mediaType: String,
    val status: com.profiletailors.smp.publishing.domain.PublicationAssetStatus,
)

// --- Calendar Query DTOs ---

data class GetCalendarPublicationsQuery(
    val from: Instant,
    val to: Instant,
    val status: PublicationStatus? = null,
    val socialAccountId: String? = null,
    val timezone: String = "UTC",
) : Query<CalendarResponse>

data class CalendarResponse(
    val publications: List<CalendarPublicationResult>,
    val conflicts: List<ConflictEntry>,
    val activity: List<ActivityEntry>,
)

data class CalendarPublicationResult(
    val id: String,
    val workspaceId: String,
    val socialAccountId: String,
    val provider: SocialProvider,
    val status: PublicationStatus,
    val scheduleMode: ScheduleMode,
    val priority: Boolean,
    val title: String?,
    val bodyText: String?,
    val assetIds: List<String>,
    val scheduledFor: Instant?,
    val hasConflict: Boolean,
    val conflictingPublicationIds: List<String>,
    val externalPublicationId: String? = null,
    val publicUrl: String? = null,
    val publishedAt: Instant? = null,
    val previewUrl: String? = null,
    val blockedReason: String? = null,
    val errorCode: String? = null,
)

data class ConflictEntry(
    val publicationId: String,
    val conflictingPublicationIds: List<String>,
    val reason: String = "OVERLAPPING_SCHEDULE",
)

data class ActivityEntry(val date: LocalDate, val density: ActivityDensity, val count: Int)

// --- List Publications Query DTOs ---

data class ListPublicationsQuery(
    val status: PublicationStatus? = null,
    val socialAccountId: String? = null,
    val from: Instant? = null,
    val to: Instant? = null,
    val limit: Int = 50,
    val offset: Int = 0,
) : Query<ListPublicationsResponse>

data class ListPublicationsResponse(val publications: List<ListPublicationItem>, val total: Int)

data class ListPublicationItem(
    val id: String,
    val workspaceId: String,
    val socialAccountId: String,
    val provider: SocialProvider,
    val status: PublicationStatus,
    val title: String?,
    val bodyText: String?,
    val scheduledFor: Instant?,
    val publishedAt: Instant?,
    val publicUrl: String?,
    val externalPublicationId: String?,
    val failedAt: Instant?,
    val lastErrorCode: String?,
    val lastErrorMessage: String?,
    val blockedReason: String?,
    val createdAt: Instant?,
)

// --- Notification Event DTOs ---

data class ListNotificationEventsQuery(
    val socialAccountId: String? = null,
    val publicationId: String? = null,
    val categories: Set<NotificationCategory>? = null,
    val limit: Int = 50,
) : Query<NotificationEventsResponse>

data class NotificationEventsResponse(val events: List<NotificationEventItem>)

data class NotificationEventItem(
    val id: String,
    val provider: SocialProvider,
    val socialAccountId: String,
    val publicationId: String?,
    val category: NotificationCategory,
    val message: String,
    val suggestedAction: String?,
    val publicUrl: String?,
    val occurredAt: Instant,
)
