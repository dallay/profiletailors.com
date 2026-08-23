package com.profiletailors.smp.publishing.infrastructure.scheduling

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.media.application.AssetNotReadyException
import com.profiletailors.smp.media.application.MediaAssetResolver
import com.profiletailors.smp.media.application.MediaServiceUnavailableException
import com.profiletailors.smp.publishing.domain.DeliveryAttempt
import com.profiletailors.smp.publishing.domain.DeliveryAttemptOutcome
import com.profiletailors.smp.publishing.domain.DeliveryAttemptPhase
import com.profiletailors.smp.publishing.domain.DeliveryAttemptRepository
import com.profiletailors.smp.publishing.domain.DeliveryRetryPolicy
import com.profiletailors.smp.publishing.domain.JobStatus
import com.profiletailors.smp.publishing.domain.NotificationCategory
import com.profiletailors.smp.publishing.domain.NotificationEvent
import com.profiletailors.smp.publishing.domain.NotificationEventRepository
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidationInput
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.ProviderPublishCommand
import com.profiletailors.smp.publishing.domain.ProviderPublishResult
import com.profiletailors.smp.publishing.domain.ProviderTransportUncertaintyException
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

@Suppress("LargeClass")
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
                PublishingFailure.publishingFailed(exception::class.simpleName),
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
        val applied = transactionRunner.runAtomically {
            if (!publicationJobRepository.block(claim.jobId, claim.claimVersion, now)) {
                false
            } else {
                publicationRepository.markBlocked(publication.id, now, reason)
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
                true
            }
        }
        if (!applied) return
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
        val applied = transactionRunner.runAtomically {
            if (!publicationJobRepository.fail(claim.jobId, claim.claimVersion, now)) {
                false
            } else {
                publicationRepository.markFailed(publication.id, now, reason, null)
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
                true
            }
        }
        if (!applied) return
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
        val applied = transactionRunner.runAtomically {
            if (!publicationJobRepository.block(claim.jobId, claim.claimVersion, now)) {
                false
            } else {
                check(
                    persistAttemptOutcome(
                        claim = claim,
                        publication = publication,
                        outcome = DeliveryAttemptOutcome.FAILED,
                        retryable = false,
                        providerMessage = null,
                        providerErrorCode = PublishingFailureCategory.ACCOUNT_RECONNECT_REQUIRED.code,
                        attemptedAt = now,
                    ),
                ) { "Delivery attempt outcome could not be fenced for ${claim.jobId}." }
                publicationRepository.markBlocked(
                    publication.id,
                    now,
                    PublishingFailureCategory.ACCOUNT_RECONNECT_REQUIRED.code,
                )
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
                true
            }
        }
        if (!applied) return
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
        val operationKey = claim.operationKey
        val existingAttempt = deliveryAttemptRepository.findByOperationKey(operationKey)
        if (existingAttempt != null) {
            if (existingAttempt.outcome == DeliveryAttemptOutcome.SUCCEEDED &&
                existingAttempt.externalPublicationId != null
            ) {
                finalizeRecoveredSuccess(claim, publication, existingAttempt, now)
            } else {
                handleAmbiguousOutcome(claim, publication, existingAttempt, now)
            }
            return
        }
        val startedAttempt = startDeliveryAttempt(claim, publication, operationKey, now)
        val providerOutcome = invokeProvider(
            ProviderPublishCommand(
                publicationId = publication.id,
                workspaceId = publication.workspaceId,
                socialAccount = socialAccount,
                publication = publication,
                assets = assets,
                operationKey = operationKey,
            ),
        )
        val providerException = providerOutcome.exceptionOrNull()
        if (providerException != null) {
            if (providerException is ReconnectRequiredException ||
                providerException is PublishingFailureException ||
                providerException is RetryablePublishingException ||
                providerException is ProviderUploadException
            ) {
                throw providerException
            }
            if (providerException is ProviderTransportUncertaintyException) {
                handleAmbiguousOutcome(claim, publication, startedAttempt, now)
                return
            }
            throw providerException
        }
        val result = requireNotNull(providerOutcome.getOrNull())
        finalizeSuccessfulPublication(claim, publication, startedAttempt, result, now)
    }

    private suspend fun startDeliveryAttempt(
        claim: PublicationJobClaim,
        publication: com.profiletailors.smp.publishing.domain.PublicationDraft,
        operationKey: String,
        now: Instant,
    ): DeliveryAttempt = DeliveryAttempt(
        id = "attempt-${UUID.randomUUID()}",
        publicationId = publication.id,
        publicationJobId = claim.jobId,
        attemptNumber = claim.attemptNumber,
        outcome = DeliveryAttemptOutcome.IN_PROGRESS,
        retryable = false,
        attemptedAt = now,
        operationKey = operationKey,
        claimVersion = claim.claimVersion,
        phase = DeliveryAttemptPhase.PROVIDER_CREATE,
    ).also { deliveryAttemptRepository.record(it) }

    private suspend fun invokeProvider(command: ProviderPublishCommand): Result<ProviderPublishResult> = try {
        Result.success(socialPublisher.publish(command))
    } catch (@Suppress("TooGenericExceptionCaught") exception: Exception) {
        Result.failure(exception)
    }

    private suspend fun finalizeSuccessfulPublication(
        claim: PublicationJobClaim,
        publication: com.profiletailors.smp.publishing.domain.PublicationDraft,
        startedAttempt: DeliveryAttempt,
        result: ProviderPublishResult,
        now: Instant,
    ) {
        val applied = transactionRunner.runAtomically {
            if (!publicationJobRepository.complete(claim.jobId, claim.claimVersion, now)) {
                false
            } else {
                check(
                    deliveryAttemptRepository.update(
                        startedAttempt.copy(
                            outcome = DeliveryAttemptOutcome.SUCCEEDED,
                            providerMessage = sanitizeDiagnostic(result.providerMessage),
                            externalPublicationId = result.externalPublicationId,
                            phase = DeliveryAttemptPhase.FINALIZATION,
                        ),
                    ),
                ) { "Delivery attempt outcome could not be fenced for ${claim.jobId}." }
                publicationRepository.markPublished(publication.id, result.externalPublicationId, now)
                true
            }
        }
        if (!applied) return
        lifecycleLogger.succeeded(
            publicationId = publication.id,
            jobId = claim.jobId,
            workspaceId = publication.workspaceId,
            attemptNumber = claim.attemptNumber,
            provider = publication.provider,
            durationMs = attemptDurationMs(now),
        )
    }

    private suspend fun persistAttemptOutcome(
        claim: PublicationJobClaim,
        publication: com.profiletailors.smp.publishing.domain.PublicationDraft,
        outcome: DeliveryAttemptOutcome,
        retryable: Boolean,
        providerMessage: String?,
        providerErrorCode: String?,
        externalPublicationId: String? = null,
        attemptedAt: Instant,
    ): Boolean {
        val existing = deliveryAttemptRepository.findByOperationKey(claim.operationKey)
        val attempt = DeliveryAttempt(
            id = existing?.id ?: "attempt-${UUID.randomUUID()}",
            publicationId = publication.id,
            publicationJobId = claim.jobId,
            attemptNumber = claim.attemptNumber,
            outcome = outcome,
            retryable = retryable,
            providerMessage = providerMessage,
            providerErrorCode = providerErrorCode,
            externalPublicationId = externalPublicationId,
            attemptedAt = attemptedAt,
            createdAt = existing?.createdAt,
            operationKey = claim.operationKey,
            claimVersion = claim.claimVersion,
            phase = DeliveryAttemptPhase.FINALIZATION,
        )
        return if (existing == null) {
            deliveryAttemptRepository.record(attempt)
            true
        } else {
            deliveryAttemptRepository.update(attempt)
        }
    }

    private suspend fun finalizeRecoveredSuccess(
        claim: PublicationJobClaim,
        publication: com.profiletailors.smp.publishing.domain.PublicationDraft,
        attempt: DeliveryAttempt,
        now: Instant,
    ) {
        val externalPublicationId = requireNotNull(attempt.externalPublicationId)
        val applied = transactionRunner.runAtomically {
            if (!publicationJobRepository.complete(claim.jobId, claim.claimVersion, now)) {
                false
            } else {
                publicationRepository.markPublished(publication.id, externalPublicationId, now)
                true
            }
        }
        if (!applied) return
        lifecycleLogger.succeeded(
            publicationId = publication.id,
            jobId = claim.jobId,
            workspaceId = publication.workspaceId,
            attemptNumber = claim.attemptNumber,
            provider = publication.provider,
            durationMs = attemptDurationMs(now),
        )
    }

    private suspend fun handleAmbiguousOutcome(
        claim: PublicationJobClaim,
        publication: com.profiletailors.smp.publishing.domain.PublicationDraft,
        existingAttempt: DeliveryAttempt,
        now: Instant,
    ) {
        val ambiguousAttempt = existingAttempt.copy(
            outcome = DeliveryAttemptOutcome.AMBIGUOUS,
            retryable = false,
            providerMessage = null,
            providerErrorCode = PublishingFailureCategory.AMBIGUOUS_OUTCOME.code,
            attemptedAt = now,
            claimVersion = claim.claimVersion,
            phase = DeliveryAttemptPhase.AMBIGUOUS,
        )
        val applied = transactionRunner.runAtomically {
            if (!publicationJobRepository.block(claim.jobId, claim.claimVersion, now)) {
                false
            } else {
                check(deliveryAttemptRepository.update(ambiguousAttempt)) {
                    "Delivery attempt ambiguity could not be fenced for ${claim.jobId}."
                }
                publicationRepository.markBlocked(
                    publication.id,
                    now,
                    PublishingFailureCategory.AMBIGUOUS_OUTCOME.code,
                )
                recordNotificationEvent(
                    NotificationEventPayload(
                        workspaceId = publication.workspaceId,
                        socialAccountId = publication.socialAccountId,
                        publicationId = publication.id,
                        category = NotificationCategory.AMBIGUOUS_OUTCOME,
                        message = PublishingFailureCategory.AMBIGUOUS_OUTCOME.code,
                        suggestedAction = "Reconcile the provider outcome before retrying this publication.",
                        occurredAt = now,
                        provider = publication.provider,
                    ),
                )
                true
            }
        }
        if (!applied) return
        lifecycleLogger.blocked(
            publicationId = publication.id,
            jobId = claim.jobId,
            workspaceId = publication.workspaceId,
            attemptNumber = claim.attemptNumber,
            provider = publication.provider,
            failureCategory = PublishingFailureCategory.AMBIGUOUS_OUTCOME,
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
        val applied = transactionRunner.runAtomically {
            val transitioned = if (shouldRetry) {
                publicationJobRepository.rescheduleRetry(
                    claim.jobId,
                    claim.claimVersion,
                    retryPolicy.nextRetryAt(now),
                    claim.attemptNumber,
                )
            } else {
                publicationJobRepository.fail(claim.jobId, claim.claimVersion, now)
            }
            if (!transitioned) {
                false
            } else {
                check(
                    persistAttemptOutcome(
                        claim = claim,
                        publication = publication,
                        outcome = DeliveryAttemptOutcome.FAILED,
                        retryable = failure.retryable,
                        providerMessage = sanitizeDiagnostic(failure.diagnostic),
                        providerErrorCode = categoryCode,
                        attemptedAt = now,
                    ),
                ) { "Delivery attempt outcome could not be fenced for ${claim.jobId}." }
                if (!shouldRetry) {
                    publicationRepository.markFailed(
                        publication.id,
                        now,
                        categoryCode,
                        null,
                    )
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
                true
            }
        }
        if (!applied) return
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
    AMBIGUOUS_OUTCOME("AMBIGUOUS_OUTCOME", false, blocked = true),
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
    private val staleGrace: Duration = Duration.parse("PT5M"),
    private val lifecycleLogger: PublishingLifecycleLogger = PublishingLifecycleLogger(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun pollOnce(): PublicationJobClaim? {
        val now = clock.instant()
        val releasedCount = publicationJobRepository.releaseExpiredClaims(now, staleGrace)
        if (releasedCount > 0) {
            log.info(
                "Released expired publication-job claims released={} staleGraceSeconds={}",
                releasedCount,
                staleGrace.seconds,
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
                log.error(
                    "Failed to requeue BLOCKED publication {}: type={}",
                    publication.id,
                    e::class.simpleName,
                )
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
