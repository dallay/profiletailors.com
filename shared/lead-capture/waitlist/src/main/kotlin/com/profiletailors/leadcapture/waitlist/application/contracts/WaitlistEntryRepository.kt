package com.profiletailors.leadcapture.waitlist.application.contracts

import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntry
import com.profiletailors.leadcapture.waitlist.domain.WaitlistId

interface WaitlistEntryRepository {
    fun findByNormalizedEmail(waitlistId: WaitlistId, email: NormalizedEmail): WaitlistEntry?
    fun save(entry: WaitlistEntry): WaitlistEntry
    suspend fun saveIfNotExists(entry: WaitlistEntry, withdrawalToken: WithdrawalToken): SaveResult

    suspend fun withdrawByToken(token: String, now: java.time.Instant): WaitlistEntry?

    data class WithdrawalToken(val candidate: String, val hash: String, val expiresAt: java.time.Instant)

    sealed interface SaveResult {
        data class Saved(val entry: WaitlistEntry) : SaveResult
        data class AlreadyExists(val existing: WaitlistEntry) : SaveResult
    }
}
