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
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationJob
import com.profiletailors.smp.publishing.domain.PublicationJobRepository
import com.profiletailors.smp.publishing.domain.PublicationLifecyclePolicy
import com.profiletailors.smp.publishing.domain.PublicationRepository
import com.profiletailors.smp.publishing.domain.PublicationSchedulingPolicy
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Creates a new publication draft, validates it through the lifecycle policy and provider
 * capability checks, resolves assets through the media context (or legacy lookup), and
 * enqueues a job for processing.
 */
@Suppress("LongParameterList")
@Service
internal class CreatePublicationHandler(
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
) : CommandWithResultHandler<CreatePublicationCommand, PublicationResult> {
    override suspend fun handle(command: CreatePublicationCommand): PublicationResult {
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
        val resourceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = requireNotNull(resourceContext.workspaceId)
        val socialAccount = requireSocialAccount(workspaceId, command.socialAccountId)

        // Resolve assets through the media context when the integration is enabled,
        // otherwise fall back to the legacy repository lookup.
        // Short-circuit empty assetIds so zero-asset publications still succeed even when media is unavailable.
        val assets = resolveAssets(workspaceId, command.assetIds)

        val now = clock.instant()
        val draft = PublicationDraft(
            id = "pub-${UUID.randomUUID()}",
            workspaceId = workspaceId,
            authorPrincipalId = principalCtx.principalId,
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
        val persisted = transactionRunner.runAtomically {
            val created = publicationRepository.createDraft(queued)
            publicationJobRepository.enqueue(newJobFor(created, now))
            created
        }
        return persisted.toResult()
    }

    /**
     * Resolves assets through the media context or falls back to legacy lookup.
     *
     * When `mediaIntegrationSettings.enabled` is true and `assetIds` is non-empty:
     *   - Calls `mediaAssetResolver.resolveReadyAssets(workspaceId, assetIds)` with a 5-second timeout
     *   - Throws `MediaServiceUnavailableException` on timeout or infrastructure failure
     *   - Throws `AssetNotReadyException` for missing, cross-workspace, or non-READY assets
     *
     * When `mediaIntegrationSettings.enabled` is false or `assetIds` is empty:
     *   - Falls back to the legacy `publicationAssetRepository` lookup (no media context call)
     *
     * The 5-second timeout ensures that media context unavailability fails fast and returns
     * HTTP 503 rather than allowing publication creation to silently skip validation.
     */
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

    private suspend fun requireSocialAccount(workspaceId: String, socialAccountId: String): SocialAccount =
        socialAccountRepository.findByWorkspaceAndId(workspaceId, socialAccountId)
            ?: throw SocialAccountNotFoundException(socialAccountId)

    private fun newJobFor(publication: PublicationDraft, now: Instant): PublicationJob = replacementJobFor(
        publication,
        schedulingPolicy,
        now,
    )

    private companion object {
        /** 5-second timeout for media asset resolution, matching the design spec. */
        const val TIMEOUT_MILLIS = 5_000L
        const val MILLIS_PER_SECOND = 1_000L
    }
}
