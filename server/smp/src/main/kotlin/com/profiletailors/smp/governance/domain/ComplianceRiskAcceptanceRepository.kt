package com.profiletailors.smp.governance.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface ComplianceRiskAcceptanceRepository {
    fun activeForControl(
        controlId: ComplianceControlId,
        context: ComplianceEvaluationContext,
        evaluatedAt: Instant,
    ): Flow<ComplianceRiskAcceptance>
    suspend fun save(riskAcceptance: ComplianceRiskAcceptance): ComplianceRiskAcceptance
}
