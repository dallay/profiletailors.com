package com.profiletailors.leadcapture.waitlist.application

import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import java.time.Instant

interface WaitlistWithdrawalUrlProvider {
    /** Provides [token] and its expiry for later withdrawal URL resolution. */
    suspend fun remember(entryId: WaitlistEntryId, token: String, expiresAt: Instant)

    /** Returns a withdrawal URL when one is available for [entryId] at [now], or `null` otherwise. */
    suspend fun urlFor(entryId: WaitlistEntryId, now: Instant): String?

    /** Removes stored withdrawal material for [entryId]. */
    suspend fun forget(entryId: WaitlistEntryId)

    companion object {
        val noop: WaitlistWithdrawalUrlProvider = object : WaitlistWithdrawalUrlProvider {
            override suspend fun remember(entryId: WaitlistEntryId, token: String, expiresAt: Instant) = Unit

            override suspend fun urlFor(entryId: WaitlistEntryId, now: Instant): String? = null

            override suspend fun forget(entryId: WaitlistEntryId) = Unit
        }
    }
}
