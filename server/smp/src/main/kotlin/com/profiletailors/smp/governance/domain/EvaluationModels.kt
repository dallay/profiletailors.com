package com.profiletailors.smp.governance.domain

import com.profiletailors.common.domain.ValueObject
import java.time.Instant

/**
 * Context for evaluating compliance controls.
 * Null dimensions act as wildcards (match any value).
 * A future release-gate consumer MUST supply a fully populated context.
 */
data class ComplianceEvaluationContext(
    val release: String? = null,
    val market: String? = null,
    val environment: String? = null,
    val provider: String? = null,
    val product: String? = null,
    val workspace: String? = null,
)

/**
 * A control matched by an applicability rule.
 */
data class ApplicableComplianceControl(
    val control: ComplianceControl,
    val matchingRule: ComplianceControlApplicabilityRule,
    val required: Boolean,
)

/**
 * Per-requirement evaluation result.
 */
data class RequirementResult(
    val requirement: ComplianceControlEvidenceRequirement,
    val status: RequirementStatus,
    val matchedEvidenceCount: Int,
)

@ValueObject
enum class RequirementStatus { SATISFIED, PENDING, MISSING }

/**
 * Per-control evaluation result.
 */
data class ControlResult(
    val control: ComplianceControl,
    val status: ControlStatus,
    val requirementResults: List<RequirementResult> = emptyList(),
    val expiredEvidenceCount: Int = 0,
    val pendingEvidenceCount: Int = 0,
)

data class EvaluationSummary(
    val totalControls: Int,
    val passed: Int,
    val failed: Int,
    val waived: Int,
    val notAssessed: Int,
    val warnings: Int,
    val notApplicable: Int,
)

/**
 * Ephemeral evaluation result. NOT persisted in PR 2.
 * DALLAY-466 will persist release-gate results.
 */
data class ComplianceEvaluation(
    val context: ComplianceEvaluationContext,
    val overallStatus: EvaluationStatus,
    val summary: EvaluationSummary,
    val controlResults: List<ControlResult>,
    val evaluatedAt: Instant,
    val evaluatedBy: String,
)
