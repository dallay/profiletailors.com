package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandHandler
import com.profiletailors.smp.governance.domain.ComplianceEvidence
import com.profiletailors.smp.governance.domain.ComplianceEvidenceId
import com.profiletailors.smp.governance.domain.ComplianceEvidenceRepository
import java.util.UUID

@Service
internal class RegisterComplianceEvidenceHandler(private val repository: ComplianceEvidenceRepository) :
    CommandHandler<RegisterComplianceEvidenceCommand> {

    override suspend fun handle(command: RegisterComplianceEvidenceCommand) {
        val evidence = ComplianceEvidence(
            id = ComplianceEvidenceId("ev-${UUID.randomUUID()}"),
            evidenceType = command.evidenceType,
            title = command.title,
            description = command.description,
            referenceUrl = command.referenceUrl,
            immutableReference = command.immutableReference,
            checksum = command.checksum,
            metadataJson = command.metadataJson,
            submittedBy = command.submittedBy,
        )
        repository.save(evidence)
    }
}
