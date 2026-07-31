package com.profiletailors.smp.platformadmin.application.ports

import com.profiletailors.smp.platformadmin.domain.WaitlistInvitation
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationId

interface WaitlistInvitationRepository {
    suspend fun findById(id: WaitlistInvitationId): WaitlistInvitation?
    suspend fun findActiveByWaitlistEntryId(waitlistEntryId: String): WaitlistInvitation?
    suspend fun findAllByWaitlistEntryId(waitlistEntryId: String): List<WaitlistInvitation>
    suspend fun findByTokenHash(tokenHash: String): WaitlistInvitation?
    suspend fun save(invitation: WaitlistInvitation): WaitlistInvitation
    suspend fun update(invitation: WaitlistInvitation): WaitlistInvitation
    suspend fun countResendsSince(waitlistEntryId: String, sinceEpochMillis: Long): Int
}
