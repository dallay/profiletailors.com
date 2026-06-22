package com.profiletailors.smp.publishing.infrastructure.scheduling

import com.profiletailors.smp.media.application.MediaAssetResolver
import com.profiletailors.smp.publishing.domain.DeliveryAttempt
import com.profiletailors.smp.publishing.domain.DeliveryAttemptOutcome
import com.profiletailors.smp.publishing.domain.DeliveryAttemptRepository
import com.profiletailors.smp.publishing.domain.DeliveryRetryPolicy
import com.profiletailors.smp.publishing.domain.JobStatus
import com.profiletailors.smp.publishing.domain.NotificationCategory
import com.profiletailors.smp.publishing.domain.NotificationEvent
import com.profiletailors.smp.publishing.domain.NotificationEventRepository
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidationInput
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.ProviderPublishCommand
import com.profiletailors.smp.publishing.domain.PublicationJobClaim
import com.profiletailors.smp.publishing.domain.PublicationJobRepository
import com.profiletailors.smp.publishing.domain.PublicationLifecyclePolicy
import com.profiletailors.smp.publishing.domain.PublicationRepository
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ReconnectRequiredException
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialPublisher
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.TaskScheduler
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

class PublishingJobExecutor(
    private val publicationJobRepository: PublicationJobRepository,
    private val publicationRepository: PublicationRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val mediaAssetResolver: MediaAssetResolver,
    private val deliveryAttemptRepository: DeliveryAttemptRepository,
    private val notificationEventRepository: NotificationEventRepository?,
    private val providerCapabilityValidator: ProviderCapabilityValidator,
    private val socialPublisher: SocialPublisher,
    private val retryPolicy: DeliveryRetryPolicy,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun executeClaim(claim: PublicationJobClaim) {
        val now = clock.instant()
        val publication = publicationRepository.findByWorkspaceAndId(
            claim.workspaceId,
            claim.publicationId
        ) ?: error("Publication '${claim.publicationId}' not found for worker claim.")
        
        val socialAccount = socialAccountRepository.findByWorkspaceAndId(
            publication.workspaceId,
            publication.socialAccountId
        ) ?: error("Social account '${publication.socialAccountId}' not found.")
        
        val assets = mediaAssetResolver.resolveReadyAssets(
            publication.workspaceId,
            publication.assetIds,
        ).map { resolvedAsset ->
            com.profiletailors.smp.publishing.domain.PublicationAsset(
                id = resolvedAsset.assetId,
                workspaceId = resolvedAsset.workspaceId,
                sourceType = com.profiletailors.smp.publishing.domain.AssetSourceType.UPLOADED,
                mediaType = resolvedAsset.mediaType,
                storageKey = resolvedAsset.storageKey,
                status = com.profiletailors.smp.publishing.domain.PublicationAssetStatus.READY,
                createdByPrincipalId = "media-context",
            )
        }

        // Preflight gate: check account status before calling LinkedIn
        val blocked = preflightCheck(claim, socialAccount, publication, now)
        if (blocked) {
            return // Preflight blocked the publication
        }

        try {
            validateAndPublish(claim, publication, socialAccount, assets, now)
        } catch (exception: ReconnectRequiredException) {
            handleReconnectRequired(claim, publication, socialAccount, exception, now)
        } catch (exception: RetryablePublishingException) {
            handlePublishFailure(claim, publication, exception, now)
        } catch (@Suppress("TooGenericExceptionCaught") exception: Exception) {
            handlePublishFailure(claim, publication, exception, now)
        }
    }

    /**
     * Preflight gate: checks social account status before dispatching to provider.
     * Blocks publications for DISABLED, REQUIRES_RECONNECT, or DELETED accounts.
     */
    @Suppress("LongMethod")
    private suspend fun preflightCheck(
        claim: PublicationJobClaim,
        socialAccount: com.profiletailors.smp.publishing.domain.SocialAccount,
        publication: com.profiletailors.smp.publishing.domain.PublicationDraft,
        now: Instant,
    ): Boolean {
        return when (socialAccount.status) {
            SocialConnectionStatus.DISABLED -> {
                log.info(
                    "Preflight blocked publication {} — account {} is DISABLED",
                    publication.id,
                    socialAccount.id,
                )
                blockPublication(
                    claim,
                    publication,
                    socialAccount.workspaceId,
                    now,
                    "Account is DISABLED",
                    socialAccount.provider,
                )
                true
            }
            SocialConnectionStatus.REQUIRES_RECONNECT -> {
                log.info(
                    "Preflight blocked publication {} — account {} requires reconnect",
                    publication.id,
                    socialAccount.id,
                )
                blockPublication(
                    claim,
                    publication,
                    socialAccount.workspaceId,
                    now,
                    "Account requires reconnect",
                    socialAccount.provider,
                )
                true
            }
            SocialConnectionStatus.DELETED -> {
                log.info(
                    "Preflight failed publication {} — account {} is DELETED",
                    publication.id,
                    socialAccount.id,
                )
                failPublicationTerminal(
                    claim,
                    publication,
                    socialAccount.workspaceId,
                    now,
                    "Account is DELETED",
                    socialAccount.provider,
                )
                true
            }
            SocialConnectionStatus.PENDING -> {
                log.info(
                    "Preflight blocked publication {} — account {} is PENDING (not yet active)",
                    publication.id,
                    socialAccount.id,
                )
                blockPublication(
                    claim,
                    publication,
                    socialAccount.workspaceId,
                    now,
                    "Account is PENDING activation",
                    socialAccount.provider,
                )
                true
            }
            SocialConnectionStatus.ACTIVE -> {
                false // Proceed with publishing
            }
            SocialConnectionStatus.ERROR -> {
                false // Proceed — error status is non-publishable but may be recoverable
            }
            SocialConnectionStatus.REVOKED, SocialConnectionStatus.EXPIRED -> {
                log.info(
                    "Preflight blocked publication {} — account {} has legacy status {}",
                    publication.id,
                    socialAccount.id,
                    socialAccount.status,
                )
                blockPublication(
                    claim,
                    publication,
                    socialAccount.workspaceId,
                    now,
                    "Account status: ${socialAccount.status}",
                    socialAccount.provider,
                )
                true
            }
        }
    }

    private suspend fun blockPublication(
        claim: PublicationJobClaim,
        publication: com.profiletailors.smp.publishing.domain.PublicationDraft,
        workspaceId: String,
        now: Instant,
        reason: String,
        provider: com.profiletailors.smp.publishing.domain.SocialProvider? = null,
    ) {
        PublicationLifecyclePolicy.markBlocked(publication, now, reason)
        publicationRepository.markBlocked(publication.id, now, reason)
        publicationJobRepository.complete(claim.jobId, now)

        recordNotificationEvent(
            NotificationEventPayload(
                workspaceId = workspaceId,
                socialAccountId = publication.socialAccountId,
                publicationId = publication.id,
                category = NotificationCategory.PUBLICATION_BLOCKED,
                message = "Publication blocked: $reason",
                suggestedAction = "Reconnect the LinkedIn account to retry blocked publications.",
                occurredAt = now,
                provider = provider,
            ),
        )
    }

    private suspend fun failPublicationTerminal(
        claim: PublicationJobClaim,
        publication: com.profiletailors.smp.publishing.domain.PublicationDraft,
        workspaceId: String,
        now: Instant,
        reason: String,
        provider: com.profiletailors.smp.publishing.domain.SocialProvider? = null,
    ) {
        publicationRepository.markFailed(publication.id, now, "TERMINAL_ACCOUNT_STATUS", reason)
        publicationJobRepository.fail(claim.jobId, now)

        recordNotificationEvent(
            NotificationEventPayload(
                workspaceId = workspaceId,
                socialAccountId = publication.socialAccountId,
                publicationId = publication.id,
                category = NotificationCategory.PUBLICATION_FAILED,
                message = "Publication failed terminally: $reason",
                occurredAt = now,
                provider = provider,
            ),
        )
    }

    private suspend fun handleReconnectRequired(
        claim: PublicationJobClaim,
        publication: com.profiletailors.smp.publishing.domain.PublicationDraft,
        socialAccount: com.profiletailors.smp.publishing.domain.SocialAccount,
        exception: ReconnectRequiredException,
        now: Instant,
    ) {
        log.warn(
            "Reconnect required for publication {} on account {}: {}",
            publication.id,
            socialAccount.id,
            exception.message,
        )
        publicationRepository.markBlocked(publication.id, now, exception.message)
        publicationJobRepository.complete(claim.jobId, now)

        recordNotificationEvent(
            NotificationEventPayload(
                workspaceId = publication.workspaceId,
                socialAccountId = socialAccount.id,
                publicationId = publication.id,
                category = NotificationCategory.RECONNECT_REQUIRED,
                message = exception.message ?: "Reconnect required",
                suggestedAction = "Re-authenticate the LinkedIn account.",
                occurredAt = now,
                provider = socialAccount.provider,
            ),
        )
    }

    private data class NotificationEventPayload(
        val workspaceId: String,
        val socialAccountId: String,
        val publicationId: String?,
        val category: NotificationCategory,
        val message: String,
        val suggestedAction: String? = null,
        val occurredAt: Instant,
        val provider: com.profiletailors.smp.publishing.domain.SocialProvider? = null,
    )

    private suspend fun recordNotificationEvent(payload: NotificationEventPayload) {
        notificationEventRepository?.record(
            NotificationEvent(
                id = "",
                workspaceId = payload.workspaceId,
                provider = payload.provider ?: com.profiletailors.smp.publishing.domain.SocialProvider.LINKEDIN,
                socialAccountId = payload.socialAccountId,
                publicationId = payload.publicationId,
                category = payload.category,
                message = payload.message,
                suggestedAction = payload.suggestedAction,
                occurredAt = payload.occurredAt,
            ),
        )
    }

    private suspend fun validateAndPublish(
        claim: PublicationJobClaim,
        publication: com.profiletailors.smp.publishing.domain.PublicationDraft,
        socialAccount: com.profiletailors.smp.publishing.domain.SocialAccount,
        assets: List<com.profiletailors.smp.publishing.domain.PublicationAsset>,
        now: Instant
    ) {
        providerCapabilityValidator.validate(
            ProviderCapabilityValidationInput(
                provider = socialAccount.provider,
                socialAccount = socialAccount,
                publication = publication,
                assets = assets,
            ),
        )
        val result = socialPublisher.publish(
            ProviderPublishCommand(
                publicationId = publication.id,
                workspaceId = publication.workspaceId,
                socialAccount = socialAccount,
                publication = publication,
                assets = assets,
            ),
        )
        deliveryAttemptRepository.record(
            DeliveryAttempt(
                id = "attempt-${UUID.randomUUID()}",
                publicationId = publication.id,
                publicationJobId = claim.jobId,
                attemptNumber = claim.attemptNumber,
                outcome = DeliveryAttemptOutcome.SUCCEEDED,
                retryable = false,
                providerMessage = result.providerMessage,
                externalPublicationId = result.externalPublicationId,
                attemptedAt = now,
            ),
        )
        publicationRepository.markPublished(publication.id, result.externalPublicationId, now)
        publicationJobRepository.complete(claim.jobId, now)
    }

    private suspend fun handlePublishFailure(
        claim: PublicationJobClaim,
        publication: com.profiletailors.smp.publishing.domain.PublicationDraft,
        exception: Exception,
        now: Instant
    ) {
        val retryable = exception is RetryablePublishingException
        deliveryAttemptRepository.record(
            DeliveryAttempt(
                id = "attempt-${UUID.randomUUID()}",
                publicationId = publication.id,
                publicationJobId = claim.jobId,
                attemptNumber = claim.attemptNumber,
                outcome = DeliveryAttemptOutcome.FAILED,
                retryable = retryable,
                providerMessage = exception.message,
                providerErrorCode = exception::class.simpleName,
                attemptedAt = now,
            ),
        )
        if (retryPolicy.shouldRetry(claim.attemptNumber, retryable)) {
            publicationJobRepository.rescheduleRetry(
                claim.jobId,
                retryPolicy.nextRetryAt(now),
                claim.attemptNumber
            )
        } else {
            publicationRepository.markFailed(
                publication.id,
                now,
                exception::class.simpleName,
                exception.message
            )
            publicationJobRepository.fail(claim.jobId, now)
            recordNotificationEvent(
                NotificationEventPayload(
                    workspaceId = publication.workspaceId,
                    socialAccountId = publication.socialAccountId,
                    publicationId = publication.id,
                    category = NotificationCategory.PUBLICATION_FAILED,
                    message = "Publication failed: ${exception.message}",
                    occurredAt = now,
                    provider = publication.provider,
                ),
            )
        }
    }
}

class RetryablePublishingException(
    message: String,
) : RuntimeException(message)

class PublishingWorker(
    private val publicationJobRepository: PublicationJobRepository,
    private val publicationRepository: PublicationRepository,
    private val executor: PublishingJobExecutor,
    private val clock: Clock,
    private val workerId: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun pollOnce(): PublicationJobClaim? {
        val claim = publicationJobRepository.claimNextDue(clock.instant(), workerId) ?: return null
        executor.executeClaim(claim)
        return claim
    }

    /**
     * BLOCKED-recovery scan: periodically checks for accounts that transitioned
     * from non-publishable to ACTIVE, and requeues BLOCKED publications targeting
     * those accounts with exponential backoff.
     */
    suspend fun scanBlockedForRecovery() {
        log.debug("Starting BLOCKED-recovery scan")
        val publications = publicationRepository.findBlockedForRecovery(maxRetries = BLOCKED_RECOVERY_MAX_RETRIES)
        publications.forEach { publication ->
            try {
                requeueBlockedPublication(publication)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                log.error("Failed to requeue BLOCKED publication {}: {}", publication.id, e.message, e)
            }
        }
        log.debug("BLOCKED-recovery scan completed; requeued {} publication(s)", publications.size)
    }

    private suspend fun requeueBlockedPublication(
        publication: com.profiletailors.smp.publishing.domain.PublicationDraft,
    ) {
        val now = clock.instant()
        val prepared = PublicationLifecyclePolicy.prepareBlockedRetry(
            publication = publication,
            now = now,
            maxRetries = BLOCKED_RECOVERY_MAX_RETRIES,
        )
        // Update publication first; if job replacement fails, revert to BLOCKED to avoid orphaning
        // a QUEUED publication with no matching job (recovery scans filter by BLOCKED).
        publicationRepository.updateEditableDraft(prepared)
        try {
            publicationJobRepository.replaceForPublication(
                com.profiletailors.smp.publishing.domain.PublicationJob(
                    id = "pjob-${UUID.randomUUID()}",
                    publicationId = prepared.id,
                    workspaceId = prepared.workspaceId,
                    status = com.profiletailors.smp.publishing.domain.JobStatus.PENDING,
                    dueAt = prepared.scheduledFor ?: now,
                    priorityRank = 0,
                    attemptCount = 0,
                    maxAttempts = 1,
                ),
            )
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Revert publication to BLOCKED so next recovery scan can retry
            log.warn("Failed to replace job for blocked publication ${publication.id}, reverting to BLOCKED", e)
            publicationRepository.markBlocked(publication.id, now, "Retry failed: ${e.message}")
            return
        }
        log.info(
            "Requeued BLOCKED publication {} for retry (attempt {})",
            publication.id,
            prepared.retryCount,
        )
    }

    private companion object {
        const val BLOCKED_RECOVERY_MAX_RETRIES = 5
    }
}

class PublishingWorkerLifecycle(
    private val enabled: Boolean,
    private val pollInterval: Duration,
    private val blockedRecoveryInterval: Duration,
    private val taskScheduler: TaskScheduler,
    private val worker: PublishingWorker,
) {
    fun start() {
        if (!enabled) return
        // Use initialDelay = pollInterval to give Liquibase migrations time
        // to complete before the first poll attempt (avoids race condition
        // where worker queries tables that don't exist yet).
        taskScheduler.scheduleAtFixedRate(
            { runBlocking { worker.pollOnce() } },
            java.time.Instant.now().plus(pollInterval),
            pollInterval,
        )
        taskScheduler.scheduleAtFixedRate(
            { runBlocking { worker.scanBlockedForRecovery() } },
            java.time.Instant.now().plus(blockedRecoveryInterval),
            blockedRecoveryInterval,
        )
    }
}
