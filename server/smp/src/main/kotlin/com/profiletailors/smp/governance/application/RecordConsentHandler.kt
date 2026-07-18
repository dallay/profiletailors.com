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
     * Records consent idempotently for the subject + purpose + policy version triple.
     *
     * @param command The consent record command.
     * @return The newly persisted record, or the existing active record for duplicate submissions.
     */
    suspend fun handle(command: RecordConsentCommand): ConsentRecord {
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
        if (existing != null) return existing

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
        return repository.save(record)
    }
}
