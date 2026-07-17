package com.profiletailors.smp.governance.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.governance.application.EvaluateComplianceQuery
import com.profiletailors.smp.governance.domain.ComplianceEvaluation
import com.profiletailors.smp.governance.domain.ComplianceEvaluationContext
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/governance/compliance")
class ComplianceController(private val mediator: Mediator) {

    // ── Evaluation ───────────────────────────────────────────────────

    data class EvaluationContextRequest(
        val release: String? = null,
        val market: String? = null,
        val environment: String? = null,
        val provider: String? = null,
        val product: String? = null,
        val workspace: String? = null,
    )

    data class EvaluationRequest(val context: EvaluationContextRequest)

    data class EvaluationSummaryResponse(
        val totalControls: Int,
        val passed: Int,
        val failed: Int,
        val waived: Int,
        val notAssessed: Int,
        val warnings: Int,
        val notApplicable: Int,
    )

    data class EvaluationResponse(
        val context: EvaluationContextRequest,
        val overallStatus: String,
        val summary: EvaluationSummaryResponse,
        val controlResults: List<ControlResultResponse>,
        val evaluatedAt: String,
        val evaluatedBy: String,
    )

    data class ControlResultResponse(val controlId: String, val controlKey: String, val status: String)

    /**
     * Evaluates compliance for the supplied context.
     *
     * @param request The request containing the compliance evaluation context.
     * @return The compliance evaluation response.
     */
    @PostMapping("/evaluations")
    @ResponseStatus(HttpStatus.OK)
    suspend fun evaluate(@RequestBody request: EvaluationRequest): EvaluationResponse {
        val query = EvaluateComplianceQuery(
            context = ComplianceEvaluationContext(
                release = request.context.release,
                market = request.context.market,
                environment = request.context.environment,
                provider = request.context.provider,
                product = request.context.product,
                workspace = request.context.workspace,
            ),
        )
        val evaluation: ComplianceEvaluation = mediator.send(query)
        return toResponse(evaluation, request.context)
    }

    /**
     * Reports that the compliance service is available.
     *
     * @return A map containing the status value `"ok"`.
     */

    @GetMapping("/ping")
    @ResponseStatus(HttpStatus.OK)
    fun ping(): Map<String, String> = mapOf("status" to "ok")

    /**
         * Maps a compliance evaluation and its request context to an API response.
         *
         * @param evaluation The completed compliance evaluation.
         * @param context The context used for the evaluation.
         * @return The response containing evaluation status, summary, control results, and metadata.
         */
        private fun toResponse(evaluation: ComplianceEvaluation, context: EvaluationContextRequest): EvaluationResponse =
        EvaluationResponse(
            context = context,
            overallStatus = evaluation.overallStatus.name,
            summary = EvaluationSummaryResponse(
                totalControls = evaluation.summary.totalControls,
                passed = evaluation.summary.passed,
                failed = evaluation.summary.failed,
                waived = evaluation.summary.waived,
                notAssessed = evaluation.summary.notAssessed,
                warnings = evaluation.summary.warnings,
                notApplicable = evaluation.summary.notApplicable,
            ),
            controlResults = evaluation.controlResults.map { result ->
                ControlResultResponse(
                    controlId = result.control.id.value,
                    controlKey = result.control.controlKey,
                    status = result.status.name,
                )
            },
            evaluatedAt = evaluation.evaluatedAt.toString(),
            evaluatedBy = evaluation.evaluatedBy,
        )
}
