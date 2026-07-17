package com.profiletailors.smp.leadcapture.infrastructure.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.leadcapture.common.CaptureLocale
import com.profiletailors.leadcapture.common.CaptureSource
import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.LeadMetadata
import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistEntryRepository
import com.profiletailors.leadcapture.waitlist.application.ports.WaitlistRepository
import com.profiletailors.leadcapture.waitlist.domain.Waitlist
import com.profiletailors.leadcapture.waitlist.domain.WaitlistConsent
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntry
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryStatus
import com.profiletailors.leadcapture.waitlist.domain.WaitlistId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistKey
import com.profiletailors.leadcapture.waitlist.domain.WaitlistStatus
import io.r2dbc.spi.Readable
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class R2dbcWaitlistRepository(private val databaseClient: DatabaseClient) : WaitlistRepository {

    override fun findByKey(key: WaitlistKey): Waitlist? = kotlinx.coroutines.runBlocking {
        databaseClient.sql(
            """
            SELECT id, key, name, context, status, created_at, updated_at
            FROM waitlists
            WHERE key = :key
            """.trimIndent(),
        )
            .bind("key", key.value)
            .map { row, _ -> row.toWaitlist() }
            .one()
            .awaitSingleOrNull()
    }

    private fun Readable.toWaitlist(): Waitlist = Waitlist(
        id = WaitlistId(requireNotNull(get("id", String::class.java))),
        key = WaitlistKey(requireNotNull(get("key", String::class.java))),
        name = requireNotNull(get("name", String::class.java)),
        context = requireNotNull(get("context", String::class.java)),
        status = WaitlistStatus.valueOf(requireNotNull(get("status", String::class.java))),
        createdAt = requireNotNull(get("created_at", OffsetDateTime::class.java)).toInstant(),
        updatedAt = requireNotNull(get("updated_at", OffsetDateTime::class.java)).toInstant(),
    )
}

@Repository
class R2dbcWaitlistEntryRepository(
    private val databaseClient: DatabaseClient,
    private val objectMapper: ObjectMapper = ObjectMapper(),
) : WaitlistEntryRepository {

    override fun findByNormalizedEmail(waitlistId: WaitlistId, email: NormalizedEmail): WaitlistEntry? =
        kotlinx.coroutines.runBlocking { findByNormalizedEmailAsync(waitlistId, email) }

    override fun save(entry: WaitlistEntry): WaitlistEntry = kotlinx.coroutines.runBlocking {
        insert(entry)
        requireNotNull(findByNormalizedEmailAsync(entry.waitlistId, entry.normalizedEmail))
    }

    override fun saveIfNotExists(entry: WaitlistEntry): WaitlistEntryRepository.SaveResult =
        kotlinx.coroutines.runBlocking {
            val inserted = databaseClient.sql(insertSql(onConflictDoNothing = true))
                .bindEntry(entry)
                .fetch()
                .rowsUpdated()
                .awaitSingle() > 0

            val persisted = requireNotNull(findByNormalizedEmailAsync(entry.waitlistId, entry.normalizedEmail))

            if (inserted) {
                WaitlistEntryRepository.SaveResult.Saved(persisted)
            } else {
                WaitlistEntryRepository.SaveResult.AlreadyExists(persisted)
            }
        }

    private suspend fun findByNormalizedEmailAsync(waitlistId: WaitlistId, email: NormalizedEmail): WaitlistEntry? =
        databaseClient.sql(
            """
            SELECT id, waitlist_id, email_original, normalized_email, source, form_id, locale, metadata,
                   consent_early_access, consent_marketing, consent_version, status, joined_at,
                   invited_at, converted_at, cancelled_at
            FROM waitlist_entries
            WHERE waitlist_id = :waitlistId AND normalized_email = :normalizedEmail
            """.trimIndent(),
        )
            .bind("waitlistId", waitlistId.value)
            .bind("normalizedEmail", email.value)
            .map { row, _ -> row.toWaitlistEntry() }
            .one()
            .awaitSingleOrNull()

    private suspend fun insert(entry: WaitlistEntry) {
        databaseClient.sql(insertSql(onConflictDoNothing = false))
            .bindEntry(entry)
            .then()
            .awaitSingleOrNull()
    }

    private fun insertSql(onConflictDoNothing: Boolean): String {
        val conflictClause = if (onConflictDoNothing) {
            "ON CONFLICT (waitlist_id, normalized_email) DO NOTHING"
        } else {
            ""
        }
        return """
            INSERT INTO waitlist_entries (
                id, waitlist_id, email_original, normalized_email, source, form_id, locale, metadata,
                consent_early_access, consent_marketing, consent_version, status, joined_at,
                invited_at, converted_at, cancelled_at
            ) VALUES (
                :id, :waitlistId, :emailOriginal, :normalizedEmail, :source, :formId, :locale, CAST(:metadata AS JSONB),
                :consentEarlyAccess, :consentMarketing, :consentVersion, :status, :joinedAt,
                :invitedAt, :convertedAt, :cancelledAt
            )
            $conflictClause
        """.trimIndent()
    }

    private fun DatabaseClient.GenericExecuteSpec.bindEntry(entry: WaitlistEntry): DatabaseClient.GenericExecuteSpec =
        bind("id", entry.id.value)
            .bind("waitlistId", entry.waitlistId.value)
            .bind("emailOriginal", entry.email.value)
            .bind("normalizedEmail", entry.normalizedEmail.value)
            .bind("source", entry.source.value)
            .bindNullable("formId", entry.formId, String::class.java)
            .bindNullable("locale", entry.locale?.value, String::class.java)
            .bind("metadata", objectMapper.writeValueAsString(entry.metadata.toStorageMap()))
            .bind("consentEarlyAccess", entry.consent.earlyAccess)
            .bind("consentMarketing", entry.consent.marketing)
            .bind("consentVersion", entry.consent.version)
            .bind("status", entry.status.name)
            .bind("joinedAt", OffsetDateTime.ofInstant(entry.joinedAt, ZoneOffset.UTC))
            .bindNullable(
                "invitedAt",
                entry.invitedAt?.let {
                    OffsetDateTime.ofInstant(it, ZoneOffset.UTC)
                },
                OffsetDateTime::class.java,
            )
            .bindNullable(
                "convertedAt",
                entry.convertedAt?.let {
                    OffsetDateTime.ofInstant(it, ZoneOffset.UTC)
                },
                OffsetDateTime::class.java,
            )
            .bindNullable(
                "cancelledAt",
                entry.cancelledAt?.let {
                    OffsetDateTime.ofInstant(it, ZoneOffset.UTC)
                },
                OffsetDateTime::class.java,
            )

    private fun Readable.toWaitlistEntry(): WaitlistEntry {
        val email = EmailAddress(requireNotNull(get("email_original", String::class.java)))
        return WaitlistEntry(
            id = WaitlistEntryId(requireNotNull(get("id", String::class.java))),
            waitlistId = WaitlistId(requireNotNull(get("waitlist_id", String::class.java))),
            email = email,
            normalizedEmail = NormalizedEmail.from(email),
            source = CaptureSource(requireNotNull(get("source", String::class.java))),
            formId = get("form_id", String::class.java),
            locale = get("locale", String::class.java)?.let(::CaptureLocale),
            metadata = objectMapper.readValue(
                requireNotNull(get("metadata", String::class.java)),
                Map::class.java,
            ).toLeadMetadata(),
            consent = WaitlistConsent(
                earlyAccess = requireNotNull(get("consent_early_access", Boolean::class.javaObjectType)),
                marketing = requireNotNull(get("consent_marketing", Boolean::class.javaObjectType)),
                version = requireNotNull(get("consent_version", String::class.java)),
            ),
            joinedAt = requireNotNull(get("joined_at", OffsetDateTime::class.java)).toInstant(),
            status = WaitlistEntryStatus.valueOf(requireNotNull(get("status", String::class.java))),
            invitedAt = get("invited_at", OffsetDateTime::class.java)?.toInstant(),
            convertedAt = get("converted_at", OffsetDateTime::class.java)?.toInstant(),
            cancelledAt = get("cancelled_at", OffsetDateTime::class.java)?.toInstant(),
        )
    }

    private fun LeadMetadata.toStorageMap(): Map<String, String> = buildMap {
        utmSource?.let { put("utm_source", it) }
        utmMedium?.let { put("utm_medium", it) }
        utmCampaign?.let { put("utm_campaign", it) }
        utmContent?.let { put("utm_content", it) }
        utmTerm?.let { put("utm_term", it) }
        referrer?.let { put("referrer", it) }
        pagePath?.let { put("page_path", it) }
        userAgentFamily?.let { put("user_agent_family", it) }
        consentVersion?.let { put("consent_version", it) }
    }

    private fun Map<*, *>.toLeadMetadata(): LeadMetadata = LeadMetadata(
        utmSource = this["utm_source"] as? String,
        utmMedium = this["utm_medium"] as? String,
        utmCampaign = this["utm_campaign"] as? String,
        utmContent = this["utm_content"] as? String,
        utmTerm = this["utm_term"] as? String,
        referrer = this["referrer"] as? String,
        pagePath = this["page_path"] as? String,
        userAgentFamily = this["user_agent_family"] as? String,
        consentVersion = this["consent_version"] as? String,
    )
}

private fun <T : Any> DatabaseClient.GenericExecuteSpec.bindNullable(
    name: String,
    value: T?,
    type: Class<T>,
): DatabaseClient.GenericExecuteSpec = if (value == null) {
    bindNull(name, type)
} else {
    bind(name, value)
}
