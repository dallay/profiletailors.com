package com.profiletailors.smp.shortlinks.infrastructure.persistence

import com.profiletailors.smp.shortlinks.domain.DestinationUrl
import com.profiletailors.smp.shortlinks.domain.DomainId
import com.profiletailors.smp.shortlinks.domain.Link
import com.profiletailors.smp.shortlinks.domain.LinkId
import com.profiletailors.smp.shortlinks.domain.LinkRepository
import com.profiletailors.smp.shortlinks.domain.LinkStatus
import com.profiletailors.smp.shortlinks.domain.OwnerId
import com.profiletailors.smp.shortlinks.domain.ShortCode
import com.profiletailors.smp.shortlinks.domain.ShortCodeCollisionException
import io.r2dbc.spi.Row
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.dao.DuplicateKeyException
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

private const val SHORT_CODE_CONSTRAINT = "uq_links_domain_short_code"

@Repository
class R2dbcLinkRepository(private val databaseClient: DatabaseClient) : LinkRepository {

    override suspend fun save(link: Link): Link {
        try {
            return insert(link)
        } catch (exception: DuplicateKeyException) {
            if (exception.message.orEmpty().contains(SHORT_CODE_CONSTRAINT)) {
                throw ShortCodeCollisionException(exception)
            }
            throw exception
        }
    }

    private suspend fun insert(link: Link): Link {
        databaseClient.sql(
            """
            INSERT INTO links (
              id, owner_id, domain_id, short_code, destination_url,
              status, expires_at, created_at, updated_at, deleted_at, version
            ) VALUES (
              :id, :ownerId, :domainId, :shortCode, :destinationUrl,
              :status, :expiresAt, :createdAt, :updatedAt, :deletedAt, :version
            )
            """.trimIndent(),
        )
            .bind("id", link.id.value)
            .bind("ownerId", link.ownerId.value)
            .bind("domainId", link.domainId.value)
            .bind("shortCode", link.shortCode.value)
            .bind("destinationUrl", link.destinationUrl.value)
            .bind("status", link.status.name)
            .bindNullable("expiresAt", link.expiresAt, Instant::class.java)
            .bind("createdAt", link.createdAt)
            .bind("updatedAt", link.updatedAt)
            .bindNullable("deletedAt", link.deletedAt, Instant::class.java)
            .bind("version", link.version)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return link
    }

    override suspend fun findById(id: LinkId, ownerId: OwnerId): Link? = databaseClient.sql(
        """
        SELECT id, owner_id, domain_id, short_code, destination_url,
               status, expires_at, created_at, updated_at, deleted_at, version
        FROM links
        WHERE id = :id AND owner_id = :ownerId
        """.trimIndent(),
    )
        .bind("id", id.value)
        .bind("ownerId", ownerId.value)
        .map { row, _ -> mapRow(row) }
        .one()
        .awaitSingleOrNull()

    override suspend fun updateWithVersion(link: Link, expectedVersion: Long, ownerId: OwnerId): Boolean {
        val rowsUpdated = databaseClient.sql(
            """
            UPDATE links
            SET destination_url = :destinationUrl,
                status = :status,
                expires_at = :expiresAt,
                updated_at = :updatedAt,
                deleted_at = :deletedAt,
                version = :version
            WHERE id = :id AND owner_id = :ownerId AND version = :expectedVersion
            """.trimIndent(),
        )
            .bind("id", link.id.value)
            .bind("ownerId", ownerId.value)
            .bind("destinationUrl", link.destinationUrl.value)
            .bind("status", link.status.name)
            .bindNullable("expiresAt", link.expiresAt, Instant::class.java)
            .bind("updatedAt", link.updatedAt)
            .bindNullable("deletedAt", link.deletedAt, Instant::class.java)
            .bind("version", link.version)
            .bind("expectedVersion", expectedVersion)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return rowsUpdated > 0
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

private fun DatabaseClient.GenericExecuteSpec.bindNullable(
    name: String,
    value: Any?,
    type: Class<*>,
): DatabaseClient.GenericExecuteSpec = if (value == null) bindNull(name, type) else bind(name, value)
