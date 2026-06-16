package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.publishing.domain.ActivityThresholds
import com.profiletailors.smp.publishing.domain.MIN_SCHEDULE_OFFSET
import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.ChannelEvent
import com.profiletailors.smp.publishing.domain.ChannelEventPublisher
import com.profiletailors.smp.publishing.domain.ChannelEventType
import com.profiletailors.smp.publishing.domain.CompleteProviderConnectionCommand
import com.profiletailors.smp.publishing.domain.ConnectedSocialChannel
import com.profiletailors.smp.publishing.domain.ConnectedSocialChannelReadRepository
import com.profiletailors.smp.publishing.domain.ConflictDetectionPolicy
import com.profiletailors.smp.publishing.domain.ExpiredOAuthStateException
import com.profiletailors.smp.publishing.domain.InvalidOAuthStateException
import com.profiletailors.smp.publishing.domain.LinkedInAuthorizationUrlBuilder
import com.profiletailors.smp.publishing.domain.LinkedInOAuthStatePayload
import com.profiletailors.smp.publishing.domain.OAuthStateSigner
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidationInput
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.ProviderNotConfiguredException
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationAssetRepository
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationLifecyclePolicy
import com.profiletailors.smp.publishing.domain.PublicationRepository
import com.profiletailors.smp.publishing.domain.PublicationSchedulingPolicy
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.PublicationValidationException
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.onEach
import java.time.Clock
import java.time.Instant
import java.util.Locale
import java.util.UUID

class PublicationNotFoundException(
    publicationId: String,
) : IllegalArgumentException("Publication '$publicationId' was not found in the active workspace.")

class SocialAccountNotFoundException(
    socialAccountId: String,
) : IllegalArgumentException("Social account '$socialAccountId' was not found in the active workspace.")

/**
 * Validates that a SCHEDULED_AT publication is scheduled at least 5 minutes in the future.
 * This is a belt-and-suspenders guard used by handlers that bypass [PublicationLifecyclePolicy.validateForCreation].
 * NOW and NEXT_SLOT modes are not checked here — they are resolved by the system.
 */
private fun requireScheduledInFuture(scheduleMode: ScheduleMode, scheduledFor: Instant?, now: Instant) {
    if (scheduleMode == ScheduleMode.SCHEDULED_AT) {
        val forTime = requireNotNull(scheduledFor) {
            "SCHEDULED_AT mode requires scheduledFor."
        }
        val earliestAllowed = now.plus(MIN_SCHEDULE_OFFSET)
        require(!forTime.isBefore(earliestAllowed)) {
            "Cannot schedule a publication for $forTime. " +
                "Scheduled time must be at least 5 minutes in the future. " +
                "Earliest allowed: $earliestAllowed"
        }
    }
}

@Service
internal class InitiateLinkedInConnectionHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val oauthStateSigner: OAuthStateSigner,
    private val authorizationUrlBuilder: LinkedInAuthorizationUrlBuilder,
    private val clock: Clock,
) : CommandWithResultHandler<InitiateLinkedInConnectionCommand, LinkedInConnectionInitiationResult> {
    override suspend fun handle(command: InitiateLinkedInConnectionCommand): LinkedInConnectionInitiationResult {
        if (!authorizationUrlBuilder.isConfigured()) {
            throw ProviderNotConfiguredException(SocialProvider.LINKEDIN)
        }
        val principal = principalContextProvider.require()
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val issuedAt = clock.instant()
        val expiresAt = issuedAt.plus(STATE_TTL)
        val state = oauthStateSigner.sign(
            LinkedInOAuthStatePayload(
                provider = SocialProvider.LINKEDIN,
                workspaceId = workspaceId,
                principalId = principal.principalId,
                redirectUri = command.redirectUri,
                nonce = UUID.randomUUID().toString(),
                issuedAt = issuedAt,
                expiresAt = expiresAt,
            ),
        )
        return LinkedInConnectionInitiationResult(
            authorizationUrl = authorizationUrlBuilder.buildAuthorizationUrl(
                state = state,
                redirectUri = command.redirectUri,
            ),
            state = state,
            expiresAt = expiresAt,
        )
    }

    private companion object {
        val STATE_TTL: java.time.Duration = java.time.Duration.ofMinutes(10)
    }
}

@Service
internal class CompleteLinkedInConnectionHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val socialConnectionProvider: SocialConnectionProvider,
    private val oauthStateSigner: OAuthStateSigner,
    private val socialConnectionRepository: SocialConnectionRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val channelEventPublisher: ChannelEventPublisher,
    private val clock: Clock,
) : CommandWithResultHandler<CompleteLinkedInConnectionCommand, SocialConnectionResult> {
    override suspend fun handle(command: CompleteLinkedInConnectionCommand): SocialConnectionResult {
        val principal = principalContextProvider.require()
        val resourceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = requireNotNull(resourceContext.workspaceId)
        validateState(command, principal.principalId, workspaceId)
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
                avatarUrl = providerResult.account.avatarUrl,
                status = SocialConnectionStatus.ACTIVE,
            ),
        )
        channelEventPublisher.publish(
            ChannelEvent(
                type = ChannelEventType.CONNECTED_CHANNEL_UPDATED,
                workspaceId = workspaceId,
                socialAccountId = account.id,
                occurredAt = clock.instant(),
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

    private fun validateState(
        command: CompleteLinkedInConnectionCommand,
        principalId: String,
        workspaceId: String,
    ) {
        val payload = oauthStateSigner.verify(command.state)
        if (!payload.expiresAt.isAfter(clock.instant())) {
            throw ExpiredOAuthStateException()
        }
        requireOAuthState(payload.provider == SocialProvider.LINKEDIN) {
            "OAuth state provider does not match LinkedIn."
        }
        requireOAuthState(payload.workspaceId == workspaceId) {
            "OAuth state workspace does not match the active workspace."
        }
        requireOAuthState(payload.principalId == principalId) {
            "OAuth state principal does not match the active principal."
        }
        requireOAuthState(payload.redirectUri == command.redirectUri) {
            "OAuth state redirect URI does not match the completion request."
        }
    }

    private fun requireOAuthState(condition: Boolean, message: () -> String) {
        if (!condition) {
            throw InvalidOAuthStateException(message())
        }
    }
}

@Service
internal class ListConnectedChannelsHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val connectedSocialChannelReadRepository: ConnectedSocialChannelReadRepository,
) : QueryHandler<ListConnectedChannelsQuery, ConnectedChannelsResponse> {
    override suspend fun handle(query: ListConnectedChannelsQuery): ConnectedChannelsResponse {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val statuses = query.status?.let { setOf(it) } ?: setOf(SocialConnectionStatus.ACTIVE)
        val channels = connectedSocialChannelReadRepository
            .listByWorkspace(workspaceId = workspaceId, statuses = statuses)
            .map { it.toSummary() }
        return ConnectedChannelsResponse(channels)
    }
}

private fun ConnectedSocialChannel.toSummary(): ConnectedSocialChannelSummary = ConnectedSocialChannelSummary(
    socialAccountId = socialAccountId,
    connectionId = connectionId,
    provider = provider,
    accountKind = accountKind,
    displayName = displayName,
    status = status,
    avatarUrl = avatarUrl,
    connectedAt = connectedAt,
    lastSyncedAt = lastSyncedAt,
)

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
        PublicationLifecyclePolicy.validateForCreation(draft, now)
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
        val now = clock.instant()
        val updatedDraft = current.copy(
            title = command.title,
            bodyText = command.bodyText,
            assetIds = command.assetIds,
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
            schedulingPolicy.resolveDueAt(updatedDraft, now)
        )
        val persisted = publicationRepository.updateEditableDraft(queued)
        publicationJobRepository.replaceForPublication(
            PublicationJob(
                id = "pjob-${UUID.randomUUID()}",
                publicationId = persisted.id,
                workspaceId = persisted.workspaceId,
                status = com.profiletailors.smp.publishing.domain.JobStatus.PENDING,
                dueAt = schedulingPolicy.resolveDueAt(persisted, now),
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
        val persisted = publicationRepository.updateEditableDraft(prepared)
        publicationJobRepository.replaceForPublication(
            PublicationJob(
                id = "pjob-${UUID.randomUUID()}",
                publicationId = persisted.id,
                workspaceId = persisted.workspaceId,
                status = com.profiletailors.smp.publishing.domain.JobStatus.PENDING,
                dueAt = schedulingPolicy.resolveDueAt(persisted, now),
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
        val persisted = publicationRepository.updateEditableDraft(rescheduled)
        publicationJobRepository.replaceForPublication(
            PublicationJob(
                id = "pjob-${UUID.randomUUID()}",
                publicationId = persisted.id,
                workspaceId = persisted.workspaceId,
                status = com.profiletailors.smp.publishing.domain.JobStatus.PENDING,
                dueAt = schedulingPolicy.resolveDueAt(persisted, now),
                priorityRank = schedulingPolicy.priorityRank(persisted),
                attemptCount = 0,
                maxAttempts = 1,
            ),
        )
        return persisted.toResult()
    }
}

@Service
internal class CreateAssetHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val publicationAssetRepository: PublicationAssetRepository,

    private val clock: Clock,
) : CommandWithResultHandler<CreateAssetCommand, CreateAssetResult> {
    override suspend fun handle(command: CreateAssetCommand): CreateAssetResult {
        val principal = principalContextProvider.require()
        val resourceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = requireNotNull(resourceContext.workspaceId)
        val now = clock.instant()

        val assetId = "pa-${UUID.randomUUID()}"
        val storageKey = if (command.sourceType == AssetSourceType.UPLOADED) {
            "assets/$workspaceId/$assetId"
        } else {
            null
        }

        val asset = PublicationAsset(
            id = assetId,
            workspaceId = workspaceId,
            sourceType = command.sourceType,
            mediaType = command.mediaType.uppercase(Locale.ROOT),
            storageKey = storageKey,
            externalUrl = command.externalUrl,
            originalFilename = command.originalFilename,
            status = PublicationAssetStatus.READY,
            createdByPrincipalId = principal.principalId,
            createdAt = now,
        )

        publicationAssetRepository.create(asset)

        return CreateAssetResult(
            assetId = asset.id,
            workspaceId = asset.workspaceId,
            sourceType = asset.sourceType,
            mediaType = asset.mediaType,
            status = asset.status,
        )
    }
}

@Service
internal class GetCalendarPublicationsHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val publicationRepository: PublicationRepository,
) : QueryHandler<GetCalendarPublicationsQuery, CalendarResponse> {
    override suspend fun handle(query: GetCalendarPublicationsQuery): CalendarResponse {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)

        val statuses = query.status?.let { setOf(it) }
        val accountIds = query.socialAccountId?.let { setOf(it) }

        val publications = publicationRepository.findInDateRange(
            workspaceId = workspaceId,
            from = query.from,
            to = query.to,
            statuses = statuses,
            socialAccountIds = accountIds,
        )

        val conflictMap = ConflictDetectionPolicy.findConflicts(publications)
        val conflicts = conflictMap.map { (pubId, conflictingIds) ->
            ConflictEntry(publicationId = pubId, conflictingPublicationIds = conflictingIds)
        }

        val dateCounts = publicationRepository.countByDate(
            workspaceId = workspaceId,
            from = query.from,
            to = query.to,
            statuses = statuses,
            timezone = query.timezone,
        )

        val activity = dateCounts.map { dc ->
            ActivityEntry(date = dc.date, density = ActivityThresholds.classify(dc.count), count = dc.count)
        }

        val publicationResults = publications.map { it.toCalendarResult(conflictMap[it.id].orEmpty()) }

        return CalendarResponse(
            publications = publicationResults,
            conflicts = conflicts,
            activity = activity,
        )
    }
}

private fun PublicationDraft.toCalendarResult(
    conflictingPublicationIds: List<String>,
): CalendarPublicationResult = CalendarPublicationResult(
    id = id,
    workspaceId = workspaceId,
    socialAccountId = socialAccountId,
    provider = provider,
    status = status,
    scheduleMode = scheduleMode,
    priority = priority,
    title = title,
    bodyText = bodyText,
    scheduledFor = scheduledFor,
    hasConflict = conflictingPublicationIds.isNotEmpty(),
    conflictingPublicationIds = conflictingPublicationIds,
)

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
