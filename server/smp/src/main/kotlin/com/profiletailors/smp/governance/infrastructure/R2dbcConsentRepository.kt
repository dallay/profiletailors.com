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
import java.time.Instant
import java.time.OffsetDateTime

@Suppress("StringLiteralDuplication")
@Repository
class R2dbcConsentRepository(private val databaseClient: DatabaseClient) : ConsentRepository {

    override suspend fun save(record: ConsentRecord): ConsentRecord {
        var spec = databaseClient.sql(UPSERT_CONSENT)
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
            .bind("createdAt", record.createdAt)
            .bind("version", record.version)

        spec = bindNullableInstant(spec, "withdrawnAt", record.withdrawnAt)
        spec = bindNullable(spec, "withdrawalReason", record.withdrawalReason)

        spec.fetch().rowsUpdated().awaitSingle()
        return record
    }

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

    override suspend fun existsActive(
        workspaceId: String,
        subjectReference: SubjectReference,
        purpose: String,
        policyVersion: String,
    ): Boolean = findActive(workspaceId, subjectReference, purpose, policyVersion) != null

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

    private fun bindNullableInstant(
        spec: DatabaseClient.GenericExecuteSpec,
        name: String,
        value: Instant?,
    ): DatabaseClient.GenericExecuteSpec =
        if (value != null) spec.bind(name, value) else spec.bindNull(name, Instant::class.java)

    companion object {
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
                withdrawn_at, withdrawal_reason, created_at, version
            ) VALUES (
                :id, :workspaceId, :subjectKind, :subjectValue, :consentType,
                :purpose, :policyVersion, :source, :locale, :status, :givenAt,
                :withdrawnAt, :withdrawalReason, :createdAt, :version
            )
            ON CONFLICT (id) DO UPDATE SET
                status = EXCLUDED.status,
                withdrawn_at = EXCLUDED.withdrawn_at,
                withdrawal_reason = EXCLUDED.withdrawal_reason,
                version = consent_records.version + 1
        """
    }
}
