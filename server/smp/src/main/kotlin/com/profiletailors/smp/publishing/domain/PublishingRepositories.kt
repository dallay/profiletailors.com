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

    suspend fun markPublished(publicationId: String, externalPublicationId: String, publishedAt: Instant)

    suspend fun markFailed(publicationId: String, failedAt: Instant, reasonCode: String?, reasonMessage: String?)

    suspend fun markCancelled(publicationId: String, cancelledAt: Instant)
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

interface DeliveryAttemptRepository {
    suspend fun record(attempt: DeliveryAttempt): DeliveryAttempt
}
