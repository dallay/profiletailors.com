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
    fun isActive(now: Instant): Boolean = status == WaitlistInvitationStatus.ACTIVE && !isExpired(now)

    fun isExpired(now: Instant): Boolean = now >= expiresAt

    fun revoke(at: Instant, by: UUID): WaitlistInvitation {
        if (status != WaitlistInvitationStatus.ACTIVE) {
            throw InvitationNotRevocableException(id.value.toString())
        }
        return copy(status = WaitlistInvitationStatus.REVOKED, revokedAt = at, revokedBy = by)
    }

    fun supersede(): WaitlistInvitation {
        if (status != WaitlistInvitationStatus.ACTIVE) {
            throw InvitationNotResendableException(id.value.toString())
        }
        return copy(status = WaitlistInvitationStatus.SUPERSEDED)
    }

    fun accept(at: Instant): WaitlistInvitation {
        if (status != WaitlistInvitationStatus.ACTIVE) {
            throw InvitationNotAcceptableException(id.value.toString())
        }
        return copy(status = WaitlistInvitationStatus.ACCEPTED, acceptedAt = at)
    }
}
