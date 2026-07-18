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

    /**
     * Withdraws an active consent while preserving the original record identity and given timestamp.
     *
     * @param command The withdrawal command.
     * @return The persisted withdrawn record.
     */
    suspend fun handle(command: WithdrawConsentCommand): ConsentRecord {
        require(command.workspaceId.isNotBlank()) { "workspaceId must not be blank" }
        require(command.purpose.isNotBlank()) { "purpose must not be blank" }

        val existing = repository.findActive(
            workspaceId = command.workspaceId,
            subjectReference = command.subjectReference,
            purpose = command.purpose,
            policyVersion = command.policyVersion,
        ) ?: throw ConsentRecordNotFoundException(
            buildString {
                append("Active consent record not found for ")
                append("workspaceId=${command.workspaceId}, ")
                append("purpose=${command.purpose}, ")
                append("policyVersion=${command.policyVersion}")
            },
        )

        return repository.save(existing.withdraw(at = clock.instant(), reason = command.reason))
    }
}
