package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.smp.platformadmin.application.contracts.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import java.util.UUID

data class OperatorAccess(val principalId: UUID, val roles: Set<PlatformRole>)

@Service
class OperatorAccessResolver(private val roleAssignmentRepository: PlatformRoleAssignmentRepository) {
    /**
     * Resolves the active platform roles assigned to an operator.
     *
     * @param principal The principal whose access is being resolved.
     * @return The operator's principal ID and assigned platform roles.
     */
    suspend fun resolve(principal: PrincipalContext): OperatorAccess {
        val principalId = PlatformPrincipalIds.toUuid(principal.principalId)
        val assignments = roleAssignmentRepository.findActiveByPrincipalId(principalId)
        val roles = assignments.map { it.role }.toSet()
        return OperatorAccess(principalId, roles)
    }
}
