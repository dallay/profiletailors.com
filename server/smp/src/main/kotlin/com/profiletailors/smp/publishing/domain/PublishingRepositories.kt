package com.profiletailors.smp.publishing.domain

import java.time.Duration
import java.time.Instant

interface SocialConnectionRepository {
    suspend fun upsert(connection: SocialConnection): SocialConnection

    suspend fun findByWorkspaceAndId(workspaceId: String, connectionId: String): SocialConnection?
}

interface SocialAccountRepository {
    suspend fun upsert(account: SocialAccount): SocialAccount

    suspend fun findByWorkspaceAndId(workspaceId: String, accountId: String): SocialAccount?

    suspend fun findFirstActiveByWorkspace(workspaceId: String): SocialAccount?
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

    suspend fun claimNextDue(
        now: Instant,
        workerId: String,
        claimLease: Duration = Duration.parse("PT2M"),
    ): PublicationJobClaim?

    suspend fun rescheduleRetry(jobId: String, claimVersion: Long, nextAttemptAt: Instant, attemptNumber: Int): Boolean

    suspend fun complete(jobId: String, claimVersion: Long, completedAt: Instant): Boolean

    suspend fun fail(jobId: String, claimVersion: Long, failedAt: Instant): Boolean

    suspend fun block(jobId: String, claimVersion: Long, blockedAt: Instant): Boolean

    suspend fun cancel(jobId: String, cancelledAt: Instant)

    /**
     * Returns claimed jobs whose lease expired before [now] - [staleGrace].
     * Used by operator diagnostics to surface stale work without exposing provider
     * payloads, tokens, or exception messages.
     */
    suspend fun findStaleClaims(now: Instant, staleGrace: Duration, limit: Int = DEFAULT_STALE_JOB_LIMIT): StaleJobPage

    /**
     * Resets every CLAIMED job whose lease expired before [now] - [staleGrace]
     * back to PENDING and clears the claim columns. Returns the number of rows updated.
     */
    suspend fun releaseExpiredClaims(now: Instant, staleGrace: Duration): Int

    companion object {
        const val DEFAULT_STALE_JOB_LIMIT: Int = 100
    }
}

/**
 * Snapshot of a CLAIMED job whose lease has expired past the configured stale threshold.
 * Exposes only safe, structural fields (no provider payloads, tokens, or PII).
 */
data class StaleJob(
    val jobId: String,
    val publicationId: String,
    val workspaceId: String,
    val claimedByWorker: String,
    val claimedAt: Instant,
    val leaseExpiresAt: Instant,
    val attemptNumber: Int,
)

data class StaleJobPage(val jobs: List<StaleJob>, val total: Int)

interface DeliveryAttemptRepository {
    suspend fun record(attempt: DeliveryAttempt): DeliveryAttempt

    suspend fun findByOperationKey(operationKey: String): DeliveryAttempt?

    suspend fun update(attempt: DeliveryAttempt): Boolean
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

interface BulkImportJobRepository {
    suspend fun findByIdempotencyKey(idempotencyKey: String): BulkImportJob?
    suspend fun findByWorkspaceAndId(workspaceId: String, jobId: String): BulkImportJob?
    suspend fun save(job: BulkImportJob): BulkImportJob
    suspend fun saveRows(rows: List<BulkImportRow>)
    suspend fun findRows(jobId: String): List<BulkImportRow>
}
