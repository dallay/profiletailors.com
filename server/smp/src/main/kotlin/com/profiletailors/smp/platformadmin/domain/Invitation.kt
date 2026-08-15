package com.profiletailors.smp.platformadmin.domain

import java.time.Instant

/** Semantic lifecycle of a first-class invitation. Delivery state is deliberately separate. */
enum class InvitationStatus {
    ACTIVE,
    ACCEPTED,
    EXPIRED,
    REVOKED,
}

enum class InvitationSource {
    DIRECT,
    WAITLIST,
}

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
        }
    }

    fun isExpired(now: Instant): Boolean = !now.isBefore(expiresAt)

    fun isActive(now: Instant): Boolean = status == InvitationStatus.ACTIVE && !isExpired(now)

    fun accept(at: Instant, principalId: String): Invitation {
        if (!isActive(at)) {
            throw InvitationNotAcceptableException(id.value.toString())
        }
        require(principalId.isNotBlank()) { "Accepted principal id must not be blank" }
        return copy(
            status = InvitationStatus.ACCEPTED,
            acceptedAt = at,
            acceptedPrincipalId = principalId,
        )
    }
}
