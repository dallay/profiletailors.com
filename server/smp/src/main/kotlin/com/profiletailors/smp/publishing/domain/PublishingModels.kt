package com.profiletailors.smp.publishing.domain

import java.time.Instant

enum class SocialProvider {
    LINKEDIN,
}

enum class SocialConnectionStatus {
    ACTIVE,
    REVOKED,
    EXPIRED,
    ERROR,
}

enum class SocialAccountKind {
    PERSONAL_PROFILE,
    ORGANIZATION_PAGE,
}

enum class PublicationStatus {
    DRAFT,
    QUEUED,
    SCHEDULED,
    PROCESSING,
    PUBLISHED,
    FAILED,
    CANCELLED,
}

enum class ScheduleMode {
    NOW,
    SCHEDULED_AT,
    NEXT_SLOT,
}

enum class JobStatus {
    PENDING,
    CLAIMED,
    RETRY_WAITING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

enum class AssetSourceType {
    UPLOADED,
    EXTERNAL_URL,
}

enum class PublicationAssetStatus {
    READY,
    PROCESSING,
    FAILED,
}

enum class DeliveryAttemptOutcome {
    SUCCEEDED,
    FAILED,
}

data class SocialConnection(
    val id: String,
    val workspaceId: String,
    val provider: SocialProvider,
    val providerConnectionRef: String,
    val status: SocialConnectionStatus,
    val credentialReference: String? = null,
    val connectedAt: Instant? = null,
    val lastSyncedAt: Instant? = null,
    val createdAt: Instant? = null,
)

data class SocialAccount(
    val id: String,
    val socialConnectionId: String,
    val workspaceId: String,
    val provider: SocialProvider,
    val providerAccountId: String,
    val kind: SocialAccountKind,
    val displayName: String,
    val profileUrn: String? = null,
    val status: SocialConnectionStatus,
    val createdAt: Instant? = null,
)

data class PublicationAsset(
    val id: String,
    val workspaceId: String,
    val sourceType: AssetSourceType,
    val mediaType: String,
    val storageKey: String? = null,
    val externalUrl: String? = null,
    val originalFilename: String? = null,
    val status: PublicationAssetStatus,
    val createdByPrincipalId: String,
    val createdAt: Instant? = null,
) {
    init {
        when (sourceType) {
            AssetSourceType.UPLOADED -> require(!storageKey.isNullOrBlank()) {
                "Uploaded assets require a storage key."
            }
            AssetSourceType.EXTERNAL_URL -> require(!externalUrl.isNullOrBlank()) {
                "External assets require a source URL."
            }
        }
    }
}

data class PublicationDraft(
    val id: String,
    val workspaceId: String,
    val authorPrincipalId: String,
    val provider: SocialProvider,
    val socialAccountId: String,
    val status: PublicationStatus,
    val scheduleMode: ScheduleMode,
    val priority: Boolean,
    val title: String? = null,
    val bodyText: String? = null,
    val assetIds: List<String> = emptyList(),
    val scheduledFor: Instant? = null,
    val nextSlotAfter: Instant? = null,
    val publishedAt: Instant? = null,
    val failedAt: Instant? = null,
    val externalPublicationId: String? = null,
    val lastErrorCode: String? = null,
    val lastErrorMessage: String? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    fun queueableStatus(): PublicationStatus = when (scheduleMode) {
        ScheduleMode.NOW -> PublicationStatus.QUEUED
        ScheduleMode.SCHEDULED_AT, ScheduleMode.NEXT_SLOT -> PublicationStatus.SCHEDULED
    }
}

data class PublicationJob(
    val id: String,
    val publicationId: String,
    val workspaceId: String,
    val status: JobStatus,
    val dueAt: Instant,
    val priorityRank: Int,
    val attemptCount: Int,
    val maxAttempts: Int,
    val claimedByWorker: String? = null,
    val claimedAt: Instant? = null,
    val leaseExpiresAt: Instant? = null,
    val completedAt: Instant? = null,
    val failedAt: Instant? = null,
    val cancelledAt: Instant? = null,
    val createdAt: Instant? = null,
)

data class PublicationJobClaim(
    val jobId: String,
    val publicationId: String,
    val workspaceId: String,
    val attemptNumber: Int,
    val claimedAt: Instant,
)

data class DeliveryAttempt(
    val id: String,
    val publicationId: String,
    val publicationJobId: String,
    val attemptNumber: Int,
    val outcome: DeliveryAttemptOutcome,
    val retryable: Boolean,
    val providerMessage: String? = null,
    val providerErrorCode: String? = null,
    val externalPublicationId: String? = null,
    val attemptedAt: Instant,
    val createdAt: Instant? = null,
)
