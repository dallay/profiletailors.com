package com.profiletailors.leadcapture.waitlist.application.ports

import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntry
import com.profiletailors.leadcapture.waitlist.domain.WaitlistId

interface WaitlistEntryRepository {
    fun findByNormalizedEmail(waitlistId: WaitlistId, email: NormalizedEmail): WaitlistEntry?
    fun save(entry: WaitlistEntry): WaitlistEntry

    /**
     * Atomically saves the entry only if no entry exists for the same
     * (waitlistId, normalizedEmail) key. Returns the saved entry on success
     * or null if a conflicting entry already exists.
     */
    fun saveIfNotExists(entry: WaitlistEntry): WaitlistEntry?
}
