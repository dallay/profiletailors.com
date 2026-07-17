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

@Service
internal class EvaluateComplianceHandler(
    private val controlRepository: ComplianceControlRepository,
    private val evidenceRepository: ComplianceEvidenceRepository,
    private val riskAcceptanceRepository: ComplianceRiskAcceptanceRepository,
    private val clock: Clock = Clock.systemUTC(),
) : QueryHandler<EvaluateComplianceQuery, ComplianceEvaluation> {

    override suspend fun handle(query: EvaluateComplianceQuery): ComplianceEvaluation {
        val evaluatedAt = clock.instant()
        val ctx = query.context
        val applicableControls = controlRepository.findApplicable(ctx, evaluatedAt).toList()

        val controlResults = applicableControls.map { applicable ->
            val linkedEvidence = evidenceRepository.findByControlId(applicable.control.id).toList()
            val activeWaivers = riskAcceptanceRepository
                .findActiveForControl(applicable.control.id, ctx, evaluatedAt)
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

    private fun calculateOverallStatus(requiredResults: List<ControlResult>): EvaluationStatus {
        if (requiredResults.isEmpty()) return EvaluationStatus.NOT_ASSESSED
        if (requiredResults.any { it.status == ControlStatus.FAIL }) return EvaluationStatus.NON_COMPLIANT

        val hasNotAssessed = requiredResults.any { it.status == ControlStatus.NOT_ASSESSED }
        val hasPassed = requiredResults.any { it.status == ControlStatus.PASS }
        val hasWaived = requiredResults.any { it.status == ControlStatus.WAIVED }

        return when {
            hasNotAssessed -> EvaluationStatus.NOT_ASSESSED
            hasWaived -> EvaluationStatus.PARTIAL
            hasPassed -> EvaluationStatus.COMPLIANT
            else -> EvaluationStatus.NOT_ASSESSED
        }
    }

    private fun waiverMatchesContext(waiver: ComplianceRiskAcceptance, context: ComplianceEvaluationContext): Boolean {
        if (waiver.status != RiskAcceptanceStatus.ACTIVE) return false
        return matchesOrNull(waiver.releaseScope, context.release) &&
            matchesOrNull(waiver.marketScope, context.market) &&
            matchesOrNull(waiver.environmentScope, context.environment) &&
            matchesOrNull(waiver.providerScope, context.provider) &&
            matchesOrNull(waiver.productScope, context.product) &&
            matchesOrNull(waiver.workspaceScope, context.workspace)
    }

    private fun matchesOrNull(waiverScope: String?, contextValue: String?): Boolean =
        waiverScope == null || contextValue == null || waiverScope == contextValue
}
