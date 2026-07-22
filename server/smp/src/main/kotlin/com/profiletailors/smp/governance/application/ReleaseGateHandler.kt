package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.smp.governance.domain.ComplianceEvaluation
import com.profiletailors.smp.governance.domain.ComplianceEvaluationContext

/**
 * Derives the gate status from the evaluation summary:
 * - PASS when every applicable control is passed or waived,
 * - FAIL when any control fails,
 * - NOT_APPLICABLE when no controls apply.
 */
@Service
internal class ReleaseGateHandler(private val mediator: Mediator) : QueryHandler<ReleaseGateQuery, ReleaseGateResult> {

    override suspend fun handle(query: ReleaseGateQuery): ReleaseGateResult {
        val evaluation: ComplianceEvaluation = mediator.send(
            EvaluateComplianceQuery(
                context = ComplianceEvaluationContext(release = query.release),
            ),
        )

        val gateStatus = when {
            evaluation.summary.totalControls == 0 -> "NOT_APPLICABLE"
            evaluation.summary.failed > 0 -> "FAIL"
            evaluation.summary.passed + evaluation.summary.waived == evaluation.summary.totalControls -> "PASS"
            else -> "NOT_APPLICABLE"
        }

        return ReleaseGateResult(
            release = query.release,
            gateStatus = gateStatus,
            totalControls = evaluation.summary.totalControls,
            passed = evaluation.summary.passed,
            failed = evaluation.summary.failed,
            waived = evaluation.summary.waived,
            evaluatedAt = evaluation.evaluatedAt.toString(),
        )
    }
}
