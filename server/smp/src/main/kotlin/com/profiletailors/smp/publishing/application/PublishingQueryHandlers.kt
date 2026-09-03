package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.media.application.AssetPreviewUrlResolver
import com.profiletailors.smp.media.application.MediaAssetResolver
import com.profiletailors.smp.media.application.ResolvedAssetSummary
import com.profiletailors.smp.publishing.domain.ActivityThresholds
import com.profiletailors.smp.publishing.domain.ConflictDetectionPolicy
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationJobRepository
import com.profiletailors.smp.publishing.domain.PublicationRepository
import com.profiletailors.smp.publishing.domain.StaleJob
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration

// --- GetCalendarPublicationsHandler ---

@Service
internal class GetCalendarPublicationsHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val publicationRepository: PublicationRepository,
    private val mediaAssetResolver: MediaAssetResolver,
    private val assetPreviewUrlResolver: AssetPreviewUrlResolver,
) : QueryHandler<GetCalendarPublicationsQuery, CalendarResponse> {
    private val logger: Logger = LoggerFactory.getLogger(GetCalendarPublicationsHandler::class.java)
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
        val assetIds = publications.flatMap { it.assetIds }.distinct()
        val assetsById = if (assetIds.isEmpty()) {
            emptyMap()
        } else {
            mediaAssetResolver.resolveReadyAssets(workspaceId, assetIds)
                .associateBy { it.assetId }
        }

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

        val publicationResults = publications.map { publication ->
            publication.toCalendarResult(
                conflictingPublicationIds = conflictMap[publication.id].orEmpty(),
                previewUrl = resolvePreviewUrl(publication, assetsById),
            )
        }

        return CalendarResponse(
            publications = publicationResults,
            conflicts = conflicts,
            activity = activity,
        )
    }

    private suspend fun resolvePreviewUrl(
        publication: PublicationDraft,
        assetsById: Map<String, ResolvedAssetSummary>,
    ): String? {
        val readyAssets = publication.assetIds
            .mapNotNull { assetsById[it] }

        for (asset in readyAssets) {
            val previewUrl = runCatching {
                assetPreviewUrlResolver.resolvePreviewUrl(
                    assetId = asset.assetId,
                    workspaceId = asset.workspaceId,
                    mediaType = asset.mediaType,
                    storageKey = asset.storageKey,
                    externalUrl = null,
                )
            }.onFailure { error ->
                logger.warn("Failed to resolve preview URL for assetId={}", asset.assetId, error)
            }.getOrNull()
            if (previewUrl != null) return previewUrl
        }
        return null
    }
}

// --- ListPublicationsHandler ---

@Service
internal class ListPublicationsHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val publicationRepository: PublicationRepository,
) : QueryHandler<ListPublicationsQuery, ListPublicationsResponse> {

    override suspend fun handle(query: ListPublicationsQuery): ListPublicationsResponse {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)

        val from = query.from
        val to = query.to

        if (from != null && to != null) {
            val statuses = query.status?.let { setOf(it) }
            val accountIds = query.socialAccountId?.let { setOf(it) }
            val publications = publicationRepository.findInDateRange(
                workspaceId = workspaceId,
                from = from,
                to = to,
                statuses = statuses,
                socialAccountIds = accountIds,
            )
            val items = publications.drop(query.offset).take(query.limit).map { it.toListItem() }
            return ListPublicationsResponse(
                publications = items,
                total = publications.size,
            )
        }

        // For open-ended queries, fetch a broad range (last 90 days to next 30 days)
        val now = java.time.Clock.systemUTC().instant()
        val broadFrom = from ?: now.minus(DEFAULT_BROAD_LOOKBACK)
        val broadTo = to ?: now.plus(DEFAULT_BROAD_FORWARD)
        val statuses = query.status?.let { setOf(it) }
        val accountIds = query.socialAccountId?.let { setOf(it) }
        val publications = publicationRepository.findInDateRange(
            workspaceId = workspaceId,
            from = broadFrom,
            to = broadTo,
            statuses = statuses,
            socialAccountIds = accountIds,
        )
        val items = publications.drop(query.offset).take(query.limit).map { it.toListItem() }
        return ListPublicationsResponse(
            publications = items,
            total = publications.size,
        )
    }

    companion object {
        private val DEFAULT_BROAD_LOOKBACK: java.time.Duration = java.time.Duration.ofDays(90)
        private val DEFAULT_BROAD_FORWARD: java.time.Duration = java.time.Duration.ofDays(30)
    }
}

// --- ListStaleJobsHandler ---

/**
 * Surfaces claimed jobs whose lease expired past the configured stale threshold.
 * Maps the domain [StaleJob] snapshot to the safe, PII-free [StaleJobItem] shape.
 */
@Service
internal class ListStaleJobsHandler(
    private val publicationJobRepository: PublicationJobRepository,
    private val clock: Clock,
) : QueryHandler<ListStaleJobsQuery, StaleJobsResponse> {

    override suspend fun handle(query: ListStaleJobsQuery): StaleJobsResponse {
        require(!query.leaseStaleThreshold.isNegative && !query.leaseStaleThreshold.isZero) {
            "Lease stale threshold must be positive."
        }

        val now = clock.instant()
        val stale = publicationJobRepository.findStaleClaims(now, query.leaseStaleThreshold)
        val items = stale.take(query.limit).map { it.toItem(now) }
        return StaleJobsResponse(staleJobs = items, total = stale.size)
    }

    private fun StaleJob.toItem(now: java.time.Instant): StaleJobItem = StaleJobItem(
        jobId = jobId,
        publicationId = publicationId,
        workspaceId = workspaceId,
        claimedByWorker = claimedByWorker,
        claimedAt = claimedAt,
        leaseExpiresAt = leaseExpiresAt,
        ageSeconds = Duration.between(claimedAt, now).toSeconds().coerceAtLeast(0L),
        attemptNumber = attemptNumber,
        suggestedAction = STALE_JOB_SUGGESTED_ACTION,
    )

    private companion object {
        const val STALE_JOB_SUGGESTED_ACTION = "RELEASE_AND_RETRY"
    }
}
