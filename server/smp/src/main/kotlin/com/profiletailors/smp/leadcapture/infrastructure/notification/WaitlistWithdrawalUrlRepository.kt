package com.profiletailors.smp.leadcapture.infrastructure.notification

import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import java.time.Instant

interface WaitlistWithdrawalUrlRepository {
    suspend fun save(entryId: WaitlistEntryId, ciphertextVersion: String, ciphertext: String, expiresAt: Instant)

    suspend fun findByEntryId(entryId: WaitlistEntryId): StoredWithdrawalUrl?

    suspend fun deleteByEntryId(entryId: WaitlistEntryId)

    data class StoredWithdrawalUrl(val ciphertextVersion: String, val ciphertext: String, val expiresAt: Instant)
}
