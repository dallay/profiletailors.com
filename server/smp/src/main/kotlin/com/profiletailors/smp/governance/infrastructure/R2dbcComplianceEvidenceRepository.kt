package com.profiletailors.smp.governance.infrastructure

import com.profiletailors.smp.governance.domain.ComplianceControlEvidence
import com.profiletailors.smp.governance.domain.ComplianceControlId
import com.profiletailors.smp.governance.domain.ComplianceEvidence
import com.profiletailors.smp.governance.domain.ComplianceEvidenceId
import com.profiletailors.smp.governance.domain.ComplianceEvidenceRepository
import com.profiletailors.smp.governance.domain.EvidenceLink
import com.profiletailors.smp.governance.domain.EvidenceLinkType
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
@Suppress("StringLiteralDuplication")
class R2dbcComplianceEvidenceRepository(private val databaseClient: DatabaseClient) : ComplianceEvidenceRepository {

    /**
     * Finds compliance evidence by its identifier.
     *
     * @param id The identifier of the compliance evidence to find.
     * @return The matching compliance evidence, or `null` if none exists.
     */
    override suspend fun findById(id: ComplianceEvidenceId): ComplianceEvidence? = databaseClient.sql(SELECT_BY_ID)
        .bind("id", id.value)
        .map { row, _ -> mapEvidence(row) }
        .first()
        .awaitSingleOrNull()

    /**
     * Finds all compliance evidence associated with a control.
     *
     * @param controlId The identifier of the control.
     * @return A flow of compliance evidence associated with the control.
     */
    override fun findByControlId(controlId: ComplianceControlId): Flow<ComplianceEvidence> =
        databaseClient.sql(SELECT_BY_CONTROL)
            .bind("controlId", controlId.value)
            .map { row, _ -> mapEvidence(row) }
            .all()
            .asFlow()

    /**
     * Persists compliance evidence and returns the provided evidence instance.
     *
     * @param evidence The compliance evidence to persist.
     * @return The provided compliance evidence.
     */
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
        spec = bindNullableInstant(spec, "reviewAt", evidence.reviewAt)
        spec = bindNullableInstant(spec, "verifiedAt", evidence.verifiedAt)

        spec.fetch()
            .rowsUpdated()
            .awaitSingle()
        return evidence
    }

    /**
     * Links evidence to a compliance control.
     *
     * @param controlId The compliance control identifier.
     * @param evidenceId The evidence identifier.
     * @param linkedBy The identifier of the user or process creating the link.
     * @return The created compliance control-evidence link.
     */
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

    /**
     * Saves an evidence link to an external artifact.
     *
     * @param link The evidence link to persist.
     * @return The persisted evidence link.
     */
    override suspend fun saveEvidenceLink(link: EvidenceLink): EvidenceLink {
        var spec = databaseClient.sql(INSERT_EVIDENCE_LINK)
            .bind("id", link.id)
            .bind("evidenceId", link.evidenceId.value)
            .bind("linkType", link.linkType.name)
            .bind("targetReference", link.targetReference)
            .bind("linkedBy", link.linkedBy)
        spec = bindNullable(spec, "description", link.description)
        spec.fetch()
            .rowsUpdated()
            .awaitSingle()
        return link
    }

    /**
     * Finds all evidence links for a given evidence.
     *
     * @param evidenceId The evidence identifier.
     * @return A flow of evidence links associated with the evidence.
     */
    override fun findLinksByEvidenceId(evidenceId: ComplianceEvidenceId): Flow<EvidenceLink> =
        databaseClient.sql(SELECT_LINKS_BY_EVIDENCE)
            .bind("evidenceId", evidenceId.value)
            .map { row, _ -> mapEvidenceLink(row) }
            .all()
            .asFlow()

    /**
     * Maps a database row to a compliance evidence domain object.
     *
     * @param row The database row containing compliance evidence data.
     * @return The mapped compliance evidence.
     * @throws NullPointerException If a required column is missing or null.
     * @throws IllegalArgumentException If the review status is invalid.
     */
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
        reviewAt = row.get("review_at", OffsetDateTime::class.java)?.toInstant(),
        verifiedAt = row.get("verified_at", OffsetDateTime::class.java)?.toInstant(),
        version = requireNotNull(row.get("version", Long::class.java)),
        createdAt = requireNotNull(row.get("created_at", OffsetDateTime::class.java)).toInstant(),
        updatedAt = requireNotNull(row.get("updated_at", OffsetDateTime::class.java)).toInstant(),
    )

    /**
     * Maps a database row to an evidence link domain object.
     *
     * @param row The database row containing evidence link data.
     * @return The mapped evidence link.
     */
    private fun mapEvidenceLink(row: Row): EvidenceLink = EvidenceLink(
        id = requireNotNull(row.get("id", String::class.java)),
        evidenceId = ComplianceEvidenceId(requireNotNull(row.get("evidence_id", String::class.java))),
        linkType = EvidenceLinkType.valueOf(requireNotNull(row.get("link_type", String::class.java))),
        targetReference = requireNotNull(row.get("target_reference", String::class.java)),
        description = row.get("description", String::class.java),
        linkedBy = requireNotNull(row.get("linked_by", String::class.java)),
        linkedAt = requireNotNull(row.get("linked_at", OffsetDateTime::class.java)).toInstant(),
        version = requireNotNull(row.get("version", Long::class.java)),
    )

    /**
     * Binds a string value to a statement parameter, or binds a typed SQL `NULL` when the value is absent.
     *
     * @param spec The statement specification to update.
     * @param name The parameter name.
     * @param value The nullable string value to bind.
     * @return The updated statement specification.
     */
    private fun bindNullable(
        spec: DatabaseClient.GenericExecuteSpec,
        name: String,
        value: String?,
    ): DatabaseClient.GenericExecuteSpec =
        if (value != null) spec.bind(name, value) else spec.bindNull(name, String::class.java)

    /**
     * Binds an instant value or a typed SQL `NULL` to the specified parameter.
     *
     * @param name The parameter name.
     * @param value The instant to bind, or `null`.
     * @return The execute specification with the parameter bound.
     */
    private fun bindNullableInstant(
        spec: DatabaseClient.GenericExecuteSpec,
        name: String,
        value: Instant?,
    ): DatabaseClient.GenericExecuteSpec =
        if (value != null) spec.bind(name, value) else spec.bindNull(name, Instant::class.java)

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
                 collected_at, valid_from, expires_at, review_at, verified_at, version, created_at, updated_at)
            VALUES
                (:id, :evidenceType, :title, :description, :referenceUrl, :immutableReference,
                 :checksum, :metadataJson, :submittedBy, :reviewedBy, :reviewStatus,
                 :collectedAt, :validFrom, :expiresAt, :reviewAt, :verifiedAt, :version,
                 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """
        private const val LINK_EVIDENCE = """
            INSERT INTO compliance_control_evidences (id, control_id, evidence_id, linked_by)
            VALUES (:id, :controlId, :evidenceId, :linkedBy)
        """
        private const val INSERT_EVIDENCE_LINK = """
            INSERT INTO evidence_links
                (id, evidence_id, link_type, target_reference, description, linked_by, linked_at, version)
            VALUES
                (:id, :evidenceId, :linkType, :targetReference, :description, :linkedBy, CURRENT_TIMESTAMP, 1)
        """
        private const val SELECT_LINKS_BY_EVIDENCE = """
            SELECT * FROM evidence_links WHERE evidence_id = :evidenceId ORDER BY linked_at DESC
        """
    }
}
