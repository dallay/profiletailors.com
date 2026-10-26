package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.Service
import com.profiletailors.smp.governance.domain.ConsentRecord
import com.profiletailors.smp.governance.domain.ConsentRepository
import java.time.Clock

class ConsentRecordNotFoundException(message: String) : IllegalStateException(message)

@Service
internal class WithdrawConsentHandler(
    private val repository: ConsentRepository,
    private val clock: Clock = Clock.systemUTC(),
) {

    suspend fun handle(command: WithdrawConsentCommand): ConsentRecord {
        require(command.purpose.isNotBlank()) { "purpose must not be blank" }

        return repository.withdrawActiveReturning(
            scope = command.scope,
            subjectReference = command.subjectReference,
            purpose = command.purpose,
            policyVersion = command.policyVersion,
            withdrawnAt = clock.instant(),
            reason = command.reason,
        ) ?: throw ConsentRecordNotFoundException(
            buildString {
                append("Active consent record not found for ")
                append("scope=${command.scope}, ")
                append("purpose=${command.purpose}, ")
                append("policyVersion=${command.policyVersion}")
            },
        )
    }
}
