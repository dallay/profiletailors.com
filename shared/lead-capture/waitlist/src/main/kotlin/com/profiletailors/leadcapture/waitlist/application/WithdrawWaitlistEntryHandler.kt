package com.profiletailors.leadcapture.waitlist.application

import com.profiletailors.leadcapture.waitlist.application.contracts.WaitlistEntryRepository
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import java.time.Instant

class WithdrawWaitlistEntryHandler(
    private val repository: WaitlistEntryRepository,
    private val clock: () -> Instant = Instant::now,
) {
    suspend fun handle(token: String): WithdrawResult? {
        require(token.isNotBlank()) { "Withdrawal token must not be blank" }
        val fingerprint = WaitlistWithdrawalTokenIssuer.fingerprint(token)
        val entry = repository.withdrawByToken(fingerprint.candidate, fingerprint.hash, clock())
        return entry?.let { WithdrawResult(it.id) }
    }
}

data class WithdrawResult(val entryId: WaitlistEntryId)
