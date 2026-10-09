package com.profiletailors.smp.shortlinks.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.shortlinks.domain.LinkClickMetricsRepository
import com.profiletailors.smp.shortlinks.domain.LinkId
import com.profiletailors.smp.shortlinks.domain.OwnerId
import com.profiletailors.smp.shortlinks.domain.WorkspaceLinkClickMetrics
import com.profiletailors.smp.shortlinks.domain.WorkspaceLinkCursor
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext
import java.nio.ByteBuffer
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
internal class ListWorkspaceLinkMetricsHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val linkClickMetricsRepository: LinkClickMetricsRepository,
    private val shortLinksProperties: ShortLinksConfigProperties,
) : QueryHandler<ListWorkspaceLinkMetricsQuery, WorkspaceLinkMetricsPage> {
    override suspend fun handle(query: ListWorkspaceLinkMetricsQuery): WorkspaceLinkMetricsPage {
        require(query.limit in MIN_LIMIT..MAX_LIMIT)
        val cursor = query.cursor?.let(::decodeCursor)
        val rows = linkClickMetricsRepository.findWorkspaceLinksWithMetrics(
            OwnerId.from(requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)),
            cursor,
            query.limit + 1,
        )
        val hasNext = rows.size > query.limit
        val included = rows.take(query.limit)
        val nextCursor = if (hasNext) included.lastOrNull()?.let(::encodeCursor) else null
        return WorkspaceLinkMetricsPage(
            included.map { row ->
                val link = row.link.toResult(shortLinksProperties.shortUrlBase)
                WorkspaceLinkMetricResult(
                    id = link.id,
                    shortCode = link.shortCode,
                    shortUrl = link.shortUrl,
                    destinationUrl = link.destinationUrl,
                    status = link.status,
                    createdAt = link.createdAt,
                    expiresAt = link.expiresAt,
                    version = link.version,
                    recordedRedirects = row.recordedRedirects,
                )
            },
            nextCursor,
        )
    }

    private fun encodeCursor(row: WorkspaceLinkClickMetrics): String {
        val id = row.link.id.value
        val payload = ByteBuffer.allocate(CURSOR_BYTES)
            .putLong(row.link.createdAt.epochSecond)
            .putInt(row.link.createdAt.nano)
            .putLong(id.mostSignificantBits)
            .putLong(id.leastSignificantBits)
            .array()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
    }

    private fun decodeCursor(value: String): WorkspaceLinkCursor {
        val bytes = try {
            Base64.getUrlDecoder().decode(value)
        } catch (exception: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid cursor", exception)
        }
        require(bytes.size == CURSOR_BYTES) { "Invalid cursor" }
        val buffer = ByteBuffer.wrap(bytes)
        val createdAt = Instant.ofEpochSecond(buffer.long, buffer.int.toLong())
        val id = UUID(buffer.long, buffer.long)
        return WorkspaceLinkCursor(createdAt, LinkId(id))
    }

    private companion object {
        const val MIN_LIMIT = 1
        const val MAX_LIMIT = 100
        const val CURSOR_BYTES = 28
    }
}
