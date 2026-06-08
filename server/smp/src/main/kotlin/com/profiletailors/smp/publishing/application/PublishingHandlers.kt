package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.publishing.domain.CompleteProviderConnectionCommand
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidationInput
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationLifecyclePolicy
import com.profiletailors.smp.publishing.domain.PublicationRepository
import com.profiletailors.smp.publishing.domain.PublicationSchedulingPolicy
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.PublicationValidationException
import com.profiletailors.smp.publishing.domain.PublicationAssetRepository
import com.profiletailors.smp.publishing.domain.PublicationJob
import com.profiletailors.smp.publishing.domain.PublicationJobRepository
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialConnection
import com.profiletailors.smp.publishing.domain.SocialConnectionProvider
import com.profiletailors.smp.publishing.domain.SocialConnectionRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext
import java.time.Clock
import java.time.Instant
import java.util.UUID

class PublicationNotFoundException(
    publicationId: String,
) : IllegalArgumentException("Publication '$publicationId' was not found in the active workspace.")

class SocialAccountNotFoundException(
    socialAccountId: String,
) : IllegalArgumentException("Social account '$socialAccountId' was not found in the active workspace.")

@Service
internal class CompleteLinkedInConnectionHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val socialConnectionProvider: SocialConnectionProvider,
    private val socialConnectionRepository: SocialConnectionRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val clock: Clock,
) : CommandWithResultHandler<CompleteLinkedInConnectionCommand, SocialConnectionResult> {
    override suspend fun handle(command: CompleteLinkedInConnectionCommand): SocialConnectionResult {
        val principal = principalContextProvider.require()
        val resourceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = requireNotNull(resourceContext.workspaceId)
        val providerResult = socialConnectionProvider.completeConnection(
            CompleteProviderConnectionCommand(
                workspaceId = workspaceId,
                actorPrincipalId = principal.principalId,
                authorizationCode = command.authorizationCode,
                redirectUri = command.redirectUri,
            ),
        )

        val connection = socialConnectionRepository.upsert(
            SocialConnection(
                id = "soconn-${UUID.randomUUID()}",
                workspaceId = workspaceId,
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = providerResult.providerConnectionRef,
                status = SocialConnectionStatus.ACTIVE,
                credentialReference = providerResult.credentialReference,
                connectedAt = clock.instant(),
            ),
        )
        val account = socialAccountRepository.upsert(
            SocialAccount(
                id = "soacc-${UUID.randomUUID()}",
                socialConnectionId = connection.id,
                workspaceId = workspaceId,
                provider = SocialProvider.LINKEDIN,
                providerAccountId = providerResult.account.providerAccountId,
                kind = providerResult.account.kind,
                displayName = providerResult.account.displayName,
                profileUrn = providerResult.account.profileUrn,
                status = SocialConnectionStatus.ACTIVE,
            ),
        )

        return SocialConnectionResult(
            connectionId = connection.id,
            workspaceId = connection.workspaceId,
            provider = connection.provider,
            status = connection.status,
            account = SocialAccountSummary(
                accountId = account.id,
                providerAccountId = account.providerAccountId,
                displayName = account.displayName,
                kind = account.kind,
                profileUrn = account.profileUrn,
            ),
        )
    }
}

@Service
internal class CreatePublicationHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val socialAccountRepository: SocialAccountRepository,
    private val publicationRepository: PublicationRepository,
    private val publicationAssetRepository: PublicationAssetRepository,
    private val publicationJobRepository: PublicationJobRepository,
    private val providerCapabilityValidator: ProviderCapabilityValidator,
    private val schedulingPolicy: PublicationSchedulingPolicy,
    private val clock: Clock,
) : CommandWithResultHandler<CreatePublicationCommand, PublicationResult> {
    override suspend fun handle(command: CreatePublicationCommand): PublicationResult {
        val principal = principalContextProvider.require()
        val resourceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = requireNotNull(resourceContext.workspaceId)
        val socialAccount = requireSocialAccount(workspaceId, command.socialAccountId)
        val assets = publicationAssetRepository.findByWorkspaceAndIds(workspaceId, command.assetIds)
        val now = clock.instant()
        val draft = PublicationDraft(
            id = "pub-${UUID.randomUUID()}",
            workspaceId = workspaceId,
            authorPrincipalId = principal.principalId,
            provider = socialAccount.provider,
            socialAccountId = socialAccount.id,
            status = PublicationStatus.DRAFT,
            scheduleMode = command.scheduleMode,
            priority = command.priority,
            title = command.title,
            bodyText = command.bodyText,
            assetIds = command.assetIds,
            scheduledFor = command.scheduledFor,
            nextSlotAfter = command.nextSlotAfter,
        )
        PublicationLifecyclePolicy.validateForCreation(draft)
        providerCapabilityValidator.validate(
            ProviderCapabilityValidationInput(
                provider = socialAccount.provider,
                socialAccount = socialAccount,
                publication = draft,
                assets = assets,
            ),
        )
        val queued = PublicationLifecyclePolicy.queue(draft, schedulingPolicy.resolveDueAt(draft, now))
        val persisted = publicationRepository.createDraft(queued)
        publicationJobRepository.enqueue(newJobFor(persisted, now))
        return persisted.toResult()
    }

    private suspend fun requireSocialAccount(workspaceId: String, socialAccountId: String): SocialAccount =
        socialAccountRepository.findByWorkspaceAndId(workspaceId, socialAccountId)
            ?: throw SocialAccountNotFoundException(socialAccountId)

    private fun newJobFor(publication: PublicationDraft, now: Instant): PublicationJob = PublicationJob(
        id = "pjob-${UUID.randomUUID()}",
        publicationId = publication.id,
        workspaceId = publication.workspaceId,
        status = com.profiletailors.smp.publishing.domain.JobStatus.PENDING,
        dueAt = schedulingPolicy.resolveDueAt(publication, now),
        priorityRank = schedulingPolicy.priorityRank(publication),
        attemptCount = 0,
        maxAttempts = 1,
    )
}

@Service
internal class EditPublicationHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val socialAccountRepository: SocialAccountRepository,
    private val publicationRepository: PublicationRepository,
    private val publicationAssetRepository: PublicationAssetRepository,
    private val publicationJobRepository: PublicationJobRepository,
    private val providerCapabilityValidator: ProviderCapabilityValidator,
    private val schedulingPolicy: PublicationSchedulingPolicy,
    private val clock: Clock,
) : CommandWithResultHandler<EditPublicationCommand, PublicationResult> {
    override suspend fun handle(command: EditPublicationCommand): PublicationResult {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val current = publicationRepository.findByWorkspaceAndId(workspaceId, command.publicationId)
            ?: throw PublicationNotFoundException(command.publicationId)
        PublicationLifecyclePolicy.requireEditable(current)
        val account = socialAccountRepository.findByWorkspaceAndId(workspaceId, current.socialAccountId)
            ?: throw SocialAccountNotFoundException(current.socialAccountId)
        val assets = publicationAssetRepository.findByWorkspaceAndIds(workspaceId, command.assetIds)
        val updatedDraft = current.copy(
            title = command.title,
            bodyText = command.bodyText,
            assetIds = command.assetIds,
            scheduleMode = command.scheduleMode,
            scheduledFor = command.scheduledFor,
            nextSlotAfter = command.nextSlotAfter,
            priority = command.priority,
        )
        PublicationLifecyclePolicy.validateForCreation(updatedDraft.copy(status = PublicationStatus.DRAFT))
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
            schedulingPolicy.resolveDueAt(updatedDraft, clock.instant())
        )
        val persisted = publicationRepository.updateEditableDraft(queued)
        publicationJobRepository.replaceForPublication(
            PublicationJob(
                id = "pjob-${UUID.randomUUID()}",
                publicationId = persisted.id,
                workspaceId = persisted.workspaceId,
                status = com.profiletailors.smp.publishing.domain.JobStatus.PENDING,
                dueAt = schedulingPolicy.resolveDueAt(persisted, clock.instant()),
                priorityRank = schedulingPolicy.priorityRank(persisted),
                attemptCount = 0,
                maxAttempts = 1,
            ),
        )
        return persisted.toResult()
    }
}

@Service
internal class CancelPublicationHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val publicationRepository: PublicationRepository,
    private val publicationJobRepository: PublicationJobRepository,
    private val clock: Clock,
) : CommandWithResultHandler<CancelPublicationCommand, PublicationResult> {
    override suspend fun handle(command: CancelPublicationCommand): PublicationResult {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val current = publicationRepository.findByWorkspaceAndId(workspaceId, command.publicationId)
            ?: throw PublicationNotFoundException(command.publicationId)
        val cancelledAt = clock.instant()
        val cancelled = PublicationLifecyclePolicy.cancel(current, cancelledAt)
        publicationRepository.markCancelled(cancelled.id, cancelledAt)
        publicationJobRepository.cancel(cancelled.id, cancelledAt)
        return cancelled.toResult()
    }
}

@Service
internal class RetryPublicationHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val publicationRepository: PublicationRepository,
    private val publicationJobRepository: PublicationJobRepository,
    private val schedulingPolicy: PublicationSchedulingPolicy,
    private val clock: Clock,
) : CommandWithResultHandler<RetryPublicationCommand, PublicationResult> {
    override suspend fun handle(command: RetryPublicationCommand): PublicationResult {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val current = publicationRepository.findByWorkspaceAndId(workspaceId, command.publicationId)
            ?: throw PublicationNotFoundException(command.publicationId)
        val prepared = PublicationLifecyclePolicy.prepareRetry(
            current.copy(
                scheduleMode = command.scheduleMode ?: current.scheduleMode,
                scheduledFor = command.scheduledFor ?: current.scheduledFor,
                nextSlotAfter = command.nextSlotAfter ?: current.nextSlotAfter,
                priority = command.priority ?: current.priority,
            ),
        )
        val persisted = publicationRepository.updateEditableDraft(prepared)
        publicationJobRepository.replaceForPublication(
            PublicationJob(
                id = "pjob-${UUID.randomUUID()}",
                publicationId = persisted.id,
                workspaceId = persisted.workspaceId,
                status = com.profiletailors.smp.publishing.domain.JobStatus.PENDING,
                dueAt = schedulingPolicy.resolveDueAt(persisted, clock.instant()),
                priorityRank = schedulingPolicy.priorityRank(persisted),
                attemptCount = 0,
                maxAttempts = 1,
            ),
        )
        return persisted.toResult()
    }
}

@Service
internal class ReschedulePublicationHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val publicationRepository: PublicationRepository,
    private val publicationJobRepository: PublicationJobRepository,
    private val schedulingPolicy: PublicationSchedulingPolicy,
    private val clock: Clock,
) : CommandWithResultHandler<ReschedulePublicationCommand, PublicationResult> {
    override suspend fun handle(command: ReschedulePublicationCommand): PublicationResult {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val current = publicationRepository.findByWorkspaceAndId(workspaceId, command.publicationId)
            ?: throw PublicationNotFoundException(command.publicationId)
        PublicationLifecyclePolicy.requireEditable(current)
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
                clock.instant(),
            ),
        )
        val persisted = publicationRepository.updateEditableDraft(rescheduled)
        publicationJobRepository.replaceForPublication(
            PublicationJob(
                id = "pjob-${UUID.randomUUID()}",
                publicationId = persisted.id,
                workspaceId = persisted.workspaceId,
                status = com.profiletailors.smp.publishing.domain.JobStatus.PENDING,
                dueAt = schedulingPolicy.resolveDueAt(persisted, clock.instant()),
                priorityRank = schedulingPolicy.priorityRank(persisted),
                attemptCount = 0,
                maxAttempts = 1,
            ),
        )
        return persisted.toResult()
    }
}

private fun PublicationDraft.toResult(): PublicationResult = PublicationResult(
    publicationId = id,
    workspaceId = workspaceId,
    socialAccountId = socialAccountId,
    status = status,
    scheduleMode = scheduleMode,
    priority = priority,
    title = title,
    bodyText = bodyText,
    assetIds = assetIds,
    scheduledFor = scheduledFor,
    nextSlotAfter = nextSlotAfter,
)
