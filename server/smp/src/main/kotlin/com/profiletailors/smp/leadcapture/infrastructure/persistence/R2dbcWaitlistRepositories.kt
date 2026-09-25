package com.profiletailors.smp.leadcapture.infrastructure.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.leadcapture.common.CaptureLocale
import com.profiletailors.leadcapture.common.CaptureSource
import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.LeadMetadata
import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.application.WaitlistWithdrawalUrlProvider
import com.profiletailors.leadcapture.waitlist.application.contracts.WaitlistEntryRepository
import com.profiletailors.leadcapture.waitlist.application.contracts.WaitlistEntryRepository.WithdrawalToken
import com.profiletailors.leadcapture.waitlist.application.contracts.WaitlistRepository
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
import java.time.Instant
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
    private val withdrawalUrlProvider: WaitlistWithdrawalUrlProvider,
) : WaitlistEntryRepository {

    override fun findByNormalizedEmail(waitlistId: WaitlistId, email: NormalizedEmail): WaitlistEntry? =
        kotlinx.coroutines.runBlocking { findByNormalizedEmailAsync(waitlistId, email) }

    override fun save(entry: WaitlistEntry): WaitlistEntry = kotlinx.coroutines.runBlocking {
        insert(entry)
        requireNotNull(findByNormalizedEmailAsync(entry.waitlistId, entry.normalizedEmail))
    }

    override suspend fun saveIfNotExists(
        entry: WaitlistEntry,
        withdrawalToken: WithdrawalToken,
    ): WaitlistEntryRepository.SaveResult {
        val inserted = databaseClient.sql(insertSql(onConflictDoNothing = true))
            .bindEntry(entry, withdrawalToken)
            .fetch()
            .rowsUpdated()
            .awaitSingle() > 0
        val persisted = requireNotNull(findByNormalizedEmailAsync(entry.waitlistId, entry.normalizedEmail))
        return if (inserted) {
            WaitlistEntryRepository.SaveResult.Saved(persisted)
        } else {
            WaitlistEntryRepository.SaveResult.AlreadyExists(persisted)
        }
    }

    override suspend fun withdrawByToken(candidate: String, hash: String, now: Instant): WaitlistEntry? {
        val withdrawn = databaseClient.sql(
            """
            WITH withdrawn AS (
                UPDATE waitlist_entries
                SET status = 'CANCELLED', cancelled_at = :now, last_explicit_action_at = :now,
                    email_original = 'withdrawn-' || id || '@invalid.example', normalized_email = 'withdrawn-' || id,
                    consent_early_access = false, consent_marketing = false,
                    metadata = '{}'::jsonb, withdrawal_token_used_at = :now
                WHERE withdrawal_token_candidate = :candidate
                  AND withdrawal_token_hash = :hash
                  AND withdrawal_token_expires_at > :now
                  AND withdrawal_token_used_at IS NULL
                  AND status <> 'CONVERTED'
                RETURNING id, waitlist_id, email_original, normalized_email, source, form_id, locale, metadata,
                          consent_early_access, consent_marketing, consent_version, status, joined_at,
                          invited_at, converted_at, cancelled_at
            ), scrubbed AS (
                UPDATE notifications
                SET recipient = 'withdrawn-' || withdrawn.id || '@invalid.example',
                    status = CASE WHEN notifications.status IN ('PENDING', 'DISPATCHING')
                        THEN 'FAILED' ELSE notifications.status END,
                    failed_at = CASE WHEN notifications.status IN ('PENDING', 'DISPATCHING')
                        THEN :now ELSE notifications.failed_at END,
                    error_message = CASE WHEN notifications.status = 'SENT' THEN NULL
                        ELSE 'Waitlist entry withdrawn' END
                FROM withdrawn
                WHERE notifications.template_id = 'waitlist.welcome'
                  AND notifications.idempotency_key = 'waitlist.welcome:' || withdrawn.id
                RETURNING notifications.id
            )
            SELECT * FROM withdrawn
            """.trimIndent(),
        )
            .bind("candidate", candidate)
            .bind("hash", hash)
            .bind("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
            .map { row, _ -> row.toWaitlistEntry() }
            .one()
            .awaitSingleOrNull()
        if (withdrawn != null) withdrawalUrlProvider.forget(withdrawn.id)
        return withdrawn
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
            .bindEntry(entry, null)
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
                invited_at, converted_at, cancelled_at, withdrawal_token_hash, withdrawal_token_candidate,
                withdrawal_token_expires_at, last_explicit_action_at
            ) VALUES (
                :id, :waitlistId, :emailOriginal, :normalizedEmail, :source, :formId, :locale, CAST(:metadata AS JSONB),
                :consentEarlyAccess, :consentMarketing, :consentVersion, :status, :joinedAt,
                :invitedAt, :convertedAt, :cancelledAt, :withdrawalTokenHash, :withdrawalTokenCandidate,
                :withdrawalTokenExpiresAt, :lastExplicitActionAt
            )
            $conflictClause
        """.trimIndent()
    }

    private fun DatabaseClient.GenericExecuteSpec.bindEntry(
        entry: WaitlistEntry,
        withdrawalToken: WithdrawalToken?,
    ): DatabaseClient.GenericExecuteSpec = bind("id", entry.id.value)
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
        .bindNullable("withdrawalTokenHash", withdrawalToken?.hash, String::class.java)
        .bindNullable("withdrawalTokenCandidate", withdrawalToken?.candidate, String::class.java)
        .bindNullable(
            "withdrawalTokenExpiresAt",
            withdrawalToken?.expiresAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) },
            OffsetDateTime::class.java,
        )
        .bind("lastExplicitActionAt", OffsetDateTime.ofInstant(entry.joinedAt, ZoneOffset.UTC))

    private fun Readable.toWaitlistEntry(): WaitlistEntry {
        val persistedEmail = requireNotNull(get("email_original", String::class.java))
        val persistedNormalizedEmail = requireNotNull(get("normalized_email", String::class.java))
        val withdrawn = persistedEmail.startsWith("withdrawn-") && persistedEmail.endsWith("@invalid.example")
        val email = EmailAddress(persistedEmail)
        val normalizedEmail = NormalizedEmail.fromPersisted(persistedNormalizedEmail)
        val withdrawnId = persistedEmail.substringAfter("withdrawn-").substringBefore("@invalid.example")
        check(withdrawn == (persistedNormalizedEmail == "withdrawn-$withdrawnId"))
        if (!withdrawn) {
            check(normalizedEmail == NormalizedEmail.from(email)) {
                "Persisted normalized email does not match original email"
            }
        }
        return WaitlistEntry(
            id = WaitlistEntryId(requireNotNull(get("id", String::class.java))),
            waitlistId = WaitlistId(requireNotNull(get("waitlist_id", String::class.java))),
            email = email,
            normalizedEmail = normalizedEmail,
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
