package com.profiletailors.smp.shortlinks.domain

import java.time.Instant

interface LinkClickMetricsRepository {
    suspend fun countByLinkIdAndOwner(linkId: LinkId, ownerId: OwnerId): Long?

    suspend fun findWorkspaceLinksWithMetrics(
        ownerId: OwnerId,
        cursor: WorkspaceLinkCursor?,
        limit: Int,
    ): List<WorkspaceLinkClickMetrics>
}

data class WorkspaceLinkCursor(val createdAt: Instant, val id: LinkId)

data class WorkspaceLinkClickMetrics(val link: Link, val recordedRedirects: Long)
