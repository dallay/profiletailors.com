package com.profiletailors.smp.governance.domain

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

internal class ComplianceEvidenceModelsTest {

    @Test
    fun `creates evidence with pending status by default`() {
        val evidence = ComplianceEvidence(
            id = ComplianceEvidenceId("ev-001"),
            evidenceType = "POLICY_DOCUMENT",
            title = "Data Retention Policy",
            submittedBy = "system",
        )

        evidence.reviewStatus shouldBe EvidenceReviewStatus.PENDING
        evidence.expiresAt.shouldBeNull()
    }

    @Test
    fun `creates control-evidence immutable link`() {
        val link = ComplianceControlEvidence(
            id = "ctrlev-001",
            controlId = ComplianceControlId("ctrl-001"),
            evidenceId = ComplianceEvidenceId("ev-001"),
            linkedBy = "admin",
        )

        link.controlId.value shouldBe "ctrl-001"
        link.evidenceId.value shouldBe "ev-001"
        link.linkedBy shouldBe "admin"
    }

    @Test
    fun `approved evidence has review status and reviewer`() {
        val evidence = ComplianceEvidence(
            id = ComplianceEvidenceId("ev-002"),
            evidenceType = "SECURITY_REPORT",
            title = "SOC 2 Report",
            submittedBy = "auditor",
            reviewedBy = "compliance-lead",
            reviewStatus = EvidenceReviewStatus.APPROVED,
            verifiedAt = Instant.parse("2026-07-17T12:00:00Z"),
        )

        evidence.reviewStatus shouldBe EvidenceReviewStatus.APPROVED
        evidence.reviewedBy shouldBe "compliance-lead"
    }

    @Test
    fun `evidence has no scheduled review date by default`() {
        val evidence = ComplianceEvidence(
            id = ComplianceEvidenceId("ev-003"),
            evidenceType = "AUDIT_REPORT",
            title = "Annual Audit",
            submittedBy = "auditor",
        )

        evidence.reviewAt.shouldBeNull()
    }

    @Test
    fun `evidence retains an explicit scheduled review date`() {
        val reviewAt = Instant.parse("2027-01-01T00:00:00Z")
        val evidence = ComplianceEvidence(
            id = ComplianceEvidenceId("ev-004"),
            evidenceType = "AUDIT_REPORT",
            title = "Annual Audit",
            submittedBy = "auditor",
            reviewAt = reviewAt,
        )

        evidence.reviewAt shouldBe reviewAt
    }

    @Test
    fun `creates evidence link with default version and no description`() {
        val link = EvidenceLink(
            id = "evlink-001",
            evidenceId = ComplianceEvidenceId("ev-001"),
            linkType = EvidenceLinkType.CODE,
            targetReference = "server/smp/src/main/kotlin/com/profiletailors/smp/example/Example.kt",
            linkedBy = "engineer@example.com",
        )

        link.version shouldBe 1L
        link.description.shouldBeNull()
        link.linkType shouldBe EvidenceLinkType.CODE
        link.evidenceId.value shouldBe "ev-001"
        link.linkedAt.shouldNotBeNull()
    }

    @Test
    fun `creates evidence link with an explicit description`() {
        val link = EvidenceLink(
            id = "evlink-002",
            evidenceId = ComplianceEvidenceId("ev-001"),
            linkType = EvidenceLinkType.TEST,
            targetReference = "governance-compliance.feature",
            description = "BDD coverage for the release gate",
            linkedBy = "engineer@example.com",
        )

        link.description shouldBe "BDD coverage for the release gate"
        link.linkType shouldBe EvidenceLinkType.TEST
    }

    @Test
    fun `evidence link type has exactly the five supported artifact kinds`() {
        EvidenceLinkType.entries.map { it.name }.toSet() shouldBe
            setOf("CODE", "TEST", "DOCUMENT", "OPERATIONAL_RECORD", "EXTERNAL")
    }
}
