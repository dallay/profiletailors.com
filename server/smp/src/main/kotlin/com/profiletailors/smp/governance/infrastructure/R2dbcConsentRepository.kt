package com.profiletailors.smp.governance.infrastructure

import com.profiletailors.smp.governance.domain.ConsentRecord
import com.profiletailors.smp.governance.domain.ConsentRecordId
import com.profiletailors.smp.governance.domain.ConsentRepository
import com.profiletailors.smp.governance.domain.ConsentStatus
import com.profiletailors.smp.governance.domain.ConsentType
import com.profiletailors.smp.governance.domain.SubjectKind
import com.profiletailors.smp.governance.domain.SubjectReference
import io.r2dbc.spi.Row
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import java.time.Instant
import java.time.OffsetDateTime

@Suppress("StringLiteralDuplication")
@Repository
class R2dbcConsentRepository(
    private val databaseClient: DatabaseClient,
    private val transaction: TransactionalOperator,
) : ConsentRepository {

    /**
     * Saves a consent record, updating an existing record with the same identifier.
     *
     * @param record The consent record to save.
     * @return The saved consent record.
     */
    override suspend fun save(record: ConsentRecord): ConsentRecord {
        var spec = bindRecord(databaseClient.sql(UPSERT_CONSENT), record)
        spec = bindNullableInstant(spec, "withdrawnAt", record.withdrawnAt)
        spec = bindNullable(spec, "withdrawalReason", record.withdrawalReason)
        spec.fetch().rowsUpdated().awaitSingle()
        return record
    }

    /**
     * Records an active consent and retrieves the current active record for the same identity,
     * purpose, and policy version.
     *
     * @param record The consent record to record.
     * @return A pair containing whether the record was newly inserted and the active consent record.
     * @throws IllegalStateException If the active consent record cannot be found after insertion.
     */
    override suspend fun recordActiveReturning(record: ConsentRecord): Pair<Boolean, ConsentRecord> = requireNotNull(
        transaction.executeAndAwait {
            bindRecord(databaseClient.sql(INSERT_ACTIVE), record)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
            val inserted = findActive(
                record.workspaceId,
                record.subjectReference,
                record.purpose,
                record.policyVersion,
            )
            if (inserted == null) {
                val message = "Consent record not found after insert for " +
                    "workspaceId=${record.workspaceId}, purpose=${record.purpose}, " +
                    "policyVersion=${record.policyVersion}"
                error(message)
            }
            val isNewlyInserted = inserted.id == record.id
            if (isNewlyInserted) appendEvent(inserted)
            isNewlyInserted to inserted
        },
    )

    /**
     * Withdraws the active consent matching the specified identity, purpose, and policy version.
     *
     * @param workspaceId The workspace containing the consent.
     * @param subjectReference The subject identity associated with the consent.
     * @param purpose The purpose for which consent was given.
     * @param policyVersion The policy version governing the consent.
     * @param withdrawnAt The timestamp at which the consent was withdrawn.
     * @param reason The optional reason for withdrawal.
     * @return The withdrawn consent record, or `null` if no matching active consent exists.
     */
    override suspend fun withdrawActiveReturning(
        workspaceId: String,
        subjectReference: SubjectReference,
        purpose: String,
        policyVersion: String,
        withdrawnAt: Instant,
        reason: String?,
    ): ConsentRecord? = transaction.executeAndAwait {
        var spec = databaseClient.sql(WITHDRAW_ACTIVE)
            .bind("workspaceId", workspaceId)
            .bind("subjectKind", subjectReference.kind.name)
            .bind("subjectValue", subjectReference.value)
            .bind("purpose", purpose)
            .bind("policyVersion", policyVersion)
            .bind("withdrawnAt", withdrawnAt)
        spec = bindNullable(spec, "reason", reason)
        spec.map { row, _ -> mapConsent(row) }.first().awaitSingleOrNull()?.also { appendEvent(it) }
    }

    /**
     * Finds a consent record by its identifier.
     *
     * @param id The identifier of the consent record.
     * @return The matching consent record, or `null` if no record exists.
     */
    override suspend fun findById(id: ConsentRecordId): ConsentRecord? = databaseClient.sql(SELECT_BY_ID)
        .bind("id", id.value)
        .map { row, _ -> mapConsent(row) }
        .first()
        .awaitSingleOrNull()

    override suspend fun findActive(
        workspaceId: String,
        subjectReference: SubjectReference,
        purpose: String,
        policyVersion: String,
    ): ConsentRecord? = databaseClient.sql(SELECT_ACTIVE)
        .bind("workspaceId", workspaceId)
        .bind("subjectKind", subjectReference.kind.name)
        .bind("subjectValue", subjectReference.value)
        .bind("purpose", purpose)
        .bind("policyVersion", policyVersion)
        .map { row, _ -> mapConsent(row) }
        .first()
        .awaitSingleOrNull()

    override fun findActiveByWorkspace(
        workspaceId: String,
        subjectKind: SubjectKind?,
        purpose: String?,
    ): Flow<ConsentRecord> {
        val conditions = mutableListOf("workspace_id = :workspaceId", "status = 'ACTIVE'")
        if (subjectKind != null) conditions.add("subject_kind = :subjectKind")
        if (purpose != null) conditions.add("purpose = :purpose")

        var spec = databaseClient.sql(
            """
            SELECT * FROM consent_records
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY given_at DESC, id DESC
            """.trimIndent(),
        ).bind("workspaceId", workspaceId)

        if (subjectKind != null) spec = spec.bind("subjectKind", subjectKind.name)
        if (purpose != null) spec = spec.bind("purpose", purpose)

        return spec.map { row, _ -> mapConsent(row) }.all().asFlow()
    }

    override fun findHistoricalByIdentity(
        workspaceId: String,
        subjectReference: SubjectReference,
        purpose: String,
    ): Flow<ConsentRecord> = databaseClient.sql(SELECT_HISTORY)
        .bind("workspaceId", workspaceId)
        .bind("subjectKind", subjectReference.kind.name)
        .bind("subjectValue", subjectReference.value)
        .bind("purpose", purpose)
        .map { row, _ -> mapConsent(row) }
        .all()
        .asFlow()

    /**
     * Determines whether an active consent exists for the specified identity and purpose.
     *
     * @param workspaceId The workspace identifier.
     * @param subjectReference The subject identity.
     * @param purpose The consent purpose.
     * @param policyVersion The policy version.
     * @return `true` if an active consent exists, `false` otherwise.
     */
    override suspend fun existsActive(
        workspaceId: String,
        subjectReference: SubjectReference,
        purpose: String,
        policyVersion: String,
    ): Boolean = findActive(workspaceId, subjectReference, purpose, policyVersion) != null

    /**
     * Binds the consent record fields required by an SQL statement.
     *
     * @param spec The SQL execution specification to populate.
     * @param record The consent record whose fields are bound.
     * @return The execution specification with the record fields bound.
     */
    private fun bindRecord(
        spec: DatabaseClient.GenericExecuteSpec,
        record: ConsentRecord,
    ): DatabaseClient.GenericExecuteSpec = spec
        .bind("id", record.id.value)
        .bind("workspaceId", record.workspaceId)
        .bind("subjectKind", record.subjectReference.kind.name)
        .bind("subjectValue", record.subjectReference.value)
        .bind("consentType", record.consentType.name)
        .bind("purpose", record.purpose)
        .bind("policyVersion", record.policyVersion)
        .bind("source", record.source)
        .bind("locale", record.locale)
        .bind("status", record.status.name)
        .bind("givenAt", record.givenAt)

    /**
     * Appends an event for the specified consent record.
     *
     * @param record The consent record to record as an event.
     */
    private suspend fun appendEvent(record: ConsentRecord) {
        var spec = bindRecord(databaseClient.sql(INSERT_EVENT), record)
            .bind("eventId", "ce-${java.util.UUID.randomUUID()}")
            .bind("eventAt", record.withdrawnAt ?: record.givenAt)
        spec = bindNullableInstant(spec, "withdrawnAt", record.withdrawnAt)
        spec = bindNullable(spec, "withdrawalReason", record.withdrawalReason)
        spec.fetch().rowsUpdated().awaitSingle()
    }

    /**
     * Maps a database row to a consent record.
     *
     * @param row The database row containing consent record fields.
     * @return The consent record represented by the row.
     */
    private fun mapConsent(row: Row): ConsentRecord = ConsentRecord(
        id = ConsentRecordId(requireNotNull(row.get("id", String::class.java))),
        workspaceId = requireNotNull(row.get("workspace_id", String::class.java)),
        subjectReference = SubjectReference(
            value = requireNotNull(row.get("subject_value", String::class.java)),
            kind = SubjectKind.valueOf(requireNotNull(row.get("subject_kind", String::class.java))),
        ),
        consentType = ConsentType.valueOf(requireNotNull(row.get("consent_type", String::class.java))),
        purpose = requireNotNull(row.get("purpose", String::class.java)),
        policyVersion = requireNotNull(row.get("policy_version", String::class.java)),
        source = requireNotNull(row.get("source", String::class.java)),
        locale = requireNotNull(row.get("locale", String::class.java)),
        status = ConsentStatus.valueOf(requireNotNull(row.get("status", String::class.java))),
        givenAt = requireNotNull(row.get("given_at", OffsetDateTime::class.java)).toInstant(),
        withdrawnAt = row.get("withdrawn_at", OffsetDateTime::class.java)?.toInstant(),
        withdrawalReason = row.get("withdrawal_reason", String::class.java),
        createdAt = requireNotNull(row.get("created_at", OffsetDateTime::class.java)).toInstant(),
        version = requireNotNull(row.get("version", Long::class.java)),
    )

    private fun bindNullable(
        spec: DatabaseClient.GenericExecuteSpec,
        name: String,
        value: String?,
    ): DatabaseClient.GenericExecuteSpec =
        if (value != null) spec.bind(name, value) else spec.bindNull(name, String::class.java)

    /**
     * Binds an instant value to a named parameter, or binds SQL `NULL` when the value is absent.
     *
     * @param spec The database statement to update.
     * @param name The name of the parameter.
     * @param value The instant value to bind, or `null`.
     * @return The updated database statement.
     */
    private fun bindNullableInstant(
        spec: DatabaseClient.GenericExecuteSpec,
        name: String,
        value: Instant?,
    ): DatabaseClient.GenericExecuteSpec =
        if (value != null) spec.bind(name, value) else spec.bindNull(name, Instant::class.java)

    companion object {
        private const val INSERT_ACTIVE = """
            INSERT INTO consent_records (
                id, workspace_id, subject_kind, subject_value, consent_type, purpose,
                policy_version, source, locale, status, given_at, created_at
            ) VALUES (
                :id, :workspaceId, :subjectKind, :subjectValue, :consentType, :purpose,
                :policyVersion, :source, :locale, :status, :givenAt,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (workspace_id, subject_kind, subject_value, purpose, policy_version)
                WHERE status = 'ACTIVE' DO NOTHING
        """

        private const val WITHDRAW_ACTIVE = """
            UPDATE consent_records SET status = 'WITHDRAWN', withdrawn_at = :withdrawnAt,
                withdrawal_reason = :reason, version = version + 1
            WHERE workspace_id = :workspaceId AND subject_kind = :subjectKind
              AND subject_value = :subjectValue AND purpose = :purpose
              AND policy_version = :policyVersion AND status = 'ACTIVE'
            RETURNING *
        """

        private const val INSERT_EVENT = """
            INSERT INTO consent_record_events (
                id, consent_id, workspace_id, subject_kind, subject_value, consent_type,
                purpose, policy_version, source, locale, status, given_at, withdrawn_at,
                withdrawal_reason, event_at
            ) VALUES (
                :eventId, :id, :workspaceId, :subjectKind, :subjectValue, :consentType,
                :purpose, :policyVersion, :source, :locale, :status, :givenAt, :withdrawnAt,
                :withdrawalReason, :eventAt
            )
        """

        private const val SELECT_BY_ID = "SELECT * FROM consent_records WHERE id = :id"

        private const val SELECT_ACTIVE = """
            SELECT * FROM consent_records
            WHERE workspace_id = :workspaceId
              AND subject_kind = :subjectKind
              AND subject_value = :subjectValue
              AND purpose = :purpose
              AND policy_version = :policyVersion
              AND status = 'ACTIVE'
            ORDER BY given_at DESC
            LIMIT 1
        """

        private const val SELECT_HISTORY = """
            SELECT * FROM consent_records
            WHERE workspace_id = :workspaceId
              AND subject_kind = :subjectKind
              AND subject_value = :subjectValue
              AND purpose = :purpose
            ORDER BY given_at ASC, id ASC
        """

        private const val UPSERT_CONSENT = """
            INSERT INTO consent_records (
                id, workspace_id, subject_kind, subject_value, consent_type,
                purpose, policy_version, source, locale, status, given_at,
                withdrawn_at, withdrawal_reason, created_at
            ) VALUES (
                :id, :workspaceId, :subjectKind, :subjectValue, :consentType,
                :purpose, :policyVersion, :source, :locale, :status, :givenAt,
                :withdrawnAt, :withdrawalReason, CURRENT_TIMESTAMP
            )
            ON CONFLICT (id) DO UPDATE SET
                status = EXCLUDED.status,
                withdrawn_at = EXCLUDED.withdrawn_at,
                withdrawal_reason = EXCLUDED.withdrawal_reason,
                version = consent_records.version + 1
        """
    }
}
