package com.profiletailors.smp.publishing.domain

import com.profiletailors.common.domain.AggregateRoot
import com.profiletailors.common.domain.ValueObject
import java.time.Instant
import java.time.LocalDate

@ValueObject
enum class SocialProvider {
    LINKEDIN,
}

@ValueObject
enum class SocialConnectionStatus {
    PENDING,
    ACTIVE,
    DISABLED,
    REQUIRES_RECONNECT,
    DELETED,
    ERROR,

    // Legacy values kept for backward compatibility
    REVOKED,
    EXPIRED,
}

@ValueObject
enum class SocialAccountKind {
    PERSONAL_PROFILE,
    ORGANIZATION_PAGE,
}

@ValueObject
enum class PublicationStatus {
    DRAFT,
    QUEUED,
    SCHEDULED,
    PROCESSING,
    PUBLISHED,
    BLOCKED,
    FAILED,
    CANCELLED,
}

@ValueObject
enum class ScheduleMode {
    NOW,
    SCHEDULED_AT,
    NEXT_SLOT,
}

@ValueObject
enum class JobStatus {
    PENDING,
    CLAIMED,
    RETRY_WAITING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

@ValueObject
enum class AssetSourceType {
    UPLOADED,
    EXTERNAL_URL,
}

@ValueObject
enum class PublicationAssetStatus {
    READY,
    PROCESSING,
    FAILED,
}

@ValueObject
enum class DeliveryAttemptOutcome {
    SUCCEEDED,
    FAILED,
    IN_PROGRESS,
}

enum class DeliveryAttemptPhase {
    VALIDATE,
    PROVIDER_CREATE,
    PROVIDER_UPDATE,
    RECORD_RESULT,
    FINALIZATION,
}

@AggregateRoot
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

@AggregateRoot
data class SocialAccount(
    val id: String,
    val socialConnectionId: String,
    val workspaceId: String,
    val provider: SocialProvider,
    val providerAccountId: String,
    val kind: SocialAccountKind,
    val displayName: String,
    val profileUrn: String? = null,
    val avatarUrl: String? = null,
    val status: SocialConnectionStatus,
    val createdAt: Instant? = null,
)

data class ProviderAssetRef(val providerAssetId: String, val mediaType: String, val accessUrl: String? = null)

@AggregateRoot
data class PublicationAsset(
    val id: String,
    val workspaceId: String,
    val sourceType: AssetSourceType,
    val mediaType: String,
    val storageKey: String? = null,
    val externalUrl: String? = null,
    val originalFilename: String? = null,
    val fileSizeBytes: Long? = null,
    val status: PublicationAssetStatus,
    val providerAssetRef: ProviderAssetRef? = null,
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

@AggregateRoot
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
    val publicUrl: String? = null,
    val blockedAt: Instant? = null,
    val blockedReason: String? = null,
    val retryCount: Int = 0,
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

data class DateCount(val date: LocalDate, val count: Int)

@AggregateRoot
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
    val leaseExpiresAt: Instant? = null,
    val operationKey: String? = null,
    val claimVersion: Long? = null,
)

@AggregateRoot
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
    val operationKey: String? = null,
    val claimVersion: Long? = null,
    val phase: DeliveryAttemptPhase? = null,
)

/**
 * LinkedIn capability bundles for feature-gated publishing.
 * Each bundle maps to a set of required OAuth scopes and a resource kind.
 */
enum class LinkedinCapabilityBundle(
    val requiredScopes: Set<String>,
    val resourceKind: SocialAccountKind,
    val mvpStatus: CapabilityMvpStatus,
) {
    PERSONAL_PROFILE_TEXT(
        requiredScopes = setOf("w_member_social"),
        resourceKind = SocialAccountKind.PERSONAL_PROFILE,
        mvpStatus = CapabilityMvpStatus.SUPPORTED,
    ),
    PERSONAL_PROFILE_IMAGE(
        requiredScopes = setOf("w_member_social"),
        resourceKind = SocialAccountKind.PERSONAL_PROFILE,
        mvpStatus = CapabilityMvpStatus.SUPPORTED,
    ),
    ORG_PAGE_TEXT(
        requiredScopes = setOf("w_organization_social"),
        resourceKind = SocialAccountKind.ORGANIZATION_PAGE,
        mvpStatus = CapabilityMvpStatus.GATED,
    ),
    ORG_PAGE_IMAGE(
        requiredScopes = setOf("w_organization_social"),
        resourceKind = SocialAccountKind.ORGANIZATION_PAGE,
        mvpStatus = CapabilityMvpStatus.GATED,
    ),
    ORG_MENTIONS(
        requiredScopes = setOf("w_organization_social"),
        resourceKind = SocialAccountKind.ORGANIZATION_PAGE,
        mvpStatus = CapabilityMvpStatus.GATED,
    ),
    VIDEO(
        requiredScopes = setOf("w_member_social"),
        resourceKind = SocialAccountKind.PERSONAL_PROFILE,
        mvpStatus = CapabilityMvpStatus.GATED,
    ),
    PDF_DOCUMENT(
        requiredScopes = setOf("w_member_social"),
        resourceKind = SocialAccountKind.PERSONAL_PROFILE,
        mvpStatus = CapabilityMvpStatus.GATED,
    ),
    CAROUSEL(
        requiredScopes = setOf("w_member_social"),
        resourceKind = SocialAccountKind.PERSONAL_PROFILE,
        mvpStatus = CapabilityMvpStatus.GATED,
    ),
    COMMENTS_THREADS(
        requiredScopes = setOf("w_member_social"),
        resourceKind = SocialAccountKind.PERSONAL_PROFILE,
        mvpStatus = CapabilityMvpStatus.UNSUPPORTED,
    ),
    ANALYTICS(
        requiredScopes = setOf("w_member_social"),
        resourceKind = SocialAccountKind.PERSONAL_PROFILE,
        mvpStatus = CapabilityMvpStatus.UNSUPPORTED,
    ),
}

@ValueObject
enum class CapabilityMvpStatus {
    SUPPORTED,
    GATED,
    UNSUPPORTED,
}

/**
 * Tracks which scopes were actually granted during OAuth for a social connection.
 */
@ValueObject
data class GrantedScopeBundle(val grantedScopes: Set<String>, val capabilityBundles: Set<LinkedinCapabilityBundle>) {
    companion object {
        fun fromGrantedScopes(scopes: Set<String>): GrantedScopeBundle {
            val bundles = LinkedinCapabilityBundle.entries
                .filter { bundle -> bundle.requiredScopes.all { it in scopes } }
                .filter { bundle -> bundle.mvpStatus != CapabilityMvpStatus.UNSUPPORTED }
                .toSet()
            return GrantedScopeBundle(grantedScopes = scopes, capabilityBundles = bundles)
        }
    }
}
