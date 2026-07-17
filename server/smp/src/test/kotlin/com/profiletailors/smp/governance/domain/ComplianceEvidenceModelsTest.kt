package com.profiletailors.smp.governance.domain

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
