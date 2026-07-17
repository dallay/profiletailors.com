package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandHandler
import com.profiletailors.smp.governance.domain.ComplianceControl
import com.profiletailors.smp.governance.domain.ComplianceControlId
import com.profiletailors.smp.governance.domain.ComplianceControlRepository
import java.util.UUID

@Service
internal class RegisterComplianceControlHandler(private val repository: ComplianceControlRepository) :
    CommandHandler<RegisterComplianceControlCommand> {

    override suspend fun handle(command: RegisterComplianceControlCommand) {
        val control = ComplianceControl(
            id = command.id ?: ComplianceControlId("ctrl-${UUID.randomUUID()}"),
            controlKey = command.controlKey,
            name = command.name,
            description = command.description,
            owner = command.owner,
            category = command.category,
            status = command.status,
        )
        repository.save(control)
    }
}
