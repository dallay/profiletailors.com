package com.profiletailors.smp.shortlinks.infrastructure.persistence

import com.profiletailors.smp.shortlinks.domain.DestinationUrl
import com.profiletailors.smp.shortlinks.domain.DomainId
import com.profiletailors.smp.shortlinks.domain.Link
import com.profiletailors.smp.shortlinks.domain.LinkClickMetricsRepository
import com.profiletailors.smp.shortlinks.domain.LinkId
import com.profiletailors.smp.shortlinks.domain.LinkStatus
import com.profiletailors.smp.shortlinks.domain.OwnerId
import com.profiletailors.smp.shortlinks.domain.ShortCode
import com.profiletailors.smp.shortlinks.domain.WorkspaceLinkClickMetrics
import com.profiletailors.smp.shortlinks.domain.WorkspaceLinkCursor
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class R2dbcLinkClickMetricsRepository(private val databaseClient: DatabaseClient) : LinkClickMetricsRepository {
    override suspend fun findWorkspaceLinksWithMetrics(
        ownerId: OwnerId,
        cursor: WorkspaceLinkCursor?,
        limit: Int,
    ): List<WorkspaceLinkClickMetrics> {
        val sql = buildString {
            append(
                """
                SELECT links.id, links.owner_id, links.domain_id, links.short_code, links.destination_url,
                       links.status, links.expires_at, links.created_at, links.updated_at, links.deleted_at,
                       links.version, COUNT(link_clicks.id) AS click_count
                FROM links
                LEFT JOIN link_clicks ON link_clicks.link_id = links.id
                WHERE links.owner_id = :ownerId AND links.deleted_at IS NULL
                """.trimIndent(),
            )
            if (cursor != null) append(" AND (links.created_at, links.id) < (:createdAt, :linkId)")
            append(" GROUP BY links.id ORDER BY links.created_at DESC, links.id DESC LIMIT :limit")
        }
        var statement = databaseClient.sql(sql).bind("ownerId", ownerId.value)
        if (cursor != null) {
            statement = statement.bind("createdAt", cursor.createdAt).bind("linkId", cursor.id.value)
        }
        return statement.bind("limit", limit).map { row, _ -> mapWorkspaceMetric(row) }.all().collectList()
            .awaitSingle()
    }

    override suspend fun countByLinkIdAndOwner(linkId: LinkId, ownerId: OwnerId): Long? = databaseClient.sql(
        """
            SELECT COUNT(link_clicks.id) AS click_count
            FROM links
            LEFT JOIN link_clicks ON link_clicks.link_id = links.id
            WHERE links.id = :linkId AND links.owner_id = :ownerId AND links.deleted_at IS NULL
            GROUP BY links.id
        """.trimIndent(),
    )
        .bind("linkId", linkId.value)
        .bind("ownerId", ownerId.value)
        .map { row, _ -> requireNotNull(row.get("click_count", Long::class.javaObjectType)) }
        .one()
        .awaitSingleOrNull()

    private fun mapWorkspaceMetric(row: io.r2dbc.spi.Row): WorkspaceLinkClickMetrics {
        val link = Link(
            id = LinkId(requireNotNull(row.get("id", UUID::class.java))),
            ownerId = OwnerId(requireNotNull(row.get("owner_id", UUID::class.java))),
            domainId = DomainId(requireNotNull(row.get("domain_id", UUID::class.java))),
            shortCode = ShortCode(requireNotNull(row.get("short_code", String::class.java))),
            destinationUrl = DestinationUrl(requireNotNull(row.get("destination_url", String::class.java))),
            status = LinkStatus.valueOf(requireNotNull(row.get("status", String::class.java))),
            expiresAt = row.get("expires_at", OffsetDateTime::class.java)?.toInstant(),
            createdAt = requireNotNull(row.get("created_at", OffsetDateTime::class.java)).toInstant(),
            updatedAt = requireNotNull(row.get("updated_at", OffsetDateTime::class.java)).toInstant(),
            deletedAt = row.get("deleted_at", OffsetDateTime::class.java)?.toInstant(),
            version = requireNotNull(row.get("version", Long::class.javaObjectType)),
        )
        return WorkspaceLinkClickMetrics(
            link = link,
            recordedRedirects = requireNotNull(row.get("click_count", Long::class.javaObjectType)),
        )
    }
}
