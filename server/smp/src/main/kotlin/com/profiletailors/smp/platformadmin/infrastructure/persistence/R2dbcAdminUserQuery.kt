package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.identity.domain.UserAccountState
import com.profiletailors.smp.platformadmin.application.PlatformPrincipalIds
import com.profiletailors.smp.platformadmin.application.contracts.AdminUserQuery
import com.profiletailors.smp.platformadmin.application.contracts.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.application.model.AdminUserDetail
import com.profiletailors.smp.platformadmin.application.model.AdminUserSummary
import com.profiletailors.smp.platformadmin.application.model.AdminWorkspaceMembershipSummary
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.application.query.ListAdminUsersQuery
import com.profiletailors.smp.tenancy.application.WorkspaceReadRepository
import io.r2dbc.spi.Readable
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class R2dbcAdminUserQuery(
    private val databaseClient: DatabaseClient,
    private val platformRoleAssignmentRepository: PlatformRoleAssignmentRepository,
    private val workspaceReadRepository: WorkspaceReadRepository,
) : AdminUserQuery {

    override suspend fun list(query: ListAdminUsersQuery): PagedResult<AdminUserSummary> {
        validatePagination(query.page, query.size)

        val conditions = mutableListOf("p.principal_type = :principalType")
        val params = mutableMapOf<String, Any?>("principalType" to "USER")

        query.status?.let {
            conditions += "p.account_state = :status"
            params["status"] = UserAccountState.valueOf(it.uppercase()).name
        }
        query.email?.let {
            conditions += "LOWER(ui.email) = :email"
            params["email"] = it.trim().lowercase()
        }
        query.createdFrom?.let {
            conditions += "p.created_at >= :createdFrom"
            params["createdFrom"] = OffsetDateTime.ofInstant(it, ZoneOffset.UTC)
        }
        query.createdTo?.let {
            conditions += "p.created_at <= :createdTo"
            params["createdTo"] = OffsetDateTime.ofInstant(it, ZoneOffset.UTC)
        }

        val where = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
        val orderCol = ALLOWED_SORT_FIELDS[query.sortField] ?: "p.created_at"
        val orderDir = if (query.sortDirection.uppercase() == "ASC") "ASC" else "DESC"
        val offset = query.page.toLong() * query.size

        val countSql = "SELECT COUNT(*) FROM principals p LEFT JOIN user_identities ui ON ui.principal_id = p.id $where"
        val dataSql = """
            SELECT p.id, p.principal_type, p.account_state, p.status, p.version, p.created_at, p.display_identity,
                   ui.email, ui.email_status,
                   (SELECT COUNT(*) FROM workspace_memberships wm WHERE wm.principal_id = p.id) AS workspace_count,
                   '' AS platform_roles
            FROM principals p
            LEFT JOIN user_identities ui ON ui.principal_id = p.id
            $where
            ORDER BY $orderCol $orderDir
            LIMIT :size OFFSET :offset
        """.trimIndent()

        val countSpec = params.entries.fold(databaseClient.sql(countSql)) { spec, (k, v) ->
            if (v != null) spec.bind(k, v) else spec
        }
        val dataSpec = params.entries.fold(
            databaseClient.sql(dataSql).bind("size", query.size).bind("offset", offset),
        ) { spec, (k, v) ->
            if (v != null) spec.bind(k, v) else spec
        }

        val total = countSpec.map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one().awaitSingle()
        val items = dataSpec.map { row, _ -> row.toSummary() }.all().collectList().awaitSingle()
        val enrichedItems = items.map { item ->
            val principalId = item.principalId
            item.copy(
                platformRoles = findPlatformRolesByPrincipalId(principalId),
                workspaceCount = workspaceReadRepository.findMembershipsByPrincipal(principalId).size,
            )
        }

        return PagedResult.of(enrichedItems, query.page, query.size, total)
    }

    override suspend fun findById(principalId: String): AdminUserDetail? {
        val detail = databaseClient.sql(SELECT_USER_DETAIL)
            .bind("id", principalId)
            .map { row, _ -> row.toDetail() }
            .one()
            .awaitSingleOrNull()
            ?: return null
        return detail.copy(
            workspaceMemberships = findWorkspacesByPrincipalId(principalId),
            platformRoles = findPlatformRolesByPrincipalId(principalId),
        )
    }

    private suspend fun findPlatformRolesByPrincipalId(principalId: String): List<String> =
        runCatching { PlatformPrincipalIds.toUuid(principalId) }
            .getOrNull()
            ?.let {
                platformRoleAssignmentRepository.findActiveByPrincipalId(it)
                    .map { assignment -> assignment.role.name }
                    .sorted()
            }
            ?: emptyList()

    override suspend fun findWorkspacesByPrincipalId(principalId: String): List<AdminWorkspaceMembershipSummary> =
        workspaceReadRepository.findMembershipsByPrincipal(principalId).map { workspace ->
            AdminWorkspaceMembershipSummary(
                workspaceId = workspace.workspaceId,
                workspaceName = workspace.workspaceName,
                membershipStatus = workspace.membershipStatus,
                workspaceRoles = workspace.workspaceRoles,
                joinedAt = workspace.joinedAt,
            )
        }

    private fun Readable.toSummary() = AdminUserSummary(
        principalId = requireNotNull(get("id", String::class.java)),
        email = get("email", String::class.java),
        displayIdentity = get("display_identity", String::class.java),
        principalType = requireNotNull(get("principal_type", String::class.java)),
        accountState = UserAccountState.valueOf(requireNotNull(get("account_state", String::class.java))),
        emailStatus = get("email_status", String::class.java)?.let(EmailStatus::valueOf),
        createdAt = requireNotNull(get("created_at", OffsetDateTime::class.java)).toInstant(),
        lastAuthenticatedAt = null,
        authenticationMethods = emptyList(),
        workspaceCount = requireNotNull(get("workspace_count", Number::class.java)).toInt(),
        platformRoles = get("platform_roles", String::class.java).orEmpty().splitRoles(),
        status = requireNotNull(get("status", String::class.java)),
        version = requireNotNull(get("version", Number::class.java)).toLong(),
    )

    private fun Readable.toDetail() = AdminUserDetail(
        principalId = requireNotNull(get("id", String::class.java)),
        email = get("email", String::class.java),
        displayIdentity = get("display_identity", String::class.java),
        principalType = requireNotNull(get("principal_type", String::class.java)),
        accountState = UserAccountState.valueOf(requireNotNull(get("account_state", String::class.java))),
        emailStatus = get("email_status", String::class.java)?.let(EmailStatus::valueOf),
        createdAt = requireNotNull(get("created_at", OffsetDateTime::class.java)).toInstant(),
        lastAuthenticatedAt = null,
        authenticationMethods = emptyList(),
        workspaceMemberships = emptyList(),
        platformRoles = get("platform_roles", String::class.java).orEmpty().splitRoles(),
        status = requireNotNull(get("status", String::class.java)),
        version = requireNotNull(get("version", Number::class.java)).toLong(),
    )

    companion object {
        private val ALLOWED_SORT_FIELDS = mapOf(
            "createdAt" to "p.created_at",
            "email" to "ui.email",
        )
        private const val SELECT_USER_DETAIL = """
            SELECT p.id, p.principal_type, p.account_state, p.status, p.version, p.created_at, p.display_identity,
                   ui.email, ui.email_status,
                    0 AS workspace_count,
                    '' AS platform_roles
             FROM principals p LEFT JOIN user_identities ui ON ui.principal_id = p.id
             WHERE p.id = :id
               AND p.principal_type = 'USER'
        """
        private const val SELECT_WORKSPACES = """
            SELECT wm.workspace_id, w.name AS workspace_name, wm.status AS membership_status, wm.created_at AS joined_at,
                   COALESCE(string_agg(DISTINCT r.role_key, ',' ORDER BY r.role_key), '') AS workspace_roles
            FROM workspace_memberships wm
            JOIN workspaces w ON w.id = wm.workspace_id
            LEFT JOIN membership_roles mr ON mr.membership_id = wm.id
            LEFT JOIN roles r ON r.id = mr.role_id
            WHERE wm.principal_id = :principalId
            GROUP BY wm.workspace_id, w.name, wm.status, wm.created_at
            ORDER BY wm.created_at DESC
        """

        private fun String.splitRoles(): List<String> = split(',').filter(String::isNotBlank)
    }
}
