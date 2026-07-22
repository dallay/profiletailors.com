package com.profiletailors.smp.governance.domain

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ComplianceEvidenceModelsTest {

    @Test
    fun `creates evidence with pending status by default`() {
        val evidence = ComplianceEvidence(
            id = ComplianceEvidenceId("ev-001"),
            evidenceType = "POLICY_DOCUMENT",
            title = "Data Retention Policy",
            submittedBy = "system",
        )

        assertEquals(EvidenceReviewStatus.PENDING, evidence.reviewStatus)
        assertNull(evidence.expiresAt)
    }

    @Test
    fun `creates control-evidence immutable link`() {
        val link = ComplianceControlEvidence(
            id = "ctrlev-001",
            controlId = ComplianceControlId("ctrl-001"),
            evidenceId = ComplianceEvidenceId("ev-001"),
            linkedBy = "admin",
        )

        assertEquals("ctrl-001", link.controlId.value)
        assertEquals("ev-001", link.evidenceId.value)
        assertEquals("admin", link.linkedBy)
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

        assertEquals(EvidenceReviewStatus.APPROVED, evidence.reviewStatus)
        assertEquals("compliance-lead", evidence.reviewedBy)
    }

    @Test
    fun `evidence has no scheduled review date by default`() {
        val evidence = ComplianceEvidence(
            id = ComplianceEvidenceId("ev-003"),
            evidenceType = "AUDIT_REPORT",
            title = "Annual Audit",
            submittedBy = "auditor",
        )

        assertNull(evidence.reviewAt)
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

        assertEquals(reviewAt, evidence.reviewAt)
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

        assertEquals(1L, link.version)
        assertNull(link.description)
        assertEquals(EvidenceLinkType.CODE, link.linkType)
        assertEquals("ev-001", link.evidenceId.value)
        assertNotNull(link.linkedAt)
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

        assertEquals("BDD coverage for the release gate", link.description)
        assertEquals(EvidenceLinkType.TEST, link.linkType)
    }

    @Test
    fun `evidence link type has exactly the five supported artifact kinds`() {
        assertEquals(
            setOf("CODE", "TEST", "DOCUMENT", "OPERATIONAL_RECORD", "EXTERNAL"),
            EvidenceLinkType.entries.map { it.name }.toSet(),
        )
    }
}
