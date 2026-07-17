package com.profiletailors.smp.governance.domain

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class ComplianceRiskAcceptanceModelsTest {

    @Test
    fun `creates active risk acceptance with mandatory fields`() {
        val ra = ComplianceRiskAcceptance(
            id = ComplianceRiskAcceptanceId("ra-001"),
            controlId = ComplianceControlId("ctrl-001"),
            riskSummary = "Accept delay in DPA execution for MVP release",
            requestedBy = "product-manager",
            expiresAt = Instant.parse("2026-12-31T23:59:59Z"),
        )

        assertEquals(RiskAcceptanceStatus.ACTIVE, ra.status)
        assertNull(ra.acceptedBy)
    }

    @Test
    fun `scope fields default to null which means wildcard`() {
        val ra = ComplianceRiskAcceptance(
            id = ComplianceRiskAcceptanceId("ra-002"),
            controlId = ComplianceControlId("ctrl-001"),
            riskSummary = "Accept risk for all markets",
            requestedBy = "product-manager",
            expiresAt = Instant.parse("2026-12-31T23:59:59Z"),
        )

        assertNull(ra.marketScope)
    }
}
