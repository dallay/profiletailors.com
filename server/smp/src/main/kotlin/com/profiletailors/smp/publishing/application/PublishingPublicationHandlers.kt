package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.identity.application.AuthFeature
import com.profiletailors.smp.identity.application.EmailVerificationPolicy
import com.profiletailors.smp.identity.application.NoOpPrincipalIdentityLookup
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.application.permissiveEmailVerificationPolicy
import com.profiletailors.smp.identity.application.requireEmailVerification
import com.profiletailors.smp.media.application.MediaAssetResolver
import com.profiletailors.smp.media.application.MediaServiceUnavailableException
import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidationInput
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationAssetRepository
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import com.profiletailors.smp.publishing.domain.PublicationDeletionNotAllowedException
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationJob
import com.profiletailors.smp.publishing.domain.PublicationJobRepository
import com.profiletailors.smp.publishing.domain.PublicationLifecyclePolicy
import com.profiletailors.smp.publishing.domain.PublicationRepository
import com.profiletailors.smp.publishing.domain.PublicationSchedulingPolicy
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.Instant

// --- EditPublicationHandler ---

@Suppress("LongParameterList")
@Service
internal class EditPublicationHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val socialAccountRepository: SocialAccountRepository,
    private val publicationRepository: PublicationRepository,
    private val publicationAssetRepository: PublicationAssetRepository,
    private val publicationJobRepository: PublicationJobRepository,
    private val transactionRunner: AtomicTransactionRunner,
    private val providerCapabilityValidator: ProviderCapabilityValidator,
    private val schedulingPolicy: PublicationSchedulingPolicy,
    private val mediaAssetResolver: MediaAssetResolver,
    private val mediaIntegrationSettings: PublishingMediaIntegrationSettings,
    private val clock: Clock,
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy = permissiveEmailVerificationPolicy,
) : CommandWithResultHandler<EditPublicationCommand, PublicationResult> {
    override suspend fun handle(command: EditPublicationCommand): PublicationResult {
        val principalCtx = principalContextProvider.require()
        requireEmailVerification(
            principalCtx,
            principalIdentityLookup,
            emailVerificationPolicy,
            AuthFeature.PUBLISH_CONTENT,
        )
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val current = publicationRepository.findByWorkspaceAndId(workspaceId, command.publicationId)
            ?: throw PublicationNotFoundException(command.publicationId)
        PublicationLifecyclePolicy.requireEditable(current)
        val account = socialAccountRepository.findByWorkspaceAndId(workspaceId, current.socialAccountId)
            ?: throw SocialAccountNotFoundException(current.socialAccountId)

        val updatedAssetIds = command.assetIds ?: current.assetIds
        val assets = resolveAssets(workspaceId, updatedAssetIds)

        val now = clock.instant()
        val updatedDraft = current.copy(
            title = command.title,
            bodyText = command.bodyText,
            assetIds = updatedAssetIds,
            scheduleMode = command.scheduleMode,
            scheduledFor = command.scheduledFor,
            nextSlotAfter = command.nextSlotAfter,
            priority = command.priority,
        )
        PublicationLifecyclePolicy.validateForCreation(updatedDraft.copy(status = PublicationStatus.DRAFT), now)
        providerCapabilityValidator.validate(
            ProviderCapabilityValidationInput(
                provider = account.provider,
                socialAccount = account,
                publication = updatedDraft,
                assets = assets,
            ),
        )
        val queued = PublicationLifecyclePolicy.queue(
            updatedDraft,
            schedulingPolicy.resolveDueAt(updatedDraft, now),
        )
        val persisted = transactionRunner.runAtomically {
            publicationRepository.updateEditableDraft(queued).also { persisted ->
                publicationJobRepository.replaceForPublication(newJobFor(persisted, now))
            }
        }
        return persisted.toResult()
    }

    private suspend fun resolveAssets(workspaceId: String, assetIds: List<String>): List<PublicationAsset> {
        val shouldUseLegacyLookup = assetIds.isEmpty() || !mediaIntegrationSettings.enabled
        if (shouldUseLegacyLookup) {
            return legacyAssetLookup(workspaceId, assetIds)
        }

        return resolveReadyAssetsFromMedia(workspaceId, assetIds)
    }

    private suspend fun resolveReadyAssetsFromMedia(
        workspaceId: String,
        assetIds: List<String>,
    ): List<PublicationAsset> {
        val resolvedAssets = withTimeoutOrNull(TIMEOUT_MILLIS) {
            mediaAssetResolver.resolveReadyAssets(workspaceId, assetIds)
        } ?: throw MediaServiceUnavailableException(
            "Media asset resolution timed out after " +
                "${TIMEOUT_MILLIS / MILLIS_PER_SECOND} seconds",
        )

        return resolvedAssets.map { resolvedAsset ->
            PublicationAsset(
                id = resolvedAsset.assetId,
                workspaceId = resolvedAsset.workspaceId,
                sourceType = AssetSourceType.UPLOADED,
                mediaType = resolvedAsset.mediaType,
                storageKey = resolvedAsset.storageKey,
                status = PublicationAssetStatus.READY,
                createdByPrincipalId = MEDIA_CONTEXT_PRINCIPAL_ID,
            )
        }
    }

    private suspend fun legacyAssetLookup(workspaceId: String, assetIds: List<String>): List<PublicationAsset> =
        if (assetIds.isEmpty()) {
            emptyList()
        } else {
            publicationAssetRepository.findByWorkspaceAndIds(workspaceId, assetIds)
        }

    private fun newJobFor(publication: PublicationDraft, now: Instant): PublicationJob = replacementJobFor(
        publication,
        schedulingPolicy,
        now,
    )

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val MILLIS_PER_SECOND = 1_000L
    }
}

// --- DeletePublicationHandler ---

@Service
internal class DeletePublicationHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val publicationRepository: PublicationRepository,
    private val publicationJobRepository: PublicationJobRepository,
    private val recurringScheduleRepository: com.profiletailors.smp.publishing.domain.RecurringScheduleRepository =
        com.profiletailors.smp.publishing.domain.NoOpRecurringScheduleRepository,
    private val notificationEventRepository: com.profiletailors.smp.publishing.domain.NotificationEventRepository =
        com.profiletailors.smp.publishing.domain.NoOpNotificationEventRepository,
    private val clock: Clock,
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy = permissiveEmailVerificationPolicy,
) : CommandWithResultHandler<DeletePublicationCommand, PublicationResult> {
    override suspend fun handle(command: DeletePublicationCommand): PublicationResult {
        val ctxBefore = resourceContextProvider.current()
        java.io.File(
            "/tmp/debug-wf.log",
        ).appendText("[DEBUG_HANDLER] DeletePublicationHandler.handle: current()=$ctxBefore\n")
        val principalCtx = principalContextProvider.require()
        requireEmailVerification(
            principalCtx,
            principalIdentityLookup,
            emailVerificationPolicy,
            AuthFeature.PUBLISH_CONTENT,
        )
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val current = publicationRepository.findByWorkspaceAndId(workspaceId, command.publicationId)
            ?: throw PublicationNotFoundException(command.publicationId)
        recurringScheduleRepository.pauseByTemplatePost(workspaceId, current.id)
        val deleted = publicationRepository.deleteUnpublished(workspaceId, current.id)
        if (!deleted) throw PublicationDeletionNotAllowedException(current.id)
        notificationEventRepository.record(
            com.profiletailors.smp.publishing.domain.NotificationEvent(
                id = "nevt-${java.util.UUID.randomUUID()}", workspaceId = workspaceId,
                provider = current.provider, socialAccountId = current.socialAccountId,
                publicationId = current.id,
                category = com.profiletailors.smp.publishing.domain.NotificationCategory.RECURRENCE_PAUSED,
                message = "Recurring schedule paused because its template post was deleted.",
                suggestedAction = "Create a new recurring schedule from another post.", occurredAt = clock.instant(),
            ),
        )
        return current.toResult()
    }
}

// --- CancelPublicationHandler ---

@Service
internal class CancelPublicationHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val publicationRepository: PublicationRepository,
    private val publicationJobRepository: PublicationJobRepository,
    private val transactionRunner: AtomicTransactionRunner,
    private val clock: Clock,
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy = permissiveEmailVerificationPolicy,
) : CommandWithResultHandler<CancelPublicationCommand, PublicationResult> {
    override suspend fun handle(command: CancelPublicationCommand): PublicationResult {
        val principalCtx = principalContextProvider.require()
        requireEmailVerification(
            principalCtx,
            principalIdentityLookup,
            emailVerificationPolicy,
            AuthFeature.PUBLISH_CONTENT,
        )
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val current = publicationRepository.findByWorkspaceAndId(workspaceId, command.publicationId)
            ?: throw PublicationNotFoundException(command.publicationId)
        val cancelledAt = clock.instant()
        val cancelled = PublicationLifecyclePolicy.cancel(current, cancelledAt)
        transactionRunner.runAtomically {
            publicationRepository.markCancelled(cancelled.id, cancelledAt)
            publicationJobRepository.cancel(cancelled.id, cancelledAt)
        }
        return cancelled.toResult()
    }
}

// --- RetryPublicationHandler ---

@Service
internal class RetryPublicationHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val publicationRepository: PublicationRepository,
    private val publicationJobRepository: PublicationJobRepository,
    private val transactionRunner: AtomicTransactionRunner,
    private val schedulingPolicy: PublicationSchedulingPolicy,
    private val clock: Clock,
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy = permissiveEmailVerificationPolicy,
) : CommandWithResultHandler<RetryPublicationCommand, PublicationResult> {
    override suspend fun handle(command: RetryPublicationCommand): PublicationResult {
        val principalCtx = principalContextProvider.require()
        requireEmailVerification(
            principalCtx,
            principalIdentityLookup,
            emailVerificationPolicy,
            AuthFeature.SCHEDULE_POST,
        )
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val current = publicationRepository.findByWorkspaceAndId(workspaceId, command.publicationId)
            ?: throw PublicationNotFoundException(command.publicationId)
        val now = clock.instant()
        val effectiveMode = command.scheduleMode ?: current.scheduleMode
        val effectiveScheduledFor = command.scheduledFor ?: current.scheduledFor
        requireScheduledInFuture(effectiveMode, effectiveScheduledFor, now)
        val prepared = PublicationLifecyclePolicy.prepareRetry(
            current.copy(
                scheduleMode = effectiveMode,
                scheduledFor = effectiveScheduledFor,
                nextSlotAfter = command.nextSlotAfter ?: current.nextSlotAfter,
                priority = command.priority ?: current.priority,
            ),
        )
        val persisted = transactionRunner.runAtomically {
            publicationRepository.updateEditableDraft(prepared).also { persisted ->
                publicationJobRepository.replaceForPublication(replacementJobFor(persisted, schedulingPolicy, now))
            }
        }
        return persisted.toResult()
    }
}

// --- ReschedulePublicationHandler ---

@Service
internal class ReschedulePublicationHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val publicationRepository: PublicationRepository,
    private val publicationJobRepository: PublicationJobRepository,
    private val transactionRunner: AtomicTransactionRunner,
    private val schedulingPolicy: PublicationSchedulingPolicy,
    private val clock: Clock,
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy = permissiveEmailVerificationPolicy,
) : CommandWithResultHandler<ReschedulePublicationCommand, PublicationResult> {
    override suspend fun handle(command: ReschedulePublicationCommand): PublicationResult {
        val principalCtx = principalContextProvider.require()
        requireEmailVerification(
            principalCtx,
            principalIdentityLookup,
            emailVerificationPolicy,
            AuthFeature.SCHEDULE_POST,
        )
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val current = publicationRepository.findByWorkspaceAndId(workspaceId, command.publicationId)
            ?: throw PublicationNotFoundException(command.publicationId)
        PublicationLifecyclePolicy.requireEditable(current)
        val now = clock.instant()
        requireScheduledInFuture(command.scheduleMode, command.scheduledFor, now)
        val rescheduled = PublicationLifecyclePolicy.queue(
            current.copy(
                scheduleMode = command.scheduleMode,
                scheduledFor = command.scheduledFor,
                nextSlotAfter = command.nextSlotAfter,
                priority = command.priority ?: current.priority,
            ),
            schedulingPolicy.resolveDueAt(
                current.copy(
                    scheduleMode = command.scheduleMode,
                    scheduledFor = command.scheduledFor,
                    nextSlotAfter = command.nextSlotAfter,
                    priority = command.priority ?: current.priority,
                ),
                now,
            ),
        )
        val persisted = transactionRunner.runAtomically {
            publicationRepository.updateEditableDraft(rescheduled).also { persisted ->
                publicationJobRepository.replaceForPublication(replacementJobFor(persisted, schedulingPolicy, now))
            }
        }
        return persisted.toResult()
    }
}
