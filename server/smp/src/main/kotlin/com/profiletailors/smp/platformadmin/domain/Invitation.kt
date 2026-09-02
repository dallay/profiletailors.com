package com.profiletailors.smp.platformadmin.domain

import com.profiletailors.common.domain.AggregateRoot
import com.profiletailors.common.domain.ValueObject
import java.time.Instant

@ValueObject
enum class InvitationStatus {
    ACTIVE,
    ACCEPTED,
    EXPIRED,
    REVOKED,
}

@ValueObject
enum class InvitationSource {
    DIRECT,
    WAITLIST,
}

@AggregateRoot
data class Invitation(
    val id: InvitationId,
    val source: InvitationSource,
    val sourceReferenceId: String?,
    val workspaceId: String,
    val invitedEmailNormalized: String,
    val tokenHash: String,
    val status: InvitationStatus,
    val issuedBy: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val acceptedAt: Instant? = null,
    val acceptedPrincipalId: String? = null,
    val version: Long = 0,
) {
    init {
        require(workspaceId.isNotBlank()) { "Invitation workspaceId must not be blank" }
        require(invitedEmailNormalized == invitedEmailNormalized.trim().lowercase()) {
            "Invitation email must be normalized"
        }
        require(invitedEmailNormalized.isNotBlank()) { "Invitation email must not be blank" }
        require(tokenHash.isNotBlank()) { "Invitation token hash must not be blank" }
        require(issuedBy.isNotBlank()) { "Invitation issuer must not be blank" }
        require(expiresAt.isAfter(createdAt)) { "Invitation expiration must be after creation" }
        require(version >= 0) { "Invitation version must not be negative" }
        when (source) {
            InvitationSource.DIRECT -> require(sourceReferenceId == null) {
                "Direct invitations must not have a waitlist source reference"
            }
            InvitationSource.WAITLIST -> require(!sourceReferenceId.isNullOrBlank()) {
                "Waitlist invitations require a waitlist source reference"
            }
        }
        if (status == InvitationStatus.ACCEPTED) {
            require(acceptedAt != null) { "Accepted invitations require acceptedAt" }
            require(!acceptedPrincipalId.isNullOrBlank()) { "Accepted invitations require acceptedPrincipalId" }
        } else {
            require(acceptedAt == null) { "Non-accepted invitations must not have acceptedAt" }
            require(acceptedPrincipalId == null) {
                "Non-accepted invitations must not have acceptedPrincipalId"
            }
        }
    }

    fun isExpired(now: Instant): Boolean = !now.isBefore(expiresAt)

    fun isActive(now: Instant): Boolean = status == InvitationStatus.ACTIVE && !isExpired(now)

    /**
     * Accepts the invitation for a principal at the specified time.
     *
     * @param at The time at which the invitation is accepted.
     * @param principalId The identifier of the principal accepting the invitation.
     * @return The accepted invitation with acceptance metadata and an incremented version.
     * @throws InvitationNotAcceptableException If the invitation is not active at the specified time.
     * @throws IllegalArgumentException If the principal ID is blank.
     */
    fun accept(at: Instant, principalId: String): Invitation {
        if (!isActive(at)) {
            throw InvitationNotAcceptableException(id.value.toString())
        }
        require(principalId.isNotBlank()) { "Accepted principal id must not be blank" }
        return copy(
            status = InvitationStatus.ACCEPTED,
            acceptedAt = at,
            acceptedPrincipalId = principalId,
            version = version + 1,
        )
    }

    /**
     * Expires the invitation when it is active and its expiration time has been reached.
     *
     * @param at The time at which expiration is evaluated.
     * @return A copy of the invitation with expired status and an incremented version.
     * @throws InvitationNotExpirableException If the invitation is not active or has not reached its expiration time.
     */
    fun expire(at: Instant): Invitation {
        if (status != InvitationStatus.ACTIVE || at.isBefore(expiresAt)) {
            throw InvitationNotExpirableException(id.value.toString())
        }
        return copy(status = InvitationStatus.EXPIRED, version = version + 1)
    }

    /**
     * Revokes this invitation.
     *
     * @return A copy of the invitation with revoked status and an incremented version.
     * @throws InvitationNotRevocableException If the invitation is not active.
     */
    fun revoke(): Invitation {
        if (status != InvitationStatus.ACTIVE) {
            throw InvitationNotRevocableException(id.value.toString())
        }
        return copy(status = InvitationStatus.REVOKED, version = version + 1)
    }
}
