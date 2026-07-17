package com.profiletailors.smp.governance.domain

import java.time.Instant

@JvmInline
value class ComplianceControlId(val value: String)

@JvmInline
value class ComplianceControlApplicabilityRuleId(val value: String)

@JvmInline
value class ApplicabilityDimensionId(val value: String)

@JvmInline
value class ComplianceControlEvidenceRequirementId(val value: String)

@JvmInline
value class ComplianceEvidenceId(val value: String)

@JvmInline
value class ComplianceRiskAcceptanceId(val value: String)

/** Lifecycle state of a compliance control definition. */
enum class ComplianceControlStatus { ACTIVE, INACTIVE, DEPRECATED }

/** Dimension along which a control's applicability can be scoped. */
enum class ScopeType(val value: String) {
    RELEASE("RELEASE"),
    MARKET("MARKET"),
    ENVIRONMENT("ENVIRONMENT"),
    PROVIDER("PROVIDER"),
    PRODUCT("PRODUCT"),
    WORKSPACE("WORKSPACE"),
}

/** Review state of a submitted compliance evidence. */
enum class EvidenceReviewStatus { PENDING, APPROVED, REJECTED }

/** Lifecycle state of a recorded risk acceptance (waiver). */
enum class RiskAcceptanceStatus { ACTIVE, EXPIRED, REVOKED }

/** Result status for a single control within an evaluation. */
enum class ControlStatus { PASS, FAIL, WAIVED, NOT_ASSESSED, NOT_APPLICABLE, WARNING }

/** Overall compliance status for a complete evaluation. */
enum class EvaluationStatus { COMPLIANT, NON_COMPLIANT, PARTIAL, NOT_ASSESSED }

/**
 * A compliance control that must be satisfied for the system to be compliant.
 *
 * Each control has a unique key, a human-readable name, and optional
 * ownership, category, and review scheduling metadata.
 */
data class ComplianceControl(
    val id: ComplianceControlId,
    val controlKey: String,
    val name: String,
    val description: String? = null,
    val owner: String? = null,
    val category: String? = null,
    val status: ComplianceControlStatus = ComplianceControlStatus.ACTIVE,
    val version: Long = 1,
    val nextReviewAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)

/**
 * A single scope dimension value that forms part of an applicability rule.
 */
data class ApplicabilityDimension(
    val id: ApplicabilityDimensionId,
    val ruleId: ComplianceControlApplicabilityRuleId,
    val scopeType: ScopeType,
    val scopeValue: String,
    val createdAt: Instant = Instant.now(),
    val version: Long = 1,
)

/**
 * Rule that determines when and where a compliance control applies.
 */
data class ComplianceControlApplicabilityRule(
    val id: ComplianceControlApplicabilityRuleId,
    val controlId: ComplianceControlId,
    val required: Boolean = true,
    val validFrom: Instant = Instant.now(),
    val validUntil: Instant? = null,
    val version: Long = 1,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val dimensions: List<ApplicabilityDimension> = emptyList(),
)

/**
 * Requirement linking a compliance control to a specific evidence type.
 */
data class ComplianceControlEvidenceRequirement(
    val id: ComplianceControlEvidenceRequirementId,
    val controlId: ComplianceControlId,
    val evidenceType: String,
    val minimumApprovedEvidence: Int = 1,
    val manualApprovalRequired: Boolean = true,
    val required: Boolean = true,
    val validFrom: Instant = Instant.now(),
    val validUntil: Instant? = null,
    val version: Long = 1,
)
