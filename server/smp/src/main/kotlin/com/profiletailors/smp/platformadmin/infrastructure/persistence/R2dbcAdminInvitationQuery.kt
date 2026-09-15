package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.smp.platformadmin.application.contracts.AdminInvitationQuery
import com.profiletailors.smp.platformadmin.application.contracts.WaitlistInvitationRepository
import com.profiletailors.smp.platformadmin.application.model.AdminDirectInvitationSummary
import com.profiletailors.smp.platformadmin.application.model.AdminInvitationSummary
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.application.query.ListAdminDirectInvitationsQuery
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationId
import io.r2dbc.spi.Readable
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class R2dbcAdminInvitationQuery(
    private val invitationRepository: WaitlistInvitationRepository,
    private val databaseClient: DatabaseClient,
) : AdminInvitationQuery {

    override suspend fun findById(invitationId: UUID): AdminInvitationSummary? =
        invitationRepository.findById(WaitlistInvitationId(invitationId))?.toSummary()

    override suspend fun list(query: ListAdminDirectInvitationsQuery): PagedResult<AdminDirectInvitationSummary> {
        validatePagination(query.page, query.size)

        val effectiveStatus =
            "CASE WHEN status = 'ACTIVE' AND expires_at <= :now THEN 'EXPIRED' ELSE status END"
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val conditions = mutableListOf("source = 'DIRECT'")
        val params = mutableMapOf<String, Any>()
        query.status?.let {
            conditions += "$effectiveStatus = :status"
            params["status"] = it
            params["now"] = now
        }
        query.email?.let {
            conditions += "invited_email_normalized LIKE '%' || :email || '%'"
            params["email"] = it.trim().lowercase()
        }

        val where = "WHERE ${conditions.joinToString(" AND ")}"
        val offset = query.page.toLong() * query.size
        val countSql = "SELECT COUNT(*) FROM invitations $where"
        val dataSql = """
            SELECT id, invited_email_normalized, target, workspace_id, $effectiveStatus AS status, expires_at, version
            FROM invitations
            $where
            ORDER BY created_at DESC, id DESC
            LIMIT :size OFFSET :offset
        """.trimIndent()

        var countSpec = params.entries.fold(databaseClient.sql(countSql)) { spec, (key, value) ->
            spec.bind(key, value)
        }
        if (query.status != null) {
            countSpec = countSpec.bind("now", now)
        }
        val dataSpec = params.entries.fold(
            databaseClient.sql(dataSql).bind("size", query.size).bind("offset", offset).bind("now", now),
        ) { spec, (key, value) ->
            spec.bind(key, value)
        }

        val total = countSpec.map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one().awaitSingle()
        val items = dataSpec.map { row, _ -> row.toDirectSummary() }.all().collectList().awaitSingle()

        return PagedResult.of(items, query.page, query.size, total)
    }

    private fun com.profiletailors.smp.platformadmin.domain.WaitlistInvitation.toSummary() = AdminInvitationSummary(
        id = id.value,
        waitlistEntryId = waitlistEntryId,
        status = status.name,
        issuedAt = issuedAt,
        expiresAt = expiresAt,
        acceptedAt = acceptedAt,
        revokedAt = revokedAt,
        revokedBy = revokedBy,
        createdBy = createdBy,
        deliveryStatus = deliveryStatus.name,
        deliveryAttemptCount = deliveryAttemptCount,
        version = version,
    )

    private fun Readable.toDirectSummary() = AdminDirectInvitationSummary(
        invitationId = requireNotNull(get("id", UUID::class.java)),
        email = requireNotNull(get("invited_email_normalized", String::class.java)),
        target = requireNotNull(get("target", String::class.java)),
        workspaceId = get("workspace_id", String::class.java),
        status = requireNotNull(get("status", String::class.java)),
        expiresAt = requireNotNull(get("expires_at", OffsetDateTime::class.java)).toInstant(),
        version = requireNotNull(get("version", Long::class.java)),
    )
}
