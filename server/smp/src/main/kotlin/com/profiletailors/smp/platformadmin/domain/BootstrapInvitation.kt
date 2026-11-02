package com.profiletailors.smp.platformadmin.domain

import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

enum class BootstrapInvitationStatus {
    PENDING,
    CLAIMED,
    COMPLETED,
}

data class BootstrapInvitation(
    val email: String,
    val tokenHash: String,
    val expiresAt: Instant,
    val createdAt: Instant,
    val status: BootstrapInvitationStatus = BootstrapInvitationStatus.PENDING,
    val claimedPrincipalId: UUID? = null,
    val claimedAt: Instant? = null,
    val completedAt: Instant? = null,
) {
    init {
        require(email.isNotBlank() && email == email.trim().lowercase())
        require(tokenHash.isNotBlank())
        require(status == BootstrapInvitationStatus.PENDING == (claimedPrincipalId == null))
        require(status == BootstrapInvitationStatus.PENDING == (claimedAt == null))
        require(status == BootstrapInvitationStatus.COMPLETED == (completedAt != null))
    }

    fun canClaim(at: Instant): Boolean = status == BootstrapInvitationStatus.PENDING && at.isBefore(expiresAt)

    fun claim(principalId: UUID, at: Instant): BootstrapInvitation {
        check(canClaim(at))
        return copy(
            status = BootstrapInvitationStatus.CLAIMED,
            claimedPrincipalId = principalId,
            claimedAt = at,
        )
    }

    fun complete(principalId: UUID, at: Instant): BootstrapInvitation {
        check(status == BootstrapInvitationStatus.CLAIMED && claimedPrincipalId == principalId)
        return copy(status = BootstrapInvitationStatus.COMPLETED, completedAt = at)
    }
}

interface BootstrapInvitationRepository {
    suspend fun lock()
    suspend fun hasActiveOwner(): Boolean
    suspend fun findCurrent(): BootstrapInvitation?
    suspend fun replace(invitation: BootstrapInvitation)
    suspend fun findByTokenHash(tokenHash: String): BootstrapInvitation?
    suspend fun claim(tokenHash: String, principalId: UUID, now: Instant): Boolean
    suspend fun findClaimedByPrincipalId(principalId: UUID): BootstrapInvitation?
    suspend fun complete(principalId: UUID, now: Instant): Boolean
}

fun interface BootstrapTokenGenerator {
    fun generate(): BootstrapToken
}

data class BootstrapToken(val rawToken: String, val tokenHash: String)

object BootstrapTokenDigest {
    fun hash(rawToken: String): String = MessageDigest.getInstance("SHA-256")
        .digest(rawToken.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

class ActiveBootstrapInvitationException : IllegalStateException("A pending bootstrap invitation requires confirmation")

class ExistingBootstrapAccountException : IllegalStateException("The email address already belongs to an account")

class PlatformOwnerAlreadyExistsException : IllegalStateException("A platform owner already exists")

class BootstrapUnavailableException : IllegalStateException("Bootstrap is unavailable")

class StaleBootstrapTermsException : IllegalArgumentException("The current Terms version must be accepted")
