package com.profiletailors.smp.governance.domain

import java.time.Instant

data class ComplianceRiskAcceptance(
    val id: ComplianceRiskAcceptanceId,
    val controlId: ComplianceControlId,
    val releaseScope: String? = null,
    val marketScope: String? = null,
    val environmentScope: String? = null,
    val providerScope: String? = null,
    val productScope: String? = null,
    val workspaceScope: String? = null,
    val riskSummary: String,
    val residualRisk: String? = null,
    val justification: String? = null,
    val requestedBy: String,
    val acceptedBy: String? = null,
    val acceptedAt: Instant? = null,
    val expiresAt: Instant,
    val revokedAt: Instant? = null,
    val status: RiskAcceptanceStatus = RiskAcceptanceStatus.ACTIVE,
    val version: Long = 1,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
