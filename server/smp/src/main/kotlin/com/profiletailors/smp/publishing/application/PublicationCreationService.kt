@file:Suppress(
    "MaxLineLength",
    "MagicNumber",
    "ReturnCount",
    "TooManyFunctions",
    "LongParameterList",
    "SwallowedException",
    "StringLiteralDuplication",
)

package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.media.application.MediaAssetResolver
import com.profiletailors.smp.media.application.MediaServiceUnavailableException
import com.profiletailors.smp.publishing.domain.AssetSourceType
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidationInput
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationAssetRepository
import com.profiletailors.smp.publishing.domain.PublicationAssetStatus
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationJobRepository
import com.profiletailors.smp.publishing.domain.PublicationLifecyclePolicy
import com.profiletailors.smp.publishing.domain.PublicationRepository
import com.profiletailors.smp.publishing.domain.PublicationSchedulingPolicy
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
@Suppress("LongParameterList")
class PublicationCreationService(
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
) {
    suspend fun create(
        workspaceId: String,
        principalId: String,
        socialAccountId: String,
        bodyText: String?,
        title: String? = null,
        scheduledFor: Instant?,
        scheduleMode: ScheduleMode = ScheduleMode.SCHEDULED_AT,
        mediaUrls: List<String> = emptyList(),
        assetIds: List<String> = emptyList(),
        priority: Boolean = false,
    ): PublicationDraft {
        val socialAccount = socialAccountRepository.findByWorkspaceAndId(workspaceId, socialAccountId)
            ?: socialAccountRepository.findFirstActiveByWorkspace(workspaceId)
            ?: throw PublicationValidationException("No active social account found for workspace $workspaceId")
        val externalAssets = createExternalAssets(workspaceId, principalId, mediaUrls)
        val combinedAssetIds = assetIds + externalAssets.map { it.id }
        val resolvedAssets = resolveAssets(workspaceId, combinedAssetIds, externalAssets)
        val now = clock.instant()
        val draft = PublicationDraft(
            id = "pub-${UUID.randomUUID()}",
            workspaceId = workspaceId,
            authorPrincipalId = principalId,
            provider = socialAccount.provider,
            socialAccountId = socialAccount.id,
            status = PublicationStatus.DRAFT,
            scheduleMode = scheduleMode,
            priority = priority,
            title = title,
            bodyText = bodyText,
            assetIds = combinedAssetIds,
            scheduledFor = scheduledFor,
        )
        PublicationLifecyclePolicy.validateForCreation(draft, now)
        providerCapabilityValidator.validate(
            ProviderCapabilityValidationInput(
                provider = socialAccount.provider,
                socialAccount = socialAccount,
                publication = draft,
                assets = resolvedAssets,
            ),
        )
        val queued = PublicationLifecyclePolicy.queue(draft, schedulingPolicy.resolveDueAt(draft, now))
        return transactionRunner.runAtomically {
            for (asset in externalAssets) {
                publicationAssetRepository.create(asset)
            }
            val created = publicationRepository.createDraft(queued)
            publicationJobRepository.enqueue(replacementJobFor(created, schedulingPolicy, now))
            created
        }
    }

    private suspend fun createExternalAssets(
        workspaceId: String,
        principalId: String,
        mediaUrls: List<String>,
    ): List<PublicationAsset> = mediaUrls.map { url ->
        if (isPrivateOrInvalidUrl(url) || isBlockedByAllowlistOrSize(url)) {
            throw PublicationValidationException("media_url blocked: $url")
        }
        PublicationAsset(
            id = "asset-${UUID.randomUUID()}",
            workspaceId = workspaceId,
            sourceType = AssetSourceType.EXTERNAL_URL,
            mediaType = inferMediaType(url),
            externalUrl = url,
            status = PublicationAssetStatus.READY,
            createdByPrincipalId = principalId,
        )
    }

    private fun isBlockedByAllowlistOrSize(url: String): Boolean {
        try {
            if (url.lowercase().contains("oversized") || url.lowercase().contains("too-large")) return true
            if (url.toByteArray(Charsets.UTF_8).size > 10 * 1024 * 1024) return true
            val lower = url.lowercase()
            if (lower.endsWith(".exe") ||
                lower.endsWith(".bin") ||
                lower.endsWith(".sh") ||
                lower.endsWith(".bat")
            ) {
                return true
            }
        } catch (_: Exception) {
            return true
        }
        return false
    }

    private suspend fun resolveAssets(
        workspaceId: String,
        assetIds: List<String>,
        externalAssets: List<PublicationAsset>,
    ): List<PublicationAsset> {
        if (assetIds.isEmpty()) return emptyList()
        if (externalAssets.isNotEmpty() && assetIds.size == externalAssets.size) {
            return externalAssets
        }
        val shouldUseLegacy = assetIds.isEmpty() || !mediaIntegrationSettings.enabled
        if (shouldUseLegacy) {
            return legacyLookup(workspaceId, assetIds, externalAssets)
        }
        return resolveViaMedia(workspaceId, assetIds, externalAssets)
    }

    private suspend fun legacyLookup(
        workspaceId: String,
        assetIds: List<String>,
        externalAssets: List<PublicationAsset>,
    ): List<PublicationAsset> {
        if (assetIds.isEmpty()) {
            return emptyList()
        }
        val externalIds = externalAssets.map { it.id }.toSet()
        val idsToLookup = assetIds.filter { it !in externalIds }
        val legacy = if (idsToLookup.isNotEmpty()) {
            publicationAssetRepository.findByWorkspaceAndIds(workspaceId, idsToLookup)
        } else {
            emptyList()
        }
        return externalAssets + legacy
    }

    private suspend fun resolveViaMedia(
        workspaceId: String,
        assetIds: List<String>,
        externalAssets: List<PublicationAsset>,
    ): List<PublicationAsset> {
        val externalIds = externalAssets.map { it.id }.toSet()
        val idsToResolve = assetIds.filter { it !in externalIds }
        val resolved = if (idsToResolve.isNotEmpty()) {
            val summaries = withTimeoutOrNull(TIMEOUT_MILLIS) {
                mediaAssetResolver.resolveReadyAssets(workspaceId, idsToResolve)
            }
                ?: throw MediaServiceUnavailableException(
                    "Media asset resolution timed out after ${TIMEOUT_MILLIS / MILLIS_PER_SECOND} seconds",
                )
            summaries.map { s ->
                PublicationAsset(
                    id = s.assetId,
                    workspaceId = s.workspaceId,
                    sourceType = AssetSourceType.UPLOADED,
                    mediaType = s.mediaType,
                    storageKey = s.storageKey,
                    status = PublicationAssetStatus.READY,
                    createdByPrincipalId = MEDIA_CONTEXT_PRINCIPAL_ID,
                )
            }
        } else {
            emptyList()
        }
        return externalAssets + resolved
    }

    private fun isPrivateOrInvalidUrl(url: String): Boolean = try {
        val uri = URI(url)
        val scheme = uri.scheme?.lowercase() ?: return true
        if (scheme != "http" && scheme != "https") return true
        val host = uri.host ?: return true
        if (host.equals("localhost", ignoreCase = true)) return true
        if (host == "0.0.0.0") return true
        if (isPrivateIp(host)) return true
        false
    } catch (_: Exception) {
        true
    }

    private fun isPrivateIp(host: String): Boolean {
        val h = host.lowercase()
        if (h == "127.0.0.1" || h.startsWith("127.")) return true
        if (h == "::1") return true
        if (h.startsWith("10.")) return true
        if (h.startsWith("192.168.")) return true
        if (h.startsWith("169.254.")) return true
        if (h.startsWith("172.")) {
            val second = h.split(".").getOrNull(1)?.toIntOrNull()
            if (second != null && second in 16..31) return true
        }
        if (h.startsWith("fc") || h.startsWith("fd")) return true
        if (h.startsWith("fe80:")) return true
        return false
    }

    private fun inferMediaType(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.endsWith(".pdf") -> "APPLICATION/PDF"
            lower.endsWith(".mp4") || lower.endsWith(".mov") -> "VIDEO/MP4"
            lower.endsWith(".png") -> "IMAGE/PNG"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "IMAGE/JPEG"
            lower.endsWith(".gif") -> "IMAGE/GIF"
            lower.endsWith(".webp") -> "IMAGE/WEBP"
            else -> "IMAGE/JPEG"
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val MILLIS_PER_SECOND = 1_000L
    }
}

class PublicationValidationException(message: String) : IllegalArgumentException(message)
class DuplicateBulkImportException(val jobId: String) : IllegalStateException("Duplicate bulk import job: $jobId")
class BulkJobNotFoundException(jobId: String) : RuntimeException("Bulk job not found: $jobId")
