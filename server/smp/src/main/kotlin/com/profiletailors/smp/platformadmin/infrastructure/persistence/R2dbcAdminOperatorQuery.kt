package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.platformadmin.application.model.AdminOperatorSummary
import com.profiletailors.smp.platformadmin.application.ports.AdminOperatorQuery
import com.profiletailors.smp.platformadmin.application.ports.PlatformRoleAssignmentRepository
import org.springframework.stereotype.Repository

@Repository
class R2dbcAdminOperatorQuery(
    private val roleAssignmentRepository: PlatformRoleAssignmentRepository,
    private val principalIdentityLookup: PrincipalIdentityLookup,
) : AdminOperatorQuery {

    override suspend fun listAllActive(): List<AdminOperatorSummary> {
        val allAssignments = roleAssignmentRepository.findAllActive()
        return allAssignments
            .groupBy { it.principalId }
            .map { (principalId, assignments) ->
                val identity = principalIdentityLookup.findByPrincipalId(principalId.toString())
                AdminOperatorSummary(
                    principalId = principalId,
                    email = identity?.email ?: "",
                    displayName = identity?.displayIdentity,
                    platformRoles = assignments.map { it.role.name },
                    assignedAt = assignments.minOf { it.assignedAt },
                )
            }
    }
}
