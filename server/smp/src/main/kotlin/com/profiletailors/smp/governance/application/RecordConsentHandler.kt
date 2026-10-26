package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.Service
import com.profiletailors.smp.governance.domain.ConsentRecord
import com.profiletailors.smp.governance.domain.ConsentRecordId
import com.profiletailors.smp.governance.domain.ConsentRepository
import java.time.Clock
import java.util.UUID

@Service
class RecordConsentHandler(private val repository: ConsentRepository, private val clock: Clock = Clock.systemUTC()) {

    suspend fun handle(command: RecordConsentCommand): RecordConsentOutcome {
        require(command.purpose.isNotBlank()) { "purpose must not be blank" }

        val existing = repository.findActive(
            scope = command.scope,
            subjectReference = command.subjectReference,
            purpose = command.purpose,
            policyVersion = command.policyVersion,
        )
        if (existing != null) return RecordConsentOutcome(created = false, record = existing)

        val record = ConsentRecord(
            id = ConsentRecordId(UUID.randomUUID().toString()),
            scope = command.scope,
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
