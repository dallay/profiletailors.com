package com.profiletailors.smp.governance.infrastructure

import com.profiletailors.smp.governance.domain.ComplianceControlId
import com.profiletailors.smp.governance.domain.ComplianceEvaluationContext
import com.profiletailors.smp.governance.domain.ComplianceRiskAcceptance
import com.profiletailors.smp.governance.domain.ComplianceRiskAcceptanceId
import com.profiletailors.smp.governance.domain.ComplianceRiskAcceptanceRepository
import com.profiletailors.smp.governance.domain.RiskAcceptanceStatus
import io.r2dbc.spi.Row
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime

@Repository
class R2dbcComplianceRiskAcceptanceRepository(private val databaseClient: DatabaseClient) :
    ComplianceRiskAcceptanceRepository {

    override fun findActiveForControl(
        controlId: ComplianceControlId,
        context: ComplianceEvaluationContext,
        evaluatedAt: Instant,
    ): Flow<ComplianceRiskAcceptance> = databaseClient.sql(SELECT_ACTIVE_BY_CONTEXT)
        .bind("controlId", controlId.value)
        .bind("evaluatedAt", evaluatedAt)
        .map { row, _ -> mapRiskAcceptance(row) }
        .all()
        .asFlow()

    override suspend fun save(riskAcceptance: ComplianceRiskAcceptance): ComplianceRiskAcceptance {
        var spec = databaseClient.sql(INSERT_RISK_ACCEPTANCE)
            .bind("id", riskAcceptance.id.value)
            .bind("controlId", riskAcceptance.controlId.value)
            .bind("riskSummary", riskAcceptance.riskSummary)
            .bind("requestedBy", riskAcceptance.requestedBy)
            .bind("expiresAt", riskAcceptance.expiresAt)
            .bind("status", riskAcceptance.status.name)
            .bind("version", riskAcceptance.version + 1)

        spec = bindNullable(spec, "releaseScope", riskAcceptance.releaseScope)
        spec = bindNullable(spec, "marketScope", riskAcceptance.marketScope)
        spec = bindNullable(spec, "environmentScope", riskAcceptance.environmentScope)
        spec = bindNullable(spec, "providerScope", riskAcceptance.providerScope)
        spec = bindNullable(spec, "productScope", riskAcceptance.productScope)
        spec = bindNullable(spec, "workspaceScope", riskAcceptance.workspaceScope)
        spec = bindNullable(spec, "residualRisk", riskAcceptance.residualRisk)
        spec = bindNullable(spec, "justification", riskAcceptance.justification)
        spec = bindNullable(spec, "acceptedBy", riskAcceptance.acceptedBy)
        spec = bindNullableInstant(spec, "acceptedAt", riskAcceptance.acceptedAt)

        spec.fetch()
            .rowsUpdated()
            .awaitSingle()
        return riskAcceptance
    }

    private fun mapRiskAcceptance(row: Row): ComplianceRiskAcceptance = ComplianceRiskAcceptance(
        id = ComplianceRiskAcceptanceId(requireNotNull(row.get("id", String::class.java))),
        controlId = ComplianceControlId(requireNotNull(row.get("control_id", String::class.java))),
        releaseScope = row.get("release_scope", String::class.java),
        marketScope = row.get("market_scope", String::class.java),
        environmentScope = row.get("environment_scope", String::class.java),
        providerScope = row.get("provider_scope", String::class.java),
        productScope = row.get("product_scope", String::class.java),
        workspaceScope = row.get("workspace_scope", String::class.java),
        riskSummary = requireNotNull(row.get("risk_summary", String::class.java)),
        residualRisk = row.get("residual_risk", String::class.java),
        justification = row.get("justification", String::class.java),
        requestedBy = requireNotNull(row.get("requested_by", String::class.java)),
        acceptedBy = row.get("accepted_by", String::class.java),
        acceptedAt = row.get("accepted_at", OffsetDateTime::class.java)?.toInstant(),
        expiresAt = requireNotNull(row.get("expires_at", OffsetDateTime::class.java)).toInstant(),
        revokedAt = row.get("revoked_at", OffsetDateTime::class.java)?.toInstant(),
        status = RiskAcceptanceStatus.valueOf(
            requireNotNull(row.get("status", String::class.java)),
        ),
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
        private const val SELECT_ACTIVE_BY_CONTEXT = """
            SELECT * FROM compliance_risk_acceptances
            WHERE control_id = :controlId
              AND status = 'ACTIVE'
              AND expires_at > :evaluatedAt
              AND revoked_at IS NULL
            ORDER BY created_at DESC
        """
        private const val INSERT_RISK_ACCEPTANCE = """
            INSERT INTO compliance_risk_acceptances
                (id, control_id, release_scope, market_scope, environment_scope,
                 provider_scope, product_scope, workspace_scope, risk_summary,
                 residual_risk, justification, requested_by, accepted_by, accepted_at,
                 expires_at, status, version, created_at, updated_at)
            VALUES
                (:id, :controlId, :releaseScope, :marketScope, :environmentScope,
                 :providerScope, :productScope, :workspaceScope, :riskSummary,
                 :residualRisk, :justification, :requestedBy, :acceptedBy, :acceptedAt,
                 :expiresAt, :status, :version, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """
    }
}
