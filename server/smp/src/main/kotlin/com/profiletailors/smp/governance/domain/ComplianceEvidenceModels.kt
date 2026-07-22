package com.profiletailors.smp.governance.domain

import java.time.Instant

/**
 * A piece of compliance evidence — a document, attestation, or artifact
 * that demonstrates compliance with one or more controls.
 */
data class ComplianceEvidence(
    val id: ComplianceEvidenceId,
    val evidenceType: String,
    val title: String,
    val description: String? = null,
    val referenceUrl: String? = null,
    val immutableReference: String? = null,
    val checksum: String? = null,
    val metadataJson: String? = null,
    val submittedBy: String,
    val reviewedBy: String? = null,
    val reviewStatus: EvidenceReviewStatus = EvidenceReviewStatus.PENDING,
    val collectedAt: Instant = Instant.now(),
    val validFrom: Instant = Instant.now(),
    val expiresAt: Instant? = null,
    val reviewAt: Instant? = null,
    val verifiedAt: Instant? = null,
    val version: Long = 1,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)

/**
 * Immutable link between a control and an evidence.
 * Once created, this association cannot be removed.
 * To invalidate evidence coverage, expire or reject the evidence itself.
 */
data class ComplianceControlEvidence(
    val id: String,
    val controlId: ComplianceControlId,
    val evidenceId: ComplianceEvidenceId,
    val linkedBy: String,
    val linkedAt: Instant = Instant.now(),
    val version: Long = 1,
)

/**
 * Generic evidence link to external artifacts (code, tests, documents, operational records).
 * Provides traceability from compliance evidence to concrete implementation.
 */
data class EvidenceLink(
    val id: String,
    val evidenceId: ComplianceEvidenceId,
    val linkType: EvidenceLinkType,
    val targetReference: String,
    val description: String? = null,
    val linkedBy: String,
    val linkedAt: Instant = Instant.now(),
    val version: Long = 1,
)

/**
 * Type of artifact linked to compliance evidence.
 */
enum class EvidenceLinkType {
    /** Link to source code file or module (e.g., repo path, GitHub URL) */
    CODE,

    /** Link to test file or test suite (e.g., test class, BDD feature) */
    TEST,

    /** Link to documentation (e.g., markdown file, ADR, runbook) */
    DOCUMENT,

    /** Link to operational record (e.g., CI run, deployment log, audit trail) */
    OPERATIONAL_RECORD,

    /** Link to external artifact (e.g., third-party audit report, certificate) */
    EXTERNAL,
}
