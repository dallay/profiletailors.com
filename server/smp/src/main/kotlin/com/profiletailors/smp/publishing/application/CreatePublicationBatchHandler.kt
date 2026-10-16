package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.authorization.application.WorkspaceMembershipGate
import com.profiletailors.smp.identity.application.AuthFeature
import com.profiletailors.smp.identity.application.EmailVerificationPolicy
import com.profiletailors.smp.identity.application.NoOpPrincipalIdentityLookup
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.application.permissiveEmailVerificationPolicy
import com.profiletailors.smp.identity.application.requireEmailVerification
import com.profiletailors.smp.media.application.MediaAssetResolver
import com.profiletailors.smp.media.application.MediaServiceUnavailableException
import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.NoOpPublicationEventPublisher
import com.profiletailors.smp.publishing.domain.ProviderCapabilityRegistry
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidationInput
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationAssetRepository
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationEvent
import com.profiletailors.smp.publishing.domain.PublicationEventPublisher
import com.profiletailors.smp.publishing.domain.PublicationEventType
import com.profiletailors.smp.publishing.domain.PublicationJob
import com.profiletailors.smp.publishing.domain.PublicationJobRepository
import com.profiletailors.smp.publishing.domain.PublicationLifecyclePolicy
import com.profiletailors.smp.publishing.domain.PublicationRepository
import com.profiletailors.smp.publishing.domain.PublicationSchedulingPolicy
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.PublicationValidationException
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
internal class CreatePublicationBatchHandler(
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
    private val emailVerificationPolicy: EmailVerificationPolicy =
        permissiveEmailVerificationPolicy,
    private val publicationEventPublisher: PublicationEventPublisher = NoOpPublicationEventPublisher,
    private val providerCapabilityRegistry: ProviderCapabilityRegistry? = null,
    private val membershipGate: WorkspaceMembershipGate,
) : CommandWithResultHandler<CreatePublicationBatchCommand, CreatePublicationBatchResult> {

    override suspend fun handle(command: CreatePublicationBatchCommand): CreatePublicationBatchResult {
        val principalCtx = principalContextProvider.require()
        requireEmailVerification(
            principalCtx,
            principalIdentityLookup,
            emailVerificationPolicy,
            AuthFeature.PUBLISH_CONTENT,
        )
        if (command.scheduleMode != ScheduleMode.NOW) {
            requireEmailVerification(
                principalCtx,
                principalIdentityLookup,
                emailVerificationPolicy,
                AuthFeature.SCHEDULE_POST,
            )
        }
        val resourceContext = resourceContextProvider.require()
        val workspaceId = requireNotNull(resourceContext.workspaceId)
        membershipGate.requireActiveMember(workspaceId)

        if (command.targets.isEmpty()) {
            throw PublicationValidationException("At least one target is required.")
        }

        val groupId = "pub-group-${UUID.randomUUID()}"
        val preparedTargets = command.targets.mapIndexed { index, target ->
            prepareOne(index, workspaceId, principalCtx.principalId, command, target, groupId)
        }
        val createdTargets = transactionRunner.runAtomically {
            preparedTargets.mapNotNull { prepared ->
                when (prepared) {
                    is PreparedTargetResult.Invalid -> null
                    is PreparedTargetResult.Valid -> prepared.index to persistPrepared(prepared)
                }
            }
        }
        createdTargets.forEach { (_, created) -> emitCreated(workspaceId, created.draft) }
        val createdByIndex = createdTargets.toMap()
        val results = preparedTargets.map { prepared ->
            when (prepared) {
                is PreparedTargetResult.Invalid -> prepared.result
                is PreparedTargetResult.Valid -> createdByIndex.getValue(prepared.index).result
            }
        }
        val successCount = results.count { it.publicationId != null }
        val status = when {
            successCount == results.size -> BatchPublicationStatus.ALL_CREATED
            successCount == 0 -> BatchPublicationStatus.ALL_FAILED
            else -> BatchPublicationStatus.PARTIAL
        }
        return CreatePublicationBatchResult(status = status, results = results)
    }

    private suspend fun prepareOne(
        index: Int,
        workspaceId: String,
        authorPrincipalId: String,
        command: CreatePublicationBatchCommand,
        target: BatchPublicationTarget,
        groupId: String,
    ): PreparedTargetResult {
        val account = when (
            val result = validateTarget { requireSocialAccount(workspaceId, target.socialAccountId) }
        ) {
            is TargetValidation.Valid -> result.value
            is TargetValidation.Invalid -> return PreparedTargetResult.Invalid(
                accountNotFound(target.socialAccountId, groupId, result.cause),
            )
        }
        val assets = when (
            val result = validateTarget { resolveAssets(workspaceId, target.assetIds) }
        ) {
            is TargetValidation.Valid -> result.value
            is TargetValidation.Invalid -> return PreparedTargetResult.Invalid(
                assetResolutionFailure(target.socialAccountId, groupId, result.cause),
            )
        }
        val now = clock.instant()
        val draft = buildDraft(workspaceId, authorPrincipalId, command, target, groupId, account, now)
        when (val validation = validateProvider(draft, account, assets, now)) {
            is TargetValidation.Valid -> Unit
            is TargetValidation.Invalid -> return PreparedTargetResult.Invalid(
                capabilityViolation(target.socialAccountId, groupId, validation.cause),
            )
        }
        val queued = PublicationLifecyclePolicy.queue(draft, schedulingPolicy.resolveDueAt(draft, now))
        return PreparedTargetResult.Valid(index, target, queued, now)
    }

    private suspend fun persistPrepared(prepared: PreparedTargetResult.Valid): PersistedTarget {
        val created = publicationRepository.createDraft(prepared.draft)
        publicationJobRepository.enqueue(newJobFor(created, prepared.createdAt))
        return PersistedTarget(
            draft = created,
            result = BatchPublicationTargetResult(
                socialAccountId = prepared.target.socialAccountId,
                publicationId = created.id,
                groupId = created.groupId,
                status = CREATED_STATUS,
            ),
        )
    }

    private suspend fun <T> validateTarget(validation: suspend () -> T): TargetValidation<T> = try {
        TargetValidation.Valid(validation())
    } catch (ex: PublicationValidationException) {
        TargetValidation.Invalid(ex)
    }

    private fun buildDraft(
        workspaceId: String,
        authorPrincipalId: String,
        command: CreatePublicationBatchCommand,
        target: BatchPublicationTarget,
        groupId: String,
        account: SocialAccount,
        now: Instant,
    ): PublicationDraft = PublicationDraft(
        id = "pub-${UUID.randomUUID()}",
        workspaceId = workspaceId,
        authorPrincipalId = authorPrincipalId,
        provider = account.provider,
        socialAccountId = account.id,
        status = PublicationStatus.DRAFT,
        scheduleMode = command.scheduleMode,
        priority = command.priority,
        title = command.title,
        bodyText = target.bodyText ?: command.bodyText,
        assetIds = target.assetIds,
        groupId = groupId,
        scheduledFor = command.scheduledFor,
        nextSlotAfter = command.nextSlotAfter,
        createdAt = now,
        updatedAt = now,
    )

    private fun validateProvider(
        draft: PublicationDraft,
        account: SocialAccount,
        assets: List<PublicationAsset>,
        now: Instant,
    ): TargetValidation<Unit> = try {
        PublicationLifecyclePolicy.validateForCreation(draft, now)
        resolveCapabilityValidator(account.provider, providerCapabilityRegistry, providerCapabilityValidator)
            .validate(ProviderCapabilityValidationInput(account.provider, account, draft, assets))
        TargetValidation.Valid(Unit)
    } catch (ex: PublicationValidationException) {
        TargetValidation.Invalid(ex)
    }

    private fun accountNotFound(socialAccountId: String, groupId: String, cause: PublicationValidationException) =
        failedTarget(socialAccountId, groupId, ACCOUNT_NOT_FOUND_CODE, cause.message ?: ACCOUNT_UNAVAILABLE_MESSAGE)

    private fun assetResolutionFailure(
        socialAccountId: String,
        groupId: String,
        cause: PublicationValidationException,
    ) = failedTarget(socialAccountId, groupId, ASSET_RESOLUTION_CODE, cause.message ?: ASSET_RESOLUTION_MESSAGE)

    private fun capabilityViolation(socialAccountId: String, groupId: String, cause: PublicationValidationException) =
        failedTarget(socialAccountId, groupId, CAPABILITY_VIOLATION_CODE, cause.message ?: CAPABILITY_VIOLATION_MESSAGE)

    private fun failedTarget(socialAccountId: String, groupId: String, errorCode: String, message: String) =
        BatchPublicationTargetResult(
            socialAccountId = socialAccountId,
            publicationId = null,
            groupId = groupId,
            status = BAD_REQUEST_STATUS,
            errorCode = errorCode,
            errorMessage = message,
        )

    private fun emitCreated(workspaceId: String, persisted: PublicationDraft) {
        publicationEventPublisher.publishBestEffort(
            PublicationEvent(
                type = PublicationEventType.CREATED,
                workspaceId = workspaceId,
                publicationId = persisted.id,
                socialAccountId = persisted.socialAccountId,
                occurredAt = clock.instant(),
            ),
        )
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
                createdByPrincipalId = com.profiletailors.smp.publishing.application.MEDIA_CONTEXT_PRINCIPAL_ID,
            )
        }
    }

    private suspend fun legacyAssetLookup(workspaceId: String, assetIds: List<String>): List<PublicationAsset> =
        if (assetIds.isEmpty()) {
            emptyList()
        } else {
            publicationAssetRepository.findByWorkspaceAndIds(workspaceId, assetIds)
        }

    private suspend fun requireSocialAccount(workspaceId: String, socialAccountId: String): SocialAccount =
        socialAccountRepository.findByWorkspaceAndId(workspaceId, socialAccountId)
            ?: throw PublicationValidationException("Social account $socialAccountId not found.")

    private fun newJobFor(publication: PublicationDraft, now: Instant): PublicationJob = replacementJobFor(
        publication,
        schedulingPolicy,
        now,
    )

    private sealed interface PreparedTargetResult {
        data class Invalid(val result: BatchPublicationTargetResult) : PreparedTargetResult

        data class Valid(
            val index: Int,
            val target: BatchPublicationTarget,
            val draft: PublicationDraft,
            val createdAt: Instant,
        ) : PreparedTargetResult
    }

    private data class PersistedTarget(val draft: PublicationDraft, val result: BatchPublicationTargetResult)

    private sealed interface TargetValidation<out T> {
        data class Valid<T>(val value: T) : TargetValidation<T>
        data class Invalid(val cause: PublicationValidationException) : TargetValidation<Nothing>
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val BAD_REQUEST_STATUS = "BAD_REQUEST"
        const val CREATED_STATUS = "CREATED"
        const val ACCOUNT_NOT_FOUND_CODE = "ACCOUNT_NOT_FOUND"
        const val ASSET_RESOLUTION_CODE = "ASSET_RESOLUTION"
        const val CAPABILITY_VIOLATION_CODE = "CAPABILITY_VIOLATION"
        const val ACCOUNT_UNAVAILABLE_MESSAGE = "Social account is unavailable."
        const val ASSET_RESOLUTION_MESSAGE = "One or more assets could not be resolved."
        const val CAPABILITY_VIOLATION_MESSAGE = "The target content is not supported by this account."
    }
}
