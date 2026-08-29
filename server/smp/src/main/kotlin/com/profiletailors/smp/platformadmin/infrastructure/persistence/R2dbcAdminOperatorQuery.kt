package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.platformadmin.application.PlatformPrincipalIds
import com.profiletailors.smp.platformadmin.application.contracts.AdminOperatorQuery
import com.profiletailors.smp.platformadmin.application.contracts.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.application.model.AdminOperatorSummary
import org.springframework.stereotype.Repository

@Repository
class R2dbcAdminOperatorQuery(
    private val roleAssignmentRepository: PlatformRoleAssignmentRepository,
    private val principalIdentityLookup: PrincipalIdentityLookup,
) : AdminOperatorQuery {

    /**
     * Retrieves summaries of all active platform role assignments grouped by principal.
     *
     * @return A list containing each principal's identity details, assigned platform roles,
     * and earliest assignment timestamp.
     */
    override suspend fun listAllActive(): List<AdminOperatorSummary> {
        val allAssignments = roleAssignmentRepository.findAllActive()
        return allAssignments
            .groupBy { it.principalId }
            .map { (principalId, assignments) ->
                val identity = principalIdentityLookup.findByPrincipalId(principalId.toString())
                    ?: principalIdentityLookup.findByPrincipalId(PlatformPrincipalIds.fromUuid(principalId))
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
