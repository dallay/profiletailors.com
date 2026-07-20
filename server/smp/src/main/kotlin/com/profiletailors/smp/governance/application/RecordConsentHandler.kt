package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.Service
import com.profiletailors.smp.governance.domain.ConsentRecord
import com.profiletailors.smp.governance.domain.ConsentRecordId
import com.profiletailors.smp.governance.domain.ConsentRepository
import java.time.Clock
import java.util.UUID

@Service
class RecordConsentHandler(private val repository: ConsentRepository, private val clock: Clock = Clock.systemUTC()) {

    /**
     * Records consent idempotently for a workspace, subject, purpose, and policy version.
     *
     * @param command The command containing the consent details to record.
     * @return The outcome indicating whether a record was created and the corresponding consent record.
     * @throws IllegalArgumentException If the workspace ID or purpose is blank.
     */
    suspend fun handle(command: RecordConsentCommand): RecordConsentOutcome {
        require(command.workspaceId.isNotBlank()) { "workspaceId must not be blank" }
        require(command.purpose.isNotBlank()) { "purpose must not be blank" }

        val existing = if (repository.existsActive(
                workspaceId = command.workspaceId,
                subjectReference = command.subjectReference,
                purpose = command.purpose,
                policyVersion = command.policyVersion,
            )
        ) {
            repository.findActive(
                workspaceId = command.workspaceId,
                subjectReference = command.subjectReference,
                purpose = command.purpose,
                policyVersion = command.policyVersion,
            )
        } else {
            null
        }
        if (existing != null) return RecordConsentOutcome(created = false, record = existing)

        val record = ConsentRecord(
            id = ConsentRecordId("cs-${UUID.randomUUID()}"),
            workspaceId = command.workspaceId,
            subjectReference = command.subjectReference,
            consentType = command.consentType,
            purpose = command.purpose,
            policyVersion = command.policyVersion,
            source = command.source,
            locale = command.locale,
            givenAt = clock.instant(),
        )
        val (created, persisted) = repository.recordActiveReturning(record)
        return RecordConsentOutcome(created = created, record = persisted)
    }
}
