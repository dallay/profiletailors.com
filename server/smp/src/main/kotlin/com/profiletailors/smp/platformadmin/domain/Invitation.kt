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

@ValueObject
enum class InvitationTarget {
    EXISTING_WORKSPACE,
    NEW_WORKSPACE,
}

@AggregateRoot
data class Invitation(
    val id: InvitationId,
    val source: InvitationSource,
    val sourceReferenceId: String?,
    val target: InvitationTarget,
    val workspaceId: String?,
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
        when (target) {
            InvitationTarget.EXISTING_WORKSPACE -> require(!workspaceId.isNullOrBlank()) {
                "EXISTING_WORKSPACE invitation requires workspaceId"
            }
            InvitationTarget.NEW_WORKSPACE -> when (status) {
                InvitationStatus.ACTIVE,
                InvitationStatus.EXPIRED,
                InvitationStatus.REVOKED,
                -> require(workspaceId == null) {
                    "NEW_WORKSPACE invitation must have null workspaceId until accepted"
                }
                InvitationStatus.ACCEPTED -> require(!workspaceId.isNullOrBlank()) {
                    "ACCEPTED NEW_WORKSPACE invitation requires workspaceId"
                }
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

    fun accept(at: Instant, principalId: String, resolvedWorkspaceId: String? = null): Invitation {
        if (!isActive(at)) {
            throw InvitationNotAcceptableException(id.value.toString())
        }
        require(principalId.isNotBlank()) { "Accepted principal id must not be blank" }
        val resolvedWsId = when (target) {
            InvitationTarget.EXISTING_WORKSPACE -> workspaceId
            InvitationTarget.NEW_WORKSPACE -> resolvedWorkspaceId
        }
        require(!resolvedWsId.isNullOrBlank()) {
            "NEW_WORKSPACE acceptance requires resolvedWorkspaceId"
        }
        return copy(
            status = InvitationStatus.ACCEPTED,
            acceptedAt = at,
            acceptedPrincipalId = principalId,
            workspaceId = resolvedWsId,
            version = version + 1,
        )
    }

    fun expire(at: Instant): Invitation {
        if (status != InvitationStatus.ACTIVE || at.isBefore(expiresAt)) {
            throw InvitationNotExpirableException(id.value.toString())
        }
        return copy(status = InvitationStatus.EXPIRED, version = version + 1)
    }

    fun revoke(): Invitation {
        if (status != InvitationStatus.ACTIVE) {
            throw InvitationNotRevocableException(id.value.toString())
        }
        return copy(status = InvitationStatus.REVOKED, version = version + 1)
    }
}
