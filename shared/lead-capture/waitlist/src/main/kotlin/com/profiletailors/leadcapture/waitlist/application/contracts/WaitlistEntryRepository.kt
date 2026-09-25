package com.profiletailors.leadcapture.waitlist.application.contracts

import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntry
import com.profiletailors.leadcapture.waitlist.domain.WaitlistId

interface WaitlistEntryRepository {
    fun findByNormalizedEmail(waitlistId: WaitlistId, email: NormalizedEmail): WaitlistEntry?
    fun save(entry: WaitlistEntry): WaitlistEntry

    /**
     * Saves [entry] and its [withdrawalToken] only if its waitlist has no entry with the same
     * normalized email; otherwise returns the existing entry without replacing its token.
     */
    suspend fun saveIfNotExists(entry: WaitlistEntry, withdrawalToken: WithdrawalToken): SaveResult

    /**
     * Consumes a matching unused token that expires after [now] and cancels its non-converted entry.
     * The update anonymizes the email and metadata and clears marketing consent. Returns the
     * updated entry, or `null` when the token cannot be used.
     */
    suspend fun withdrawByToken(token: String, now: java.time.Instant): WaitlistEntry?

    data class WithdrawalToken(val candidate: String, val hash: String, val expiresAt: java.time.Instant)

    sealed interface SaveResult {
        data class Saved(val entry: WaitlistEntry) : SaveResult
        data class AlreadyExists(val existing: WaitlistEntry) : SaveResult
    }
}
