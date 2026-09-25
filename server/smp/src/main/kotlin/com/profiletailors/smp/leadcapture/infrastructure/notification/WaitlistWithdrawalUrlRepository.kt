package com.profiletailors.smp.leadcapture.infrastructure.notification

import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import java.time.Instant

interface WaitlistWithdrawalUrlRepository {
    /** Inserts withdrawal material or replaces the material already stored for [entryId]. */
    suspend fun save(entryId: WaitlistEntryId, ciphertextVersion: String, ciphertext: String, expiresAt: Instant)

    /** Returns the stored material for [entryId], or `null` if none exists. */
    suspend fun findByEntryId(entryId: WaitlistEntryId): StoredWithdrawalUrl?

    suspend fun deleteByEntryId(entryId: WaitlistEntryId)

    data class StoredWithdrawalUrl(val ciphertextVersion: String, val ciphertext: String, val expiresAt: Instant)
}
