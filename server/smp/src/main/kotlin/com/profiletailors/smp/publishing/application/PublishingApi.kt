package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import java.time.Instant

data class CompleteLinkedInConnectionCommand(
    val authorizationCode: String,
    val redirectUri: String,
) : CommandWithResult<SocialConnectionResult>

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
    val assetIds: List<String> = emptyList(),
    val scheduleMode: ScheduleMode,
    val scheduledFor: Instant? = null,
    val nextSlotAfter: Instant? = null,
    val priority: Boolean = false,
) : CommandWithResult<PublicationResult>

data class CancelPublicationCommand(
    val publicationId: String,
) : CommandWithResult<PublicationResult>

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
)

data class PublicationAssetSummary(
    val assetId: String,
    val sourceType: AssetSourceType,
    val mediaType: String,
)

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
