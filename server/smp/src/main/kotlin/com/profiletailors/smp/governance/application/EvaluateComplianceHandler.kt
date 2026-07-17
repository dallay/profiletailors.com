package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.smp.governance.domain.ComplianceControlRepository
import com.profiletailors.smp.governance.domain.ComplianceEvaluation
import com.profiletailors.smp.governance.domain.ComplianceEvaluationContext
import com.profiletailors.smp.governance.domain.ComplianceEvidenceRepository
import com.profiletailors.smp.governance.domain.ComplianceRiskAcceptance
import com.profiletailors.smp.governance.domain.ComplianceRiskAcceptanceRepository
import com.profiletailors.smp.governance.domain.ControlResult
import com.profiletailors.smp.governance.domain.ControlStatus
import com.profiletailors.smp.governance.domain.EvaluationStatus
import com.profiletailors.smp.governance.domain.EvaluationSummary
import com.profiletailors.smp.governance.domain.RiskAcceptanceStatus
import kotlinx.coroutines.flow.toList
import java.time.Clock

/**
 * Handler that evaluates compliance by matching applicable controls against
 * linked evidence and active risk acceptances (waivers).
 *
 * For each applicable control the handler determines one of:
 * - PASS if linked evidence exists,
 * - WAIVED if an active risk acceptance matches the evaluation context,
 * - FAIL if no evidence is present,
 * - WARNING if the control is not required.
 */
@Service
internal class EvaluateComplianceHandler(
    private val controlRepository: ComplianceControlRepository,
    private val evidenceRepository: ComplianceEvidenceRepository,
    private val riskAcceptanceRepository: ComplianceRiskAcceptanceRepository,
    private val clock: Clock = Clock.systemUTC(),
) : QueryHandler<EvaluateComplianceQuery, ComplianceEvaluation> {

    /**
     * Evaluates applicable compliance controls for the query context.
     *
     * @param query The query containing the compliance evaluation context.
     * @return The compliance evaluation with control results, summary counts, overall status, and evaluation metadata.
     */
    override suspend fun handle(query: EvaluateComplianceQuery): ComplianceEvaluation {
        val evaluatedAt = clock.instant()
        val ctx = query.context
        val applicableControls = controlRepository.findApplicable(ctx, evaluatedAt).toList()

        val controlResults = applicableControls.map { applicable ->
            val linkedEvidence = evidenceRepository.findByControlId(applicable.control.id).toList()
            val activeWaivers = riskAcceptanceRepository
                .activeForControl(applicable.control.id, ctx, evaluatedAt)
                .toList()

            val waived = activeWaivers.any { waiverMatchesContext(it, ctx) }

            when {
                !applicable.required -> ControlResult(applicable.control, ControlStatus.WARNING)
                waived -> ControlResult(applicable.control, ControlStatus.WAIVED)
                linkedEvidence.isEmpty() -> ControlResult(applicable.control, ControlStatus.FAIL)
                else -> ControlResult(applicable.control, ControlStatus.PASS)
            }
        }

        val requiredResults = controlResults.filter { it.status != ControlStatus.WARNING }
        val overallStatus = calculateOverallStatus(requiredResults)

        val failed = controlResults.count { it.status == ControlStatus.FAIL }
        val passed = controlResults.count { it.status == ControlStatus.PASS }
        val waived = controlResults.count { it.status == ControlStatus.WAIVED }
        val warnings = controlResults.count { it.status == ControlStatus.WARNING }
        val notAssessed = controlResults.size - passed - failed - waived - warnings

        return ComplianceEvaluation(
            context = ctx,
            overallStatus = overallStatus,
            summary = EvaluationSummary(
                totalControls = controlResults.size,
                passed = passed,
                failed = failed,
                waived = waived,
                notAssessed = notAssessed,
                warnings = warnings,
                notApplicable = 0,
            ),
            controlResults = controlResults,
            evaluatedAt = evaluatedAt,
            evaluatedBy = "system",
        )
    }

    /**
     * Determines the overall evaluation status from required control results.
     *
     * @param requiredResults The results for controls that require compliance.
     * @return The overall evaluation status based on the required control statuses.
     */
    private fun calculateOverallStatus(requiredResults: List<ControlResult>): EvaluationStatus = when {
        requiredResults.isEmpty() -> EvaluationStatus.NOT_ASSESSED
        requiredResults.any { it.status == ControlStatus.FAIL } -> EvaluationStatus.NON_COMPLIANT
        requiredResults.any { it.status == ControlStatus.NOT_ASSESSED } -> EvaluationStatus.NOT_ASSESSED
        requiredResults.any { it.status == ControlStatus.WAIVED } -> EvaluationStatus.PARTIAL
        requiredResults.any { it.status == ControlStatus.PASS } -> EvaluationStatus.COMPLIANT
        else -> EvaluationStatus.NOT_ASSESSED
    }

    /**
     * Determines whether a risk acceptance applies to an evaluation context.
     *
     * @param waiver The risk acceptance to evaluate.
     * @param context The evaluation context to match against the waiver scopes.
     * @return `true` if the waiver is active and all specified scopes match, `false` otherwise.
     */
    private fun waiverMatchesContext(waiver: ComplianceRiskAcceptance, context: ComplianceEvaluationContext): Boolean {
        if (waiver.status != RiskAcceptanceStatus.ACTIVE) return false
        return matchesOrNull(waiver.releaseScope, context.release) &&
            matchesOrNull(waiver.marketScope, context.market) &&
            matchesOrNull(waiver.environmentScope, context.environment) &&
            matchesOrNull(waiver.providerScope, context.provider) &&
            matchesOrNull(waiver.productScope, context.product) &&
            matchesOrNull(waiver.workspaceScope, context.workspace)
    }

    /**
     * Determines whether a waiver scope applies to a context value.
     *
     * @param waiverScope The waiver scope value, or `null` to match any context value.
     * @param contextValue The context value, or `null` to match any waiver scope.
     * @return `true` if either value is `null` or both values are equal, `false` otherwise.
     */
    private fun matchesOrNull(waiverScope: String?, contextValue: String?): Boolean =
        waiverScope == null || contextValue == null || waiverScope == contextValue
}
