package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandHandler
import com.profiletailors.smp.governance.domain.ComplianceRiskAcceptance
import com.profiletailors.smp.governance.domain.ComplianceRiskAcceptanceId
import com.profiletailors.smp.governance.domain.ComplianceRiskAcceptanceRepository
import java.util.UUID

@Service
internal class RecordRiskAcceptanceHandler(private val repository: ComplianceRiskAcceptanceRepository) :
    CommandHandler<RecordRiskAcceptanceCommand> {

    override suspend fun handle(command: RecordRiskAcceptanceCommand) {
        val riskAcceptance = ComplianceRiskAcceptance(
            id = ComplianceRiskAcceptanceId("ra-${UUID.randomUUID()}"),
            controlId = command.controlId,
            releaseScope = command.releaseScope,
            marketScope = command.marketScope,
            environmentScope = command.environmentScope,
            providerScope = command.providerScope,
            productScope = command.productScope,
            workspaceScope = command.workspaceScope,
            riskSummary = command.riskSummary,
            residualRisk = command.residualRisk,
            justification = command.justification,
            requestedBy = command.requestedBy,
            expiresAt = command.expiresAt,
        )
        repository.save(riskAcceptance)
    }
}
