package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.smp.governance.domain.ComplianceControlId
import java.time.Instant

data class RecordRiskAcceptanceCommand(
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
    val expiresAt: Instant,
) : Command
