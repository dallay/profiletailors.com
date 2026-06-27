package com.profiletailors.smp.authorization.infrastructure.persistence

import com.profiletailors.common.domain.workspace.WorkspaceMembershipSnapshot
import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.authorization.domain.Role
import com.profiletailors.smp.authorization.domain.RoleCategory
import com.profiletailors.smp.authorization.domain.WorkspaceMembershipRoleResolver
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class R2dbcWorkspaceMembershipRoleResolver(private val databaseClient: DatabaseClient) :
    WorkspaceMembershipRoleResolver {
    override suspend fun resolve(membership: WorkspaceMembershipSnapshot): Set<Role> {
        val rows = databaseClient.sql(
            """
            SELECT r.id            AS role_id,
                   r.role_key      AS role_key,
                   r.category      AS role_category,
                   p.permission_key AS permission_key
            FROM membership_roles mr
            JOIN roles r ON r.id = mr.role_id
            LEFT JOIN role_permissions rp ON rp.role_id = r.id
            LEFT JOIN permissions p ON p.id = rp.permission_id
            WHERE mr.membership_id = :membershipId
            ORDER BY r.role_key, p.permission_key
            """.trimIndent(),
        )
            .bind("membershipId", membership.id)
            .fetch()
            .all()
            .collectList()
            .awaitSingle()

        return rows.groupBy { row -> requireNotNull(row["role_key"] as String) }
            .map { (_, roleRows) ->
                val firstRow = roleRows.first()
                Role(
                    key = requireNotNull(firstRow["role_key"] as String),
                    category = RoleCategory.valueOf(requireNotNull(firstRow["role_category"] as String)),
                    permissions = roleRows.mapNotNull { row ->
                        (row["permission_key"] as String?)?.let { permissionKey -> PermissionKey(permissionKey) }
                    }.toSet(),
                )
            }
            .toSet()
    }
}
