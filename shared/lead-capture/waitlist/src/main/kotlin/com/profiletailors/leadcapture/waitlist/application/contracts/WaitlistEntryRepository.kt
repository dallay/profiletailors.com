package com.profiletailors.leadcapture.waitlist.application.contracts

import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntry
import com.profiletailors.leadcapture.waitlist.domain.WaitlistId
import java.time.Instant

interface WaitlistEntryRepository {
    fun findByNormalizedEmail(waitlistId: WaitlistId, email: NormalizedEmail): WaitlistEntry?
    fun save(entry: WaitlistEntry): WaitlistEntry
    suspend fun saveIfNotExists(entry: WaitlistEntry, withdrawalToken: WithdrawalToken): SaveResult

    suspend fun withdrawByToken(candidate: String, hash: String, now: Instant): WaitlistEntry?

    data class WithdrawalToken(val candidate: String, val hash: String, val expiresAt: Instant)

    sealed interface SaveResult {
        data class Saved(val entry: WaitlistEntry) : SaveResult
        data class AlreadyExists(val existing: WaitlistEntry) : SaveResult
    }
}
