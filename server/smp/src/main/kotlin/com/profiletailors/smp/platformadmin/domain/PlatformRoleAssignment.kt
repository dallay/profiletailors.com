package com.profiletailors.smp.platformadmin.domain

import com.profiletailors.common.domain.AggregateRoot
import java.time.Instant
import java.util.UUID

@AggregateRoot
data class PlatformRoleAssignment(
    val id: PlatformRoleAssignmentId,
    val principalId: UUID,
    val role: PlatformRole,
    val assignedAt: Instant,
    val assignedBy: UUID?,
    val actorKind: AdminActorKind = AdminActorKind.HUMAN,
    val revokedAt: Instant? = null,
    val revokedBy: UUID? = null,
    val version: Long = 0,
) {
    init {
        when (actorKind) {
            AdminActorKind.HUMAN -> require(assignedBy != null)
            AdminActorKind.SYSTEM_BOOTSTRAP -> require(assignedBy == null && role == PlatformRole.PLATFORM_OWNER)
        }
    }

    val isActive: Boolean get() = revokedAt == null

    fun revoke(at: Instant, by: UUID): PlatformRoleAssignment {
        if (!isActive) throw PlatformRoleAlreadyRevokedException(id.value.toString())
        return copy(revokedAt = at, revokedBy = by)
    }
}
