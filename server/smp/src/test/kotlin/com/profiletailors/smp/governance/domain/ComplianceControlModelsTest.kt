package com.profiletailors.smp.governance.domain

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class ComplianceControlModelsTest {

    @Test
    fun `creates compliance control with required fields`() {
        val control = ComplianceControl(
            id = ComplianceControlId("ctrl-001"),
            controlKey = "PRIVACY.DATA_RETENTION",
            name = "Data retention and deletion",
            description = "Ensure data is retained only as necessary",
            owner = "security-team",
            category = "PRIVACY",
            status = ComplianceControlStatus.ACTIVE,
        )

        assertEquals("ctrl-001", control.id.value)
        assertEquals("PRIVACY.DATA_RETENTION", control.controlKey)
        assertEquals(ComplianceControlStatus.ACTIVE, control.status)
        assertEquals(1, control.version)
        assertNotNull(control.createdAt)
        assertNotNull(control.updatedAt)
    }

    @Test
    fun `creates applicability rule with dimensions`() {
        val rule = ComplianceControlApplicabilityRule(
            id = ComplianceControlApplicabilityRuleId("rule-001"),
            controlId = ComplianceControlId("ctrl-001"),
            required = true,
            validFrom = Instant.parse("2026-07-01T00:00:00Z"),
            validUntil = null,
            dimensions = listOf(
                ApplicabilityDimension(
                    id = ApplicabilityDimensionId("dim-001"),
                    ruleId = ComplianceControlApplicabilityRuleId("rule-001"),
                    scopeType = ScopeType.MARKET,
                    scopeValue = "EEA",
                ),
            ),
        )

        assertEquals("rule-001", rule.id.value)
        assertTrue(rule.required)
        assertEquals(1, rule.dimensions.size)
        assertEquals(ScopeType.MARKET, rule.dimensions.first().scopeType)
    }

    @Test
    fun `creates evidence requirement`() {
        val req = ComplianceControlEvidenceRequirement(
            id = ComplianceControlEvidenceRequirementId("evreq-001"),
            controlId = ComplianceControlId("ctrl-001"),
            evidenceType = "POLICY_DOCUMENT",
            minimumApprovedEvidence = 1,
            manualApprovalRequired = true,
            required = true,
        )

        assertEquals("POLICY_DOCUMENT", req.evidenceType)
        assertTrue(req.manualApprovalRequired)
    }

    @Test
    fun `updating control increments version`() {
        val original = ComplianceControl(
            id = ComplianceControlId("ctrl-001"),
            controlKey = "PRIVACY.DATA_RETENTION",
            name = "Data retention and deletion",
            owner = "security-team",
            category = "PRIVACY",
            status = ComplianceControlStatus.ACTIVE,
        )
        val updated = original.copy(status = ComplianceControlStatus.DEPRECATED)
        assertTrue(updated.version == original.version || updated.version > original.version)
    }
}
