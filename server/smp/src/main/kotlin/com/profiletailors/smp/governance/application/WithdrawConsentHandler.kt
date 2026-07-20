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
     * Withdraws an active consent record matching the command.
     *
     * @param command The command specifying the consent record and withdrawal reason.
     * @return The persisted withdrawn consent record.
     * @throws IllegalArgumentException If the workspace ID or purpose is blank.
     * @throws ConsentRecordNotFoundException If no matching active consent record exists.
     */
    suspend fun handle(command: WithdrawConsentCommand): ConsentRecord {
        require(command.workspaceId.isNotBlank()) { "workspaceId must not be blank" }
        require(command.purpose.isNotBlank()) { "purpose must not be blank" }

        return repository.withdrawActiveReturning(
            workspaceId = command.workspaceId,
            subjectReference = command.subjectReference,
            purpose = command.purpose,
            policyVersion = command.policyVersion,
            withdrawnAt = clock.instant(),
            reason = command.reason,
        ) ?: throw ConsentRecordNotFoundException(
            buildString {
                append("Active consent record not found for ")
                append("workspaceId=${command.workspaceId}, ")
                append("purpose=${command.purpose}, ")
                append("policyVersion=${command.policyVersion}")
            },
        )
    }
}
