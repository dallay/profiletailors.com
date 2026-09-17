package com.profiletailors.smp.tenancy.infrastructure

import com.profiletailors.smp.tenancy.application.WorkspaceReadRepository
import com.profiletailors.smp.tenancy.application.WorkspaceSummary
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

/**
 * Read-only repository for workspace queries.
 *
 * Resolves workspaces by principal membership (joins workspaces with workspace_memberships).
 * This is the read side of the CQRS pattern for the tenancy bounded context.
 */
@Repository
internal class R2dbcWorkspaceReadRepository(private val databaseClient: DatabaseClient) : WorkspaceReadRepository {
    /**
     * Find all workspaces where the given principal has an ACTIVE membership.
     *
     * Joins workspace_memberships with workspaces to return only workspaces
     * where the user is an active member (status = ACTIVE).
     * Workspace status is also checked (must be ACTIVE — excludes archived/suspended).
     *
     * @param principalId The authenticated user's principal ID
     * @return List of workspace summaries ordered by name
     */
    override suspend fun findWorkspacesByPrincipal(principalId: String): List<WorkspaceSummary> = databaseClient.sql(
        """
            SELECT w.id AS workspace_id,
                   w.name AS workspace_name,
                   w.icon AS workspace_icon,
                   CASE WHEN wo.workspace_id IS NOT NULL THEN 'OWNER' ELSE 'MEMBER' END AS role
            FROM workspace_memberships wm
            INNER JOIN workspaces w ON w.id = wm.workspace_id
            LEFT JOIN workspace_ownerships wo ON wo.workspace_id = w.id
                                           AND wo.owner_principal_id = :principalId
            WHERE wm.principal_id = :principalId
              AND wm.status = :membershipStatus
              AND w.status = :workspaceStatus
            ORDER BY w.name ASC
        """.trimIndent(),
    )
        .bind("principalId", principalId)
        .bind("membershipStatus", "ACTIVE")
        .bind("workspaceStatus", "ACTIVE")
        .map { row, _ ->
            WorkspaceSummary(
                workspaceId = requireNotNull(row.get("workspace_id", String::class.java)),
                name = requireNotNull(row.get("workspace_name", String::class.java)),
                role = requireNotNull(row.get("role", String::class.java)),
                icon = row.get("workspace_icon", String::class.java),
            )
        }
        .all()
        .collectList()
        .awaitSingle()

    override suspend fun findMembershipsByPrincipal(principalId: String) = databaseClient.sql(
        """
            SELECT wm.workspace_id,
                   w.name AS workspace_name,
                   wm.status AS membership_status,
                   wm.created_at AS joined_at,
                   COALESCE(string_agg(DISTINCT r.role_key, ',' ORDER BY r.role_key), '') AS workspace_roles
            FROM workspace_memberships wm
            JOIN workspaces w ON w.id = wm.workspace_id
            LEFT JOIN membership_roles mr ON mr.membership_id = wm.id
            LEFT JOIN roles r ON r.id = mr.role_id
            WHERE wm.principal_id = :principalId
            GROUP BY wm.workspace_id, w.name, wm.status, wm.created_at
            ORDER BY wm.created_at DESC
        """.trimIndent(),
    )
        .bind("principalId", principalId)
        .map { row, _ ->
            com.profiletailors.smp.tenancy.application.WorkspaceMembershipSummary(
                workspaceId = requireNotNull(row.get("workspace_id", String::class.java)),
                workspaceName = requireNotNull(row.get("workspace_name", String::class.java)),
                membershipStatus = requireNotNull(row.get("membership_status", String::class.java)),
                workspaceRoles = row.get("workspace_roles", String::class.java)
                    .orEmpty()
                    .split(',')
                    .filter(String::isNotBlank),
                joinedAt = requireNotNull(row.get("joined_at", OffsetDateTime::class.java)).toInstant(),
            )
        }
        .all()
        .collectList()
        .awaitSingle()
}
