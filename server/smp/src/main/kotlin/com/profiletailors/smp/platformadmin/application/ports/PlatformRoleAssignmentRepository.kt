package com.profiletailors.smp.platformadmin.application.ports

import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignment
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignmentId
import java.util.UUID

interface PlatformRoleAssignmentRepository {
    suspend fun findById(id: PlatformRoleAssignmentId): PlatformRoleAssignment?
    suspend fun findActiveByPrincipalId(principalId: UUID): List<PlatformRoleAssignment>
    suspend fun findAllActive(): List<PlatformRoleAssignment>
    suspend fun save(assignment: PlatformRoleAssignment): PlatformRoleAssignment
    suspend fun update(assignment: PlatformRoleAssignment): PlatformRoleAssignment
}
