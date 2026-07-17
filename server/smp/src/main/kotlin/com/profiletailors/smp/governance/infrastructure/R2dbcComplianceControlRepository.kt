package com.profiletailors.smp.governance.infrastructure

import com.profiletailors.smp.governance.domain.ApplicableComplianceControl
import com.profiletailors.smp.governance.domain.ComplianceControl
import com.profiletailors.smp.governance.domain.ComplianceControlApplicabilityRule
import com.profiletailors.smp.governance.domain.ComplianceControlApplicabilityRuleId
import com.profiletailors.smp.governance.domain.ComplianceControlId
import com.profiletailors.smp.governance.domain.ComplianceControlRepository
import com.profiletailors.smp.governance.domain.ComplianceControlStatus
import com.profiletailors.smp.governance.domain.ComplianceEvaluationContext
import com.profiletailors.smp.governance.domain.PageRequest
import io.r2dbc.spi.Row
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime

@Repository
class R2dbcComplianceControlRepository(private val databaseClient: DatabaseClient) : ComplianceControlRepository {

    /**
     * Finds a compliance control by its identifier.
     *
     * @param id The identifier of the compliance control to find.
     * @return The matching compliance control, or `null` if no control exists.
     */
    override suspend fun findById(id: ComplianceControlId): ComplianceControl? = databaseClient.sql(SELECT_BY_ID)
        .bind("id", id.value)
        .map { row, _ -> mapControl(row) }
        .first()
        .awaitSingleOrNull()

    /**
     * Retrieves a page of compliance controls ordered by creation time, newest first.
     *
     * @param page The pagination parameters defining the result limit and offset.
     * @return A flow of compliance controls in descending creation order.
     */
    override fun findAll(page: PageRequest): Flow<ComplianceControl> = databaseClient.sql(SELECT_ALL)
        .bind("limit", page.limit)
        .bind("offset", page.offset)
        .map { row, _ -> mapControl(row) }
        .all()
        .asFlow()

    /**
     * Finds compliance controls applicable at the specified evaluation time.
     *
     * @param context The evaluation context used to determine applicability.
     * @param evaluatedAt The timestamp at which applicability is evaluated.
     * @return A flow of applicable compliance controls.
     */
    override fun findApplicable(
        context: ComplianceEvaluationContext,
        evaluatedAt: Instant,
    ): Flow<ApplicableComplianceControl> = databaseClient.sql(SELECT_APPLICABLE_CONTROLS)
        .bind("evaluatedAt", evaluatedAt)
        .map { row, _ -> mapControl(row) }
        .all()
        .asFlow()
        .map { control ->
            ApplicableComplianceControl(
                control = control,
                matchingRule = ComplianceControlApplicabilityRule(
                    id = ComplianceControlApplicabilityRuleId("seed"),
                    controlId = control.id,
                ),
                required = true,
            )
        }

    /**
     * Saves a compliance control and advances its version and update timestamp.
     *
     * @param control The compliance control to save.
     * @return The saved control with its incremented version and updated timestamp.
     */
    override suspend fun save(control: ComplianceControl): ComplianceControl {
        val now = Instant.now()
        var spec = databaseClient.sql(UPSERT_CONTROL)
            .bind("id", control.id.value)
            .bind("controlKey", control.controlKey)
            .bind("name", control.name)

        spec = if (control.description != null) {
            spec.bind("description", control.description)
        } else {
            spec.bindNull("description", String::class.java)
        }
        spec = if (control.owner != null) {
            spec.bind("owner", control.owner)
        } else {
            spec.bindNull("owner", String::class.java)
        }
        spec = if (control.category != null) {
            spec.bind("category", control.category)
        } else {
            spec.bindNull("category", String::class.java)
        }

        spec.bind("status", control.status.name)
            .bind("version", control.version + 1)
            .bind("now", now)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return control.copy(version = control.version + 1, updatedAt = now)
    }

    /**
     * Maps a database row to a compliance control.
     *
     * @param row The database row containing compliance control fields.
     * @return The mapped compliance control.
     */
    private fun mapControl(row: Row): ComplianceControl = ComplianceControl(
        id = ComplianceControlId(requireNotNull(row.get("id", String::class.java))),
        controlKey = requireNotNull(row.get("control_key", String::class.java)),
        name = requireNotNull(row.get("name", String::class.java)),
        description = row.get("description", String::class.java),
        owner = row.get("owner", String::class.java),
        category = row.get("category", String::class.java),
        status = ComplianceControlStatus.valueOf(
            requireNotNull(row.get("status", String::class.java)),
        ),
        version = requireNotNull(row.get("version", Long::class.java)),
        nextReviewAt = row.get("next_review_at", OffsetDateTime::class.java)?.toInstant(),
        createdAt = requireNotNull(row.get("created_at", OffsetDateTime::class.java)).toInstant(),
        updatedAt = requireNotNull(row.get("updated_at", OffsetDateTime::class.java)).toInstant(),
    )

    companion object {
        private const val SELECT_BY_ID =
            "SELECT * FROM compliance_controls WHERE id = :id"

        private const val SELECT_ALL =
            "SELECT * FROM compliance_controls ORDER BY created_at DESC LIMIT :limit OFFSET :offset"

        private const val SELECT_APPLICABLE_CONTROLS =
            """
            SELECT DISTINCT cc.*
            FROM compliance_controls cc
            INNER JOIN compliance_control_applicability_rules ar ON ar.control_id = cc.id
            WHERE cc.status = 'ACTIVE'
              AND ar.valid_from <= :evaluatedAt
              AND (ar.valid_until IS NULL OR ar.valid_until > :evaluatedAt)
            ORDER BY cc.created_at DESC
            """

        private const val UPSERT_CONTROL =
            """
            INSERT INTO compliance_controls (id, control_key, name, description, owner, category, status, version, updated_at)
            VALUES (:id, :controlKey, :name, :description, :owner, :category, :status, :version, :now)
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                description = EXCLUDED.description,
                owner = EXCLUDED.owner,
                category = EXCLUDED.category,
                status = EXCLUDED.status,
                version = EXCLUDED.version,
                updated_at = EXCLUDED.updated_at
            """
    }
}
