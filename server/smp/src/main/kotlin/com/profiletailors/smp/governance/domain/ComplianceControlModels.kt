package com.profiletailors.smp.governance.domain

import java.time.Instant

// ── Identifiers ──────────────────────────────────────────────────────

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

// ── Enums ────────────────────────────────────────────────────────────

enum class ComplianceControlStatus { ACTIVE, INACTIVE, DEPRECATED }

enum class ScopeType(val value: String) {
    RELEASE("RELEASE"),
    MARKET("MARKET"),
    ENVIRONMENT("ENVIRONMENT"),
    PROVIDER("PROVIDER"),
    PRODUCT("PRODUCT"),
    WORKSPACE("WORKSPACE"),
}

enum class EvidenceReviewStatus { PENDING, APPROVED, REJECTED }

enum class RiskAcceptanceStatus { ACTIVE, EXPIRED, REVOKED }

enum class ControlStatus { PASS, FAIL, WAIVED, NOT_ASSESSED, NOT_APPLICABLE, WARNING }

enum class EvaluationStatus { COMPLIANT, NON_COMPLIANT, PARTIAL, NOT_ASSESSED }

// ── Compliance Control ───────────────────────────────────────────────

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

// ── Applicability ────────────────────────────────────────────────────

data class ApplicabilityDimension(
    val id: ApplicabilityDimensionId,
    val ruleId: ComplianceControlApplicabilityRuleId,
    val scopeType: ScopeType,
    val scopeValue: String,
    val createdAt: Instant = Instant.now(),
    val version: Long = 1,
)

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

// ── Evidence Requirements ────────────────────────────────────────────

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
