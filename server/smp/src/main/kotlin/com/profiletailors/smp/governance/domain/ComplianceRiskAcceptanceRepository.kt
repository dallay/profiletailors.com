package com.profiletailors.smp.governance.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface ComplianceRiskAcceptanceRepository {
    /**
     * Finds active risk acceptances for a control at a specific evaluation time and context.
     *
     * @param controlId The identifier of the compliance control.
     * @param context The evaluation context used to filter risk acceptances.
     * @param evaluatedAt The point in time used to determine active risk acceptances.
     * @return A stream of active compliance risk acceptances matching the criteria.
     */
    fun activeForControl(
        controlId: ComplianceControlId,
        context: ComplianceEvaluationContext,
        evaluatedAt: Instant,
    ): Flow<ComplianceRiskAcceptance>
    /**
 * Persists a compliance risk acceptance.
 *
 * @param riskAcceptance The compliance risk acceptance to persist.
 * @return The persisted compliance risk acceptance.
 */
suspend fun save(riskAcceptance: ComplianceRiskAcceptance): ComplianceRiskAcceptance
}
