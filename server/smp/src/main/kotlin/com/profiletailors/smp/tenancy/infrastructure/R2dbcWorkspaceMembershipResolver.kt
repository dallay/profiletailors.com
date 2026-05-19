package com.profiletailors.smp.tenancy.infrastructure

import com.profiletailors.smp.authorization.application.WorkspaceMembershipResolver
import com.profiletailors.smp.identity.domain.PrincipalContext
import com.profiletailors.smp.identity.domain.PrincipalType
import com.profiletailors.smp.platform.domain.ResourceContext
import com.profiletailors.smp.platform.domain.ResourceContextType
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipLookup
import com.profiletailors.smp.tenancy.domain.WorkspaceMembership
import com.profiletailors.smp.tenancy.domain.WorkspaceMembershipStatus
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class R2dbcWorkspaceMembershipResolver(
    private val databaseClient: DatabaseClient,
) : WorkspaceMembershipResolver, WorkspaceMembershipLookup {
    override suspend fun resolve(
        principalContext: PrincipalContext,
        resourceContext: ResourceContext,
    ): WorkspaceMembership? = resolve(principalContext.principalId, resourceContext)

    override suspend fun resolve(
        principalId: String,
        resourceContext: ResourceContext,
    ): WorkspaceMembership? {
        if (resourceContext.type != ResourceContextType.WORKSPACE || resourceContext.workspaceId.isNullOrBlank()) {
            return null
        }

        return databaseClient.sql(
            """
            SELECT id, workspace_id, principal_id, principal_type, status
            FROM workspace_memberships
            WHERE workspace_id = :workspaceId AND principal_id = :principalId
            """.trimIndent(),
        )
            .bind("workspaceId", resourceContext.workspaceId)
            .bind("principalId", principalId)
            .map { row, _ ->
                val principalTypeValue = requireNotNull(row.get("principal_type", String::class.java))
                val statusValue = requireNotNull(row.get("status", String::class.java))
                WorkspaceMembership(
                    id = requireNotNull(row.get("id", String::class.java)),
                    workspaceId = requireNotNull(row.get("workspace_id", String::class.java)),
                    principalId = requireNotNull(row.get("principal_id", String::class.java)),
                    principalType = PrincipalType.valueOf(principalTypeValue),
                    status = WorkspaceMembershipStatus.valueOf(statusValue),
                )
            }
            .one()
            .awaitSingleOrNull()
    }
}
