package com.profiletailors.smp.shortlinks.infrastructure.persistence

import com.profiletailors.smp.shortlinks.domain.DestinationUrl
import com.profiletailors.smp.shortlinks.domain.DomainId
import com.profiletailors.smp.shortlinks.domain.Link
import com.profiletailors.smp.shortlinks.domain.LinkFinderRepository
import com.profiletailors.smp.shortlinks.domain.LinkId
import com.profiletailors.smp.shortlinks.domain.LinkStatus
import com.profiletailors.smp.shortlinks.domain.OwnerId
import com.profiletailors.smp.shortlinks.domain.ShortCode
import io.r2dbc.spi.Row
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class R2dbcLinkFinderRepository(private val databaseClient: DatabaseClient) : LinkFinderRepository {

    override suspend fun findByDomainAndShortCode(domainId: DomainId, shortCode: ShortCode): Link? = databaseClient.sql(
        """
            SELECT id, owner_id, domain_id, short_code, destination_url,
                   status, expires_at, created_at, updated_at, deleted_at, version
            FROM links
            WHERE domain_id = :domainId AND short_code = :shortCode
        """.trimIndent(),
    )
        .bind("domainId", domainId.value)
        .bind("shortCode", shortCode.value)
        .map { row, _ -> mapRow(row) }
        .one()
        .awaitSingleOrNull()

    override suspend fun findByOwner(ownerId: OwnerId, limit: Int, afterCreatedAt: Instant?): List<Link> {
        val sql = buildString {
            append(
                """
                SELECT id, owner_id, domain_id, short_code, destination_url,
                       status, expires_at, created_at, updated_at, deleted_at, version
                FROM links
                WHERE owner_id = :ownerId AND status != 'DELETED'
                """.trimIndent(),
            )
            if (afterCreatedAt != null) {
                append(" AND created_at < :afterCreatedAt")
            }
            append(" ORDER BY created_at DESC LIMIT :limit")
        }

        var spec = databaseClient.sql(sql)
            .bind("ownerId", ownerId.value)
            .bind("limit", limit)

        if (afterCreatedAt != null) {
            spec = spec.bind("afterCreatedAt", afterCreatedAt)
        }

        return spec.map { row, _ -> mapRow(row) }
            .all()
            .collectList()
            .awaitSingle()
    }

    private fun mapRow(row: Row): Link = Link(
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
        version = requireNotNull(row.get("version", java.lang.Long::class.java)).toLong(),
    )
}
