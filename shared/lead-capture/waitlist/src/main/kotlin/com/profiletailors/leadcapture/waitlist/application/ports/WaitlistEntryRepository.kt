package com.profiletailors.leadcapture.waitlist.application.ports

import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntry
import com.profiletailors.leadcapture.waitlist.domain.WaitlistId

interface WaitlistEntryRepository {
    fun findByNormalizedEmail(waitlistId: WaitlistId, email: NormalizedEmail): WaitlistEntry?
    fun save(entry: WaitlistEntry): WaitlistEntry
    fun saveIfNotExists(entry: WaitlistEntry): SaveResult

    sealed interface SaveResult {
        data class Saved(val entry: WaitlistEntry) : SaveResult
        data class AlreadyExists(val existing: WaitlistEntry) : SaveResult
    }
}
