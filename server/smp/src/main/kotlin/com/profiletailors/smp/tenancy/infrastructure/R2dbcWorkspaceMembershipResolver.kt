package com.profiletailors.smp.tenancy.infrastructure

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import com.profiletailors.smp.authorization.domain.WorkspaceMembershipResolver
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipAccessChecker
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipLookup
import com.profiletailors.smp.tenancy.domain.WorkspaceMembership
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class R2dbcWorkspaceMembershipResolver(private val databaseClient: DatabaseClient) :
    WorkspaceMembershipResolver,
    WorkspaceMembershipLookup,
    WorkspaceMembershipAccessChecker {
    override suspend fun resolve(
        principalContext: PrincipalContext,
        resourceContext: ResourceContext,
    ): WorkspaceMembership? = resolve(principalContext.principalId, resourceContext)

    override suspend fun resolve(principalId: String, resourceContext: ResourceContext): WorkspaceMembership? {
        val workspaceId = resourceContext.workspaceId
            ?.takeIf { resourceContext.type == ResourceContextType.WORKSPACE && it.isNotBlank() }
            ?: return null

        return databaseClient.sql(
            """
            SELECT id, workspace_id, principal_id, principal_type, status
            FROM workspace_memberships
            WHERE workspace_id = :workspaceId AND principal_id = :principalId
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
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

    override suspend fun isActiveMember(principalId: String, resourceContext: ResourceContext): Boolean =
        resolve(principalId, resourceContext)?.isActive() == true
}
