package com.profiletailors.smp.platformadmin.domain

import java.time.Instant
import java.util.UUID

data class PlatformRoleAssignment(
    val id: PlatformRoleAssignmentId,
    val principalId: UUID,
    val role: PlatformRole,
    val assignedAt: Instant,
    val assignedBy: UUID,
    val revokedAt: Instant? = null,
    val revokedBy: UUID? = null,
    val version: Long = 0,
) {
    val isActive: Boolean get() = revokedAt == null

    fun revoke(at: Instant, by: UUID): PlatformRoleAssignment {
        check(isActive) { "Role assignment is already revoked." }
        return copy(revokedAt = at, revokedBy = by)
    }
}
