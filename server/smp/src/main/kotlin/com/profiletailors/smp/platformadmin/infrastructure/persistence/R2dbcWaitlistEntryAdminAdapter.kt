package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.leadcapture.common.CaptureLocale
import com.profiletailors.leadcapture.common.CaptureSource
import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.LeadMetadata
import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.domain.WaitlistConsent
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntry
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryStatus
import com.profiletailors.leadcapture.waitlist.domain.WaitlistId
import com.profiletailors.smp.platformadmin.application.ports.WaitlistEntryAdminPort
import io.r2dbc.spi.Readable
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset
/**
 * Delegates waitlist entry reads and status updates for admin use cases.
 * Reuses the R2dbcWaitlistEntryRepository mapping logic via a dedicated query.
 */
@Repository
class R2dbcWaitlistEntryAdminAdapter(private val databaseClient: DatabaseClient) : WaitlistEntryAdminPort {

    override suspend fun findById(id: String): WaitlistEntry? = databaseClient.sql(SELECT_BY_ID)
        .bind("id", id)
        .map { row, _ -> row.toWaitlistEntry() }
        .one()
        .awaitSingleOrNull()

    override suspend fun save(entry: WaitlistEntry): WaitlistEntry {
        val invitedAt = entry.invitedAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) }
        val convertedAt = entry.convertedAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) }
        val cancelledAt = entry.cancelledAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) }
        databaseClient.sql(UPDATE_STATUS)
            .bind("status", entry.status.name)
            .bindNullable("invitedAt", invitedAt, OffsetDateTime::class.java)
            .bindNullable("convertedAt", convertedAt, OffsetDateTime::class.java)
            .bindNullable("cancelledAt", cancelledAt, OffsetDateTime::class.java)
            .bind("id", entry.id.value)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return requireNotNull(findById(entry.id.value))
    }

    private fun Readable.toWaitlistEntry(): WaitlistEntry {
        val id = WaitlistEntryId(
            requireNotNull(get("id", String::class.java)),
        )
        val waitlistId = WaitlistId(
            requireNotNull(get("waitlist_id", String::class.java)),
        )
        return WaitlistEntry(
            id = id,
            waitlistId = waitlistId,
            email = EmailAddress(
                requireNotNull(get("email_original", String::class.java)),
            ),
            normalizedEmail = NormalizedEmail.fromPersisted(
                requireNotNull(get("normalized_email", String::class.java)),
            ),
            source = CaptureSource(
                requireNotNull(get("source", String::class.java)),
            ),
            formId = get("form_id", String::class.java),
            locale = get("locale", String::class.java)
                ?.let { CaptureLocale(it) },
            metadata = LeadMetadata(),
            consent = WaitlistConsent(
                earlyAccess = requireNotNull(get("consent_early_access", Boolean::class.java)),
                marketing = requireNotNull(get("consent_marketing", Boolean::class.java)),
                version = requireNotNull(get("consent_version", String::class.java)),
            ),
            joinedAt = requireNotNull(get("joined_at", OffsetDateTime::class.java)).toInstant(),
            status = WaitlistEntryStatus.valueOf(
                requireNotNull(get("status", String::class.java)),
            ),
            invitedAt = get("invited_at", OffsetDateTime::class.java)?.toInstant(),
            convertedAt = get("converted_at", OffsetDateTime::class.java)?.toInstant(),
            cancelledAt = get("cancelled_at", OffsetDateTime::class.java)?.toInstant(),
        )
    }

    companion object {
        private const val SELECT_BY_ID = """
            SELECT id, waitlist_id, email_original, normalized_email, source, form_id, locale,
                   consent_early_access, consent_marketing, consent_version, status,
                   joined_at, invited_at, converted_at, cancelled_at
            FROM waitlist_entries WHERE id = :id
        """
        private const val UPDATE_STATUS = """
            UPDATE waitlist_entries
            SET status = :status, invited_at = :invitedAt, converted_at = :convertedAt, cancelled_at = :cancelledAt
            WHERE id = :id
        """
    }
}

private fun <T> DatabaseClient.GenericExecuteSpec.bindNullable(
    name: String,
    value: T?,
    type: Class<T>,
): DatabaseClient.GenericExecuteSpec = if (value != null) bind(name, value) else bindNull(name, type)
