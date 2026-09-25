package com.profiletailors.leadcapture.waitlist.application

import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import java.time.Instant

interface WaitlistWithdrawalUrlProvider {
    suspend fun remember(entryId: WaitlistEntryId, token: String, expiresAt: Instant)

    suspend fun urlFor(entryId: WaitlistEntryId, now: Instant): String?

    suspend fun forget(entryId: WaitlistEntryId)

    companion object {
        val noop: WaitlistWithdrawalUrlProvider = object : WaitlistWithdrawalUrlProvider {
            override suspend fun remember(entryId: WaitlistEntryId, token: String, expiresAt: Instant) = Unit

            override suspend fun urlFor(entryId: WaitlistEntryId, now: Instant): String? = null

            override suspend fun forget(entryId: WaitlistEntryId) = Unit
        }
    }
}
