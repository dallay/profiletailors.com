package com.profiletailors.smp.governance.infrastructure

import com.profiletailors.smp.governance.domain.ComplianceControlEvidence
import com.profiletailors.smp.governance.domain.ComplianceControlId
import com.profiletailors.smp.governance.domain.ComplianceEvidence
import com.profiletailors.smp.governance.domain.ComplianceEvidenceId
import com.profiletailors.smp.governance.domain.ComplianceEvidenceRepository
import com.profiletailors.smp.governance.domain.EvidenceReviewStatus
import io.r2dbc.spi.Row
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime

@Repository
class R2dbcComplianceEvidenceRepository(private val databaseClient: DatabaseClient) : ComplianceEvidenceRepository {

    override suspend fun findById(id: ComplianceEvidenceId): ComplianceEvidence? = databaseClient.sql(SELECT_BY_ID)
        .bind("id", id.value)
        .map { row, _ -> mapEvidence(row) }
        .first()
        .awaitSingleOrNull()

    override fun findByControlId(controlId: ComplianceControlId): Flow<ComplianceEvidence> =
        databaseClient.sql(SELECT_BY_CONTROL)
            .bind("controlId", controlId.value)
            .map { row, _ -> mapEvidence(row) }
            .all()
            .asFlow()

    override suspend fun save(evidence: ComplianceEvidence): ComplianceEvidence {
        var spec = databaseClient.sql(INSERT_EVIDENCE)
            .bind("id", evidence.id.value)
            .bind("evidenceType", evidence.evidenceType)
            .bind("title", evidence.title)
            .bind("submittedBy", evidence.submittedBy)
            .bind("reviewStatus", evidence.reviewStatus.name)
            .bind("collectedAt", evidence.collectedAt)
            .bind("validFrom", evidence.validFrom)
            .bind("version", evidence.version + 1)

        spec = bindNullable(spec, "description", evidence.description)
        spec = bindNullable(spec, "referenceUrl", evidence.referenceUrl)
        spec = bindNullable(spec, "immutableReference", evidence.immutableReference)
        spec = bindNullable(spec, "checksum", evidence.checksum)
        spec = bindNullable(spec, "metadataJson", evidence.metadataJson)
        spec = bindNullable(spec, "reviewedBy", evidence.reviewedBy)
        spec = bindNullableInstant(spec, "expiresAt", evidence.expiresAt)
        spec = bindNullableInstant(spec, "verifiedAt", evidence.verifiedAt)

        spec.fetch()
            .rowsUpdated()
            .awaitSingle()
        return evidence
    }

    override suspend fun linkControlEvidence(
        controlId: ComplianceControlId,
        evidenceId: ComplianceEvidenceId,
        linkedBy: String,
    ): ComplianceControlEvidence {
        val id = "ctrlev-${controlId.value}-${evidenceId.value}"
        databaseClient.sql(LINK_EVIDENCE)
            .bind("id", id)
            .bind("controlId", controlId.value)
            .bind("evidenceId", evidenceId.value)
            .bind("linkedBy", linkedBy)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return ComplianceControlEvidence(
            id = id,
            controlId = controlId,
            evidenceId = evidenceId,
            linkedBy = linkedBy,
        )
    }

    private fun mapEvidence(row: Row): ComplianceEvidence = ComplianceEvidence(
        id = ComplianceEvidenceId(requireNotNull(row.get("id", String::class.java))),
        evidenceType = requireNotNull(row.get("evidence_type", String::class.java)),
        title = requireNotNull(row.get("title", String::class.java)),
        description = row.get("description", String::class.java),
        referenceUrl = row.get("reference_url", String::class.java),
        immutableReference = row.get("immutable_reference", String::class.java),
        checksum = row.get("checksum", String::class.java),
        metadataJson = row.get("metadata_json", String::class.java),
        submittedBy = requireNotNull(row.get("submitted_by", String::class.java)),
        reviewedBy = row.get("reviewed_by", String::class.java),
        reviewStatus = EvidenceReviewStatus.valueOf(
            requireNotNull(row.get("review_status", String::class.java)),
        ),
        collectedAt = requireNotNull(row.get("collected_at", OffsetDateTime::class.java)).toInstant(),
        validFrom = requireNotNull(row.get("valid_from", OffsetDateTime::class.java)).toInstant(),
        expiresAt = row.get("expires_at", OffsetDateTime::class.java)?.toInstant(),
        verifiedAt = row.get("verified_at", OffsetDateTime::class.java)?.toInstant(),
        version = requireNotNull(row.get("version", Long::class.java)),
        createdAt = requireNotNull(row.get("created_at", OffsetDateTime::class.java)).toInstant(),
        updatedAt = requireNotNull(row.get("updated_at", OffsetDateTime::class.java)).toInstant(),
    )

    private fun bindNullable(
        spec: DatabaseClient.GenericExecuteSpec,
        name: String,
        value: String?,
    ): DatabaseClient.GenericExecuteSpec = if (value !=
        null
    ) {
        spec.bind(name, value)
    } else {
        spec.bindNull(name, String::class.java)
    }

    private fun bindNullableInstant(
        spec: DatabaseClient.GenericExecuteSpec,
        name: String,
        value: Instant?,
    ): DatabaseClient.GenericExecuteSpec = if (value !=
        null
    ) {
        spec.bind(name, value)
    } else {
        spec.bindNull(name, Instant::class.java)
    }

    companion object {
        private const val SELECT_BY_ID = "SELECT * FROM compliance_evidences WHERE id = :id"
        private const val SELECT_BY_CONTROL = """
            SELECT e.* FROM compliance_evidences e
            INNER JOIN compliance_control_evidences ce ON ce.evidence_id = e.id
            WHERE ce.control_id = :controlId
            ORDER BY e.created_at DESC
        """
        private const val INSERT_EVIDENCE = """
            INSERT INTO compliance_evidences
                (id, evidence_type, title, description, reference_url, immutable_reference,
                 checksum, metadata_json, submitted_by, reviewed_by, review_status,
                 collected_at, valid_from, expires_at, verified_at, version, created_at, updated_at)
            VALUES
                (:id, :evidenceType, :title, :description, :referenceUrl, :immutableReference,
                 :checksum, :metadataJson, :submittedBy, :reviewedBy, :reviewStatus,
                 :collectedAt, :validFrom, :expiresAt, :verifiedAt, :version,
                 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """
        private const val LINK_EVIDENCE = """
            INSERT INTO compliance_control_evidences (id, control_id, evidence_id, linked_by)
            VALUES (:id, :controlId, :evidenceId, :linkedBy)
        """
    }
}
