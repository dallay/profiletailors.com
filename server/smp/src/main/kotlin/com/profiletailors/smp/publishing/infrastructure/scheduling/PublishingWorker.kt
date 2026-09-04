package com.profiletailors.smp.publishing.infrastructure.scheduling

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.media.application.AssetNotReadyException
import com.profiletailors.smp.media.application.MediaAssetResolver
import com.profiletailors.smp.media.application.MediaServiceUnavailableException
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
import com.profiletailors.smp.publishing.domain.ProviderUploadException
import com.profiletailors.smp.publishing.domain.PublicationJobClaim
import com.profiletailors.smp.publishing.domain.PublicationJobRepository
import com.profiletailors.smp.publishing.domain.PublicationLifecyclePolicy
import com.profiletailors.smp.publishing.domain.PublicationRepository
import com.profiletailors.smp.publishing.domain.PublicationValidationException
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
    private val transactionRunner: AtomicTransactionRunner,
    private val clock: Clock,
    private val lifecycleLogger: PublishingLifecycleLogger = PublishingLifecycleLogger(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Suppress("LongMethod")
    suspend fun executeClaim(claim: PublicationJobClaim) {
        val attemptStartedAt = clock.instant()
        val now = attemptStartedAt
        val publication = publicationRepository.findByWorkspaceAndId(
            claim.workspaceId,
            claim.publicationId,
        ) ?: error("Publication '${claim.publicationId}' not found for worker claim.")

        val socialAccount = socialAccountRepository.findByWorkspaceAndId(
            publication.workspaceId,
            publication.socialAccountId,
        ) ?: error("Social account '${publication.socialAccountId}' not found.")

        // Preflight gate: check account status before calling LinkedIn
        val blocked = preflightCheck(claim, socialAccount, publication, now)
        if (blocked) {
            return // Preflight blocked the publication
        }

        try {
            val assets = resolveAssets(publication)
            validateAndPublish(claim, publication, socialAccount, assets, now)
        } catch (_: ReconnectRequiredException) {
            handleReconnectRequired(claim, publication, socialAccount, now)
        } catch (exception: PublishingFailureException) {
            handlePublishFailure(claim, publication, exception.failure, now)
        } catch (exception: AssetNotReadyException) {
            handlePublishFailure(
                claim,
                publication,
                PublishingFailure.mediaNotFound(exception::class.simpleName),
                now,
            )
        } catch (exception: MediaServiceUnavailableException) {
            handlePublishFailure(
                claim,
                publication,
                PublishingFailure.mediaUnavailable(exception::class.simpleName),
                now,
            )
        } catch (exception: PublicationValidationException) {
            handlePublishFailure(
                claim,
                publication,
                PublishingFailure.validationFailed(exception::class.simpleName),
                now,
            )
        } catch (exception: RetryablePublishingException) {
            handlePublishFailure(
                claim,
                publication,
                PublishingFailure.providerUnavailable(exception::class.simpleName),
                now,
            )
        } catch (exception: ProviderUploadException) {
            handlePublishFailure(
                claim,
                publication,
                PublishingFailure.publishingFailed(exception.message),
                now,
            )
        } catch (@Suppress("TooGenericExceptionCaught") exception: Exception) {
            handlePublishFailure(
                claim,
                publication,
                PublishingFailure.publishingFailed(exception::class.simpleName),
                now,
            )
        }
    }

    private suspend fun resolveAssets(
        publication: com.profiletailors.smp.publishing.domain.PublicationDraft,
    ): List<com.profiletailors.smp.publishing.domain.PublicationAsset> = mediaAssetResolver.resolveReadyAssets(
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
    ): Boolean = when (socialAccount.status) {
        SocialConnectionStatus.DISABLED -> {
            log.info(
                "Preflight blocked publication {} — account {} is DISABLED",
                publication.id,
                socialAccount.id,
            )
            failPublicationTerminal(
                claim,
                publication,
                socialAccount.workspaceId,
                now,
                PublishingFailureCategory.ACCOUNT_UNAVAILABLE.code,
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
                PublishingFailureCategory.ACCOUNT_RECONNECT_REQUIRED.code,
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
                PublishingFailureCategory.ACCOUNT_UNAVAILABLE.code,
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
            failPublicationTerminal(
                claim,
                publication,
                socialAccount.workspaceId,
                now,
                PublishingFailureCategory.ACCOUNT_UNAVAILABLE.code,
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
                PublishingFailureCategory.ACCOUNT_RECONNECT_REQUIRED.code,
                socialAccount.provider,
            )
            true
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
        transactionRunner.runAtomically {
            publicationRepository.markBlocked(publication.id, now, reason)
            publicationJobRepository.complete(claim.jobId, claim.claimVersion ?: 0L, now)

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
        lifecycleLogger.blocked(
            publicationId = publication.id,
            jobId = claim.jobId,
            workspaceId = workspaceId,
            attemptNumber = claim.attemptNumber,
            provider = provider ?: publication.provider,
            failureCategory = PublishingFailureCategory.ACCOUNT_RECONNECT_REQUIRED,
            durationMs = attemptDurationMs(now),
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
        transactionRunner.runAtomically {
            publicationRepository.markFailed(publication.id, now, reason, null)
            publicationJobRepository.fail(claim.jobId, claim.claimVersion ?: 0L, now)

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
        lifecycleLogger.terminalFailure(
            publicationId = publication.id,
            jobId = claim.jobId,
            workspaceId = workspaceId,
            attemptNumber = claim.attemptNumber,
            provider = provider ?: publication.provider,
            failureCategory = PublishingFailureCategory.entries.first { it.code == reason },
            durationMs = attemptDurationMs(now),
        )
    }

    private suspend fun handleReconnectRequired(
        claim: PublicationJobClaim,
        publication: com.profiletailors.smp.publishing.domain.PublicationDraft,
        socialAccount: com.profiletailors.smp.publishing.domain.SocialAccount,
        now: Instant,
    ) {
        log.warn(
            "Reconnect required for publication {} on account {}: {}",
            publication.id,
            socialAccount.id,
            PublishingFailureCategory.ACCOUNT_RECONNECT_REQUIRED.code,
        )
        transactionRunner.runAtomically {
            publicationRepository.markBlocked(
                publication.id,
                now,
                PublishingFailureCategory.ACCOUNT_RECONNECT_REQUIRED.code,
            )
            publicationJobRepository.complete(claim.jobId, claim.claimVersion ?: 0L, now)

            recordNotificationEvent(
                NotificationEventPayload(
                    workspaceId = publication.workspaceId,
                    socialAccountId = socialAccount.id,
                    publicationId = publication.id,
                    category = NotificationCategory.RECONNECT_REQUIRED,
                    message = PublishingFailureCategory.ACCOUNT_RECONNECT_REQUIRED.code,
                    suggestedAction = "Re-authenticate the LinkedIn account.",
                    occurredAt = now,
                    provider = socialAccount.provider,
                ),
            )
        }
        lifecycleLogger.blocked(
            publicationId = publication.id,
            jobId = claim.jobId,
            workspaceId = publication.workspaceId,
            attemptNumber = claim.attemptNumber,
            provider = socialAccount.provider,
            failureCategory = PublishingFailureCategory.ACCOUNT_RECONNECT_REQUIRED,
            durationMs = attemptDurationMs(now),
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
        now: Instant,
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
        transactionRunner.runAtomically {
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
            publicationJobRepository.complete(claim.jobId, claim.claimVersion ?: 0L, now)
        }
        lifecycleLogger.succeeded(
            publicationId = publication.id,
            jobId = claim.jobId,
            workspaceId = publication.workspaceId,
            attemptNumber = claim.attemptNumber,
            provider = publication.provider,
            durationMs = attemptDurationMs(now),
        )
    }

    private fun attemptDurationMs(attemptStartedAt: Instant): Long =
        Duration.between(attemptStartedAt, clock.instant()).toMillis().coerceAtLeast(0)

    private fun sanitizeDiagnostic(diagnostic: String?): String? {
        if (diagnostic.isNullOrBlank()) return null
        val trimmed = diagnostic.trim()
        return when {
            Regex("""^(status=\d{3}|[A-Za-z][A-Za-z0-9_.]*(Exception|Error))$""").matches(trimmed) -> trimmed
            Regex(
                """(?:access_token|authorization|bearer\s+[A-Za-z0-9._\-]+|secret[-_]?token|client_secret)|""" +
                    """https?://\S+|""" +
                    """\bat [A-Za-z_][\w$.]*\([^)]*\)\b|""" +
                    """\bworkspace-[0-9a-f-]+\b|""" +
                    """\bbucket/[A-Za-z0-9._/\-]+""",
                RegexOption.IGNORE_CASE,
            ).containsMatchIn(trimmed) -> null
            Regex(
                """^(?:LinkedIn\s+[A-Za-z][\w\s]*):\s*\d{3}\b""",
            ).containsMatchIn(trimmed) -> trimmed.take(MAX_DIAGNOSTIC_LENGTH)
            else -> null
        }
    }

    @Suppress("LongMethod")
    private suspend fun handlePublishFailure(
        claim: PublicationJobClaim,
        publication: com.profiletailors.smp.publishing.domain.PublicationDraft,
        failure: PublishingFailure,
        now: Instant,
    ) {
        val categoryCode = failure.category.code
        val shouldRetry = retryPolicy.shouldRetry(claim.attemptNumber, failure.retryable)
        transactionRunner.runAtomically {
            deliveryAttemptRepository.record(
                DeliveryAttempt(
                    id = "attempt-${UUID.randomUUID()}",
                    publicationId = publication.id,
                    publicationJobId = claim.jobId,
                    attemptNumber = claim.attemptNumber,
                    outcome = DeliveryAttemptOutcome.FAILED,
                    retryable = failure.retryable,
                    providerMessage = sanitizeDiagnostic(failure.diagnostic),
                    providerErrorCode = categoryCode,
                    attemptedAt = now,
                ),
            )
            if (shouldRetry) {
                publicationJobRepository.rescheduleRetry(
                    claim.jobId,
                    claim.claimVersion ?: 0L,
                    retryPolicy.nextRetryAt(now),
                    claim.attemptNumber,
                )
            } else {
                publicationRepository.markFailed(
                    publication.id,
                    now,
                    categoryCode,
                    null,
                )
                publicationJobRepository.fail(claim.jobId, claim.claimVersion ?: 0L, now)
                recordNotificationEvent(
                    NotificationEventPayload(
                        workspaceId = publication.workspaceId,
                        socialAccountId = publication.socialAccountId,
                        publicationId = publication.id,
                        category = NotificationCategory.PUBLICATION_FAILED,
                        message = categoryCode,
                        occurredAt = now,
                        provider = publication.provider,
                    ),
                )
            }
        }
        if (shouldRetry) {
            lifecycleLogger.retryScheduled(
                publicationId = publication.id,
                jobId = claim.jobId,
                workspaceId = publication.workspaceId,
                attemptNumber = claim.attemptNumber,
                provider = publication.provider,
                failureCategory = failure.category,
                durationMs = attemptDurationMs(now),
            )
        } else {
            lifecycleLogger.terminalFailure(
                publicationId = publication.id,
                jobId = claim.jobId,
                workspaceId = publication.workspaceId,
                attemptNumber = claim.attemptNumber,
                provider = publication.provider,
                failureCategory = failure.category,
                durationMs = attemptDurationMs(now),
            )
        }
    }

    private companion object {
        private const val MAX_DIAGNOSTIC_LENGTH = 512
    }
}

enum class PublishingFailureCategory(val code: String, val retryable: Boolean, val blocked: Boolean = false) {
    MEDIA_NOT_FOUND("MEDIA_NOT_FOUND", false),
    MEDIA_UNAVAILABLE("MEDIA_UNAVAILABLE", true),
    PROVIDER_VALIDATION_FAILED("PROVIDER_VALIDATION_FAILED", false),
    PROVIDER_RATE_LIMITED("PROVIDER_RATE_LIMITED", true),
    PROVIDER_UNAVAILABLE("PROVIDER_UNAVAILABLE", true),
    ACCOUNT_RECONNECT_REQUIRED("ACCOUNT_RECONNECT_REQUIRED", false, blocked = true),
    ACCOUNT_UNAVAILABLE("ACCOUNT_UNAVAILABLE", false),
    PUBLISHING_FAILED("PUBLISHING_FAILED", false),
}

data class PublishingFailure(val category: PublishingFailureCategory, val diagnostic: String? = null) {
    val retryable: Boolean = category.retryable

    companion object {
        fun mediaNotFound(diagnostic: String? = null) = PublishingFailure(
            PublishingFailureCategory.MEDIA_NOT_FOUND,
            diagnostic,
        )

        fun mediaUnavailable(diagnostic: String? = null) = PublishingFailure(
            PublishingFailureCategory.MEDIA_UNAVAILABLE,
            diagnostic,
        )

        fun validationFailed(diagnostic: String? = null) = PublishingFailure(
            PublishingFailureCategory.PROVIDER_VALIDATION_FAILED,
            diagnostic,
        )

        fun providerRateLimited(diagnostic: String? = null) = PublishingFailure(
            PublishingFailureCategory.PROVIDER_RATE_LIMITED,
            diagnostic,
        )

        fun providerUnavailable(diagnostic: String? = null) = PublishingFailure(
            PublishingFailureCategory.PROVIDER_UNAVAILABLE,
            diagnostic,
        )

        fun accountReconnectRequired(diagnostic: String? = null) = PublishingFailure(
            PublishingFailureCategory.ACCOUNT_RECONNECT_REQUIRED,
            diagnostic,
        )

        fun accountUnavailable(diagnostic: String? = null) = PublishingFailure(
            PublishingFailureCategory.ACCOUNT_UNAVAILABLE,
            diagnostic,
        )

        fun publishingFailed(diagnostic: String? = null) = PublishingFailure(
            PublishingFailureCategory.PUBLISHING_FAILED,
            diagnostic,
        )
    }
}

class PublishingFailureException(val failure: PublishingFailure) : RuntimeException(failure.category.code)

class RetryablePublishingException(message: String) : RuntimeException(message)

class PublishingWorker(
    private val publicationJobRepository: PublicationJobRepository,
    private val publicationRepository: PublicationRepository,
    private val executor: PublishingJobExecutor,
    private val transactionRunner: AtomicTransactionRunner,
    private val clock: Clock,
    private val workerId: String,
    private val claimLease: Duration = Duration.parse("PT2M"),
    private val lifecycleLogger: PublishingLifecycleLogger = PublishingLifecycleLogger(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun pollOnce(): PublicationJobClaim? {
        val now = clock.instant()
        val releasedCount = publicationJobRepository.releaseExpiredClaims(now, claimLease)
        if (releasedCount > 0) {
            log.info(
                "Released expired publication-job claims released={} leaseThresholdSeconds={}",
                releasedCount,
                claimLease.seconds,
            )
        }
        log.debug("Polling for next due publication job")
        val claim = publicationJobRepository.claimNextDue(now, workerId, claimLease) ?: return null
        val publication = publicationRepository.findByWorkspaceAndId(claim.workspaceId, claim.publicationId)
            ?: error("Publication '${claim.publicationId}' not found for worker claim.")
        lifecycleLogger.claimed(
            publicationId = claim.publicationId,
            jobId = claim.jobId,
            workspaceId = claim.workspaceId,
            attemptNumber = claim.attemptNumber,
            provider = publication.provider,
        )
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
        transactionRunner.runAtomically {
            publicationRepository.updateEditableDraft(prepared)
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
