package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.smp.platformadmin.application.contracts.AdminWaitlistQuery
import com.profiletailors.smp.platformadmin.application.model.AdminInvitationSummary
import com.profiletailors.smp.platformadmin.application.model.AdminWaitlistEntryDetail
import com.profiletailors.smp.platformadmin.application.model.AdminWaitlistEntrySummary
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.application.query.ListAdminWaitlistEntriesQuery
import io.r2dbc.spi.Readable
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class R2dbcAdminWaitlistQuery(private val databaseClient: DatabaseClient) : AdminWaitlistQuery {

    @Suppress("LongMethod")
    override suspend fun list(query: ListAdminWaitlistEntriesQuery): PagedResult<AdminWaitlistEntrySummary> {
        validatePagination(query.page, query.size)

        val conditions = mutableListOf<String>()
        val params = mutableMapOf<String, Any?>()

        query.status?.let {
            conditions += "we.status = :status"
            params["status"] = it
        }
        query.waitlistId?.let {
            conditions += "we.waitlist_id = :waitlistId"
            params["waitlistId"] = it
        }
        query.waitlistKey?.let {
            conditions += "w.key = :waitlistKey"
            params["waitlistKey"] = it
        }
        query.email?.let {
            conditions += "we.normalized_email = :email"
            params["email"] = it.trim().lowercase()
        }
        query.joinedFrom?.let {
            conditions += "we.joined_at >= :joinedFrom"
            params["joinedFrom"] = OffsetDateTime.ofInstant(it, ZoneOffset.UTC)
        }
        query.joinedTo?.let {
            conditions += "we.joined_at <= :joinedTo"
            params["joinedTo"] = OffsetDateTime.ofInstant(it, ZoneOffset.UTC)
        }
        query.invitedFrom?.let {
            conditions += "we.invited_at >= :invitedFrom"
            params["invitedFrom"] = OffsetDateTime.ofInstant(it, ZoneOffset.UTC)
        }
        query.invitedTo?.let {
            conditions += "we.invited_at <= :invitedTo"
            params["invitedTo"] = OffsetDateTime.ofInstant(it, ZoneOffset.UTC)
        }

        val where = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
        val orderCol = ALLOWED_SORT_FIELDS[query.sortField] ?: "we.joined_at"
        val orderDir = if (query.sortDirection.uppercase() == "ASC") "ASC" else "DESC"
        val offset = query.page.toLong() * query.size

        val countSql = "SELECT COUNT(*) FROM waitlist_entries we JOIN waitlists w ON w.id = we.waitlist_id $where"
        val dataSql = """
            SELECT we.id, we.waitlist_id, w.key AS waitlist_key, we.email_original, we.normalized_email,
                   we.status, we.joined_at, we.invited_at, we.converted_at, we.cancelled_at, we.locale, we.source
            FROM waitlist_entries we
            JOIN waitlists w ON w.id = we.waitlist_id
            $where
            ORDER BY $orderCol $orderDir
            LIMIT :size OFFSET :offset
        """.trimIndent()

        val countSpec = params.entries.fold(databaseClient.sql(countSql)) { spec, (k, v) ->
            if (v != null) spec.bind(k, v) else spec
        }
        val dataSpec = params.entries.fold(
            databaseClient.sql(dataSql).bind("size", query.size).bind("offset", offset),
        ) { spec, (k, v) ->
            if (v != null) spec.bind(k, v) else spec
        }

        val total = countSpec.map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one().awaitSingle()
        val items = dataSpec.map { row, _ -> row.toSummary() }.all().collectList().awaitSingle()

        return PagedResult.of(items, query.page, query.size, total)
    }

    override suspend fun findById(entryId: String): AdminWaitlistEntryDetail? {
        val entry = databaseClient.sql(SELECT_ENTRY_DETAIL)
            .bind("id", entryId)
            .map { row, _ -> row.toEntrySnapshot() }
            .one()
            .awaitSingleOrNull() ?: return null

        val invitations = databaseClient.sql(SELECT_INVITATIONS_FOR_ENTRY)
            .bind("waitlistEntryId", entryId)
            .map { row, _ -> row.toInvitationSummary() }
            .all()
            .collectList()
            .awaitSingle()

        return AdminWaitlistEntryDetail(
            id = entry.id,
            waitlistId = entry.waitlistId,
            waitlistKey = entry.waitlistKey,
            email = entry.email,
            normalizedEmail = entry.normalizedEmail,
            status = entry.status,
            joinedAt = entry.joinedAt,
            invitedAt = entry.invitedAt,
            convertedAt = entry.convertedAt,
            cancelledAt = entry.cancelledAt,
            preferredLocale = entry.preferredLocale,
            earlyAccessConsent = entry.earlyAccessConsent,
            marketingConsent = entry.marketingConsent,
            consentVersion = entry.consentVersion,
            source = entry.source,
            metadataSummary = emptyMap(),
            invitationHistory = invitations,
            version = 0L,
        )
    }

    override suspend fun countByStatus(): Map<String, Long> =
        databaseClient.sql("SELECT status, COUNT(*) AS cnt FROM waitlist_entries GROUP BY status")
            .map { row, _ ->
                requireNotNull(row.get("status", String::class.java)) to
                    requireNotNull(row.get("cnt", Long::class.java))
            }
            .all()
            .collectList()
            .awaitSingle()
            .toMap()

    private fun Readable.toEntrySnapshot() = AdminWaitlistEntrySnapshot(
        id = requireNotNull(get("id", String::class.java)),
        waitlistId = requireNotNull(get("waitlist_id", String::class.java)),
        waitlistKey = requireNotNull(get("waitlist_key", String::class.java)),
        email = requireNotNull(get("email_original", String::class.java)),
        normalizedEmail = requireNotNull(get("normalized_email", String::class.java)),
        status = requireNotNull(get("status", String::class.java)),
        joinedAt = requireNotNull(get("joined_at", OffsetDateTime::class.java)).toInstant(),
        invitedAt = get("invited_at", OffsetDateTime::class.java)?.toInstant(),
        convertedAt = get("converted_at", OffsetDateTime::class.java)?.toInstant(),
        cancelledAt = get("cancelled_at", OffsetDateTime::class.java)?.toInstant(),
        preferredLocale = get("locale", String::class.java),
        earlyAccessConsent = requireNotNull(get("consent_early_access", Boolean::class.java)),
        marketingConsent = requireNotNull(get("consent_marketing", Boolean::class.java)),
        consentVersion = get("consent_version", String::class.java),
        source = requireNotNull(get("source", String::class.java)),
    )

    private data class AdminWaitlistEntrySnapshot(
        val id: String,
        val waitlistId: String,
        val waitlistKey: String,
        val email: String,
        val normalizedEmail: String,
        val status: String,
        val joinedAt: Instant,
        val invitedAt: Instant?,
        val convertedAt: Instant?,
        val cancelledAt: Instant?,
        val preferredLocale: String?,
        val earlyAccessConsent: Boolean,
        val marketingConsent: Boolean,
        val consentVersion: String?,
        val source: String,
    )

    private fun Readable.toSummary() = AdminWaitlistEntrySummary(
        id = requireNotNull(get("id", String::class.java)),
        waitlistId = requireNotNull(get("waitlist_id", String::class.java)),
        waitlistKey = requireNotNull(get("waitlist_key", String::class.java)),
        email = requireNotNull(get("email_original", String::class.java)),
        normalizedEmail = requireNotNull(get("normalized_email", String::class.java)),
        status = requireNotNull(get("status", String::class.java)),
        joinedAt = requireNotNull(get("joined_at", OffsetDateTime::class.java)).toInstant(),
        invitedAt = get("invited_at", OffsetDateTime::class.java)?.toInstant(),
        convertedAt = get("converted_at", OffsetDateTime::class.java)?.toInstant(),
        cancelledAt = get("cancelled_at", OffsetDateTime::class.java)?.toInstant(),
        preferredLocale = get("locale", String::class.java),
        source = requireNotNull(get("source", String::class.java)),
    )

    /**
     * Maps a database row to an invitation summary.
     *
     * @return The invitation summary represented by the row.
     */
    private fun Readable.toInvitationSummary() = AdminInvitationSummary(
        id = requireNotNull(get("id", UUID::class.java)),
        waitlistEntryId = requireNotNull(get("waitlist_entry_id", String::class.java)),
        status = requireNotNull(get("status", String::class.java)),
        issuedAt = requireNotNull(get("issued_at", OffsetDateTime::class.java)).toInstant(),
        expiresAt = requireNotNull(get("expires_at", OffsetDateTime::class.java)).toInstant(),
        acceptedAt = get("accepted_at", OffsetDateTime::class.java)?.toInstant(),
        revokedAt = get("revoked_at", OffsetDateTime::class.java)?.toInstant(),
        revokedBy = get("revoked_by", UUID::class.java),
        createdBy = requireNotNull(get("created_by", UUID::class.java)),
        deliveryStatus = requireNotNull(get("delivery_status", String::class.java)),
        deliveryAttemptCount = requireNotNull(get("delivery_attempt_count", Int::class.javaObjectType)),
        version = requireNotNull(get("version", Long::class.java)),
    )

    companion object {
        private val ALLOWED_SORT_FIELDS = mapOf(
            "joinedAt" to "we.joined_at",
            "invitedAt" to "we.invited_at",
            "email" to "we.normalized_email",
            "status" to "we.status",
        )
        private const val SELECT_ENTRY_DETAIL = """
            SELECT we.id, we.waitlist_id, w.key AS waitlist_key, we.email_original, we.normalized_email,
                   we.status, we.joined_at, we.invited_at, we.converted_at, we.cancelled_at, we.locale, we.source,
                   we.consent_early_access, we.consent_marketing, we.consent_version
            FROM waitlist_entries we JOIN waitlists w ON w.id = we.waitlist_id
            WHERE we.id = :id
        """
        private const val SELECT_INVITATIONS_FOR_ENTRY = """
            SELECT id, waitlist_entry_id, status, issued_at, expires_at, accepted_at, revoked_at, revoked_by,
                   created_by, delivery_status, delivery_attempt_count, version
            FROM waitlist_invitations WHERE waitlist_entry_id = :waitlistEntryId ORDER BY issued_at DESC
        """
    }
}
