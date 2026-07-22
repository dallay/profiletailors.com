package com.profiletailors.smp.governance.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.governance.application.EvaluateComplianceQuery
import com.profiletailors.smp.governance.application.ReleaseGateQuery
import com.profiletailors.smp.governance.application.ReleaseGateResult
import com.profiletailors.smp.governance.domain.ComplianceEvaluation
import com.profiletailors.smp.governance.domain.ComplianceEvaluationContext
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for governance compliance evaluation.
 */
@RestController
@RequestMapping("/api/governance/compliance")
@Validated
class ComplianceController(private val mediator: Mediator) {

    data class EvaluationContextRequest(
        @field:Size(max = 100) val release: String? = null,
        @field:Size(max = 100) val market: String? = null,
        @field:Size(max = 100) val environment: String? = null,
        @field:Size(max = 100) val provider: String? = null,
        @field:Size(max = 100) val product: String? = null,
        @field:Size(max = 100) val workspace: String? = null,
    )

    data class EvaluationRequest(@Valid val context: EvaluationContextRequest)

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
    suspend fun evaluate(@Valid @RequestBody request: EvaluationRequest): EvaluationResponse {
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
     * Evaluates compliance for a release and summarizes its release-gate status.
     *
     * @param release The release identifier to evaluate.
     * @return A map containing the release, gate status, summary counts, and evaluation timestamp.
     */
    @GetMapping("/release-gate")
    @ResponseStatus(HttpStatus.OK)
    suspend fun releaseGate(
        @RequestParam
        @Size(max = 100)
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$")
        release: String = "0.1.0",
    ): ReleaseGateResult {
        val query = ReleaseGateQuery(release = release)
        return mediator.send(query)
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
