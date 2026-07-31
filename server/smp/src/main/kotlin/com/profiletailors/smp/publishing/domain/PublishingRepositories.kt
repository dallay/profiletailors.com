package com.profiletailors.smp.publishing.domain

import java.time.Instant

interface SocialConnectionRepository {
    suspend fun upsert(connection: SocialConnection): SocialConnection

    suspend fun findByWorkspaceAndId(workspaceId: String, connectionId: String): SocialConnection?
}

interface SocialAccountRepository {
    suspend fun upsert(account: SocialAccount): SocialAccount

    suspend fun findByWorkspaceAndId(workspaceId: String, accountId: String): SocialAccount?
}

interface PublicationRepository {
    suspend fun createDraft(draft: PublicationDraft): PublicationDraft

    suspend fun updateEditableDraft(draft: PublicationDraft): PublicationDraft

    suspend fun findByWorkspaceAndId(workspaceId: String, publicationId: String): PublicationDraft?

    suspend fun findInDateRange(
        workspaceId: String,
        from: Instant,
        to: Instant,
        statuses: Set<PublicationStatus>? = null,
        socialAccountIds: Set<String>? = null,
        hydrateAssets: Boolean = true,
    ): List<PublicationDraft>

    suspend fun countByDate(
        workspaceId: String,
        from: Instant,
        to: Instant,
        statuses: Set<PublicationStatus>? = null,
        timezone: String = "UTC",
    ): List<DateCount>

    suspend fun markPublished(publicationId: String, externalPublicationId: String, publishedAt: Instant)

    suspend fun markFailed(publicationId: String, failedAt: Instant, reasonCode: String?, reasonMessage: String?)

    suspend fun markCancelled(publicationId: String, cancelledAt: Instant)

    suspend fun markBlocked(publicationId: String, blockedAt: Instant, reason: String?)

    suspend fun deleteUnpublished(workspaceId: String, publicationId: String): Boolean

    /**
     * Find publications that are BLOCKED and may be eligible for retry.
     * Used by the BLOCKED-recovery scan when account status restores to ACTIVE.
     */
    suspend fun findBlockedForRecovery(maxRetries: Int): List<PublicationDraft>
}

interface PublicationAssetRepository {
    suspend fun findByWorkspaceAndIds(workspaceId: String, assetIds: Collection<String>): List<PublicationAsset>

    suspend fun create(asset: PublicationAsset): PublicationAsset

    suspend fun updateStatus(assetId: String, status: PublicationAssetStatus)

    suspend fun updateProviderAssetRef(assetId: String, providerAssetRef: ProviderAssetRef)
}

interface PublicationJobRepository {
    suspend fun enqueue(job: PublicationJob)

    suspend fun replaceForPublication(job: PublicationJob)

    suspend fun claimNextDue(now: Instant, workerId: String): PublicationJobClaim?

    suspend fun rescheduleRetry(jobId: String, nextAttemptAt: Instant, attemptNumber: Int)

    suspend fun complete(jobId: String, completedAt: Instant)

    suspend fun fail(jobId: String, failedAt: Instant)

    suspend fun cancel(jobId: String, cancelledAt: Instant)
}

fun interface DeliveryAttemptRepository {
    suspend fun record(attempt: DeliveryAttempt): DeliveryAttempt
}

interface NotificationEventRepository {
    suspend fun record(event: NotificationEvent): NotificationEvent

    suspend fun findByWorkspace(
        workspaceId: String,
        socialAccountId: String? = null,
        publicationId: String? = null,
        categories: Set<NotificationCategory>? = null,
        limit: Int = 50,
    ): List<NotificationEvent>
}


object NoOpNotificationEventRepository : NotificationEventRepository {
    override suspend fun record(event: NotificationEvent): NotificationEvent = event
    override suspend fun findByWorkspace(
        workspaceId: String,
        socialAccountId: String?,
        publicationId: String?,
        categories: Set<NotificationCategory>?,
        limit: Int,
    ): List<NotificationEvent> = emptyList()
}
