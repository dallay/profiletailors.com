package com.profiletailors.smp.platformadmin.domain

import java.time.Instant
import java.util.UUID

data class WaitlistInvitation(
    val id: WaitlistInvitationId,
    val waitlistEntryId: String,
    val tokenHash: String,
    val status: WaitlistInvitationStatus,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val acceptedAt: Instant? = null,
    val revokedAt: Instant? = null,
    val revokedBy: UUID? = null,
    val createdBy: UUID,
    val deliveryStatus: InvitationDeliveryStatus,
    val lastDeliveryAttemptAt: Instant? = null,
    val deliveryAttemptCount: Int = 0,
    val version: Long = 0,
) {
    val isActive: Boolean get() = status == WaitlistInvitationStatus.ACTIVE

    fun isExpired(now: Instant): Boolean = now >= expiresAt

    fun revoke(at: Instant, by: UUID): WaitlistInvitation {
        check(status == WaitlistInvitationStatus.ACTIVE) { "Only active invitations can be revoked." }
        return copy(status = WaitlistInvitationStatus.REVOKED, revokedAt = at, revokedBy = by)
    }

    fun supersede(): WaitlistInvitation {
        check(status == WaitlistInvitationStatus.ACTIVE) { "Only active invitations can be superseded." }
        return copy(status = WaitlistInvitationStatus.SUPERSEDED)
    }

    fun accept(at: Instant): WaitlistInvitation {
        check(status == WaitlistInvitationStatus.ACTIVE) { "Only active invitations can be accepted." }
        return copy(status = WaitlistInvitationStatus.ACCEPTED, acceptedAt = at)
    }
}
