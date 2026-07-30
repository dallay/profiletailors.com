package com.profiletailors.smp.platformadmin.infrastructure.persistence

import com.profiletailors.smp.platformadmin.application.model.AdminUserDetail
import com.profiletailors.smp.platformadmin.application.model.AdminUserSummary
import com.profiletailors.smp.platformadmin.application.model.AdminWorkspaceMembershipSummary
import com.profiletailors.smp.platformadmin.application.model.PagedResult
import com.profiletailors.smp.platformadmin.application.ports.AdminUserQuery
import com.profiletailors.smp.platformadmin.application.query.ListAdminUsersQuery
import io.r2dbc.spi.Readable
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class R2dbcAdminUserQuery(private val databaseClient: DatabaseClient) : AdminUserQuery {

    override suspend fun list(query: ListAdminUsersQuery): PagedResult<AdminUserSummary> {
        validatePagination(query.page, query.size)

        val conditions = mutableListOf<String>()
        val params = mutableMapOf<String, Any?>()

        query.status?.let {
            conditions += "p.status = :status"
            params["status"] = it
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
        val offset = query.page * query.size

        val countSql = "SELECT COUNT(*) FROM principals p LEFT JOIN user_identities ui ON ui.principal_id = p.id $where"
        val dataSql = """
            SELECT p.id, p.status, p.created_at,
                   ui.email, ui.display_name, ui.last_authenticated_at
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
            databaseClient.sql(dataSql).bind("size", query.size).bind("offset", offset)
        ) { spec, (k, v) ->
            if (v != null) spec.bind(k, v) else spec
        }

        val total = countSpec.map { row, _ -> requireNotNull(row.get(0, Long::class.java)) }
            .one().awaitSingle()
        val items = dataSpec.map { row, _ -> row.toSummary() }.all().collectList().awaitSingle()

        return PagedResult.of(items, query.page, query.size, total)
    }

    override suspend fun findById(principalId: String): AdminUserDetail? = databaseClient.sql(SELECT_USER_DETAIL)
        .bind("id", principalId)
        .map { row, _ -> row.toDetail() }
        .one()
        .awaitSingleOrNull()

    override suspend fun findWorkspacesByPrincipalId(principalId: String): List<AdminWorkspaceMembershipSummary> =
        databaseClient.sql(SELECT_WORKSPACES)
            .bind("principalId", principalId)
            .map { row, _ ->
                AdminWorkspaceMembershipSummary(
                    workspaceId = requireNotNull(row.get("workspace_id", String::class.java)),
                    workspaceName = requireNotNull(row.get("workspace_name", String::class.java)),
                    membershipStatus = requireNotNull(row.get("membership_status", String::class.java)),
                    workspaceRoles = emptyList(),
                    joinedAt = requireNotNull(row.get("joined_at", OffsetDateTime::class.java)).toInstant(),
                )
            }
            .all()
            .collectList()
            .awaitSingle()

    private fun Readable.toSummary() = AdminUserSummary(
        principalId = requireNotNull(get("id", String::class.java)),
        email = requireNotNull(get("email", String::class.java)),
        displayIdentity = get("display_name", String::class.java),
        principalType = requireNotNull(get("status", String::class.java)),
        createdAt = requireNotNull(get("created_at", OffsetDateTime::class.java)).toInstant(),
        lastAuthenticatedAt = get("last_authenticated_at", OffsetDateTime::class.java)?.toInstant(),
        authenticationMethods = emptyList(),
        workspaceCount = 0,
        platformRoles = emptyList(),
    )

    private fun Readable.toDetail() = AdminUserDetail(
        principalId = requireNotNull(get("id", String::class.java)),
        email = requireNotNull(get("email", String::class.java)),
        displayIdentity = get("display_name", String::class.java),
        principalType = requireNotNull(get("status", String::class.java)),
        createdAt = requireNotNull(get("created_at", OffsetDateTime::class.java)).toInstant(),
        lastAuthenticatedAt = get("last_authenticated_at", OffsetDateTime::class.java)?.toInstant(),
        authenticationMethods = emptyList(),
        workspaceMemberships = emptyList(),
        platformRoles = emptyList(),
    )

    companion object {
        private val ALLOWED_SORT_FIELDS = mapOf(
            "createdAt" to "p.created_at",
            "email" to "ui.email",
            "lastAuthenticatedAt" to "ui.last_authenticated_at",
        )
        private const val SELECT_USER_DETAIL = """
            SELECT p.id, p.status, p.created_at, ui.email, ui.display_name, ui.last_authenticated_at
            FROM principals p LEFT JOIN user_identities ui ON ui.principal_id = p.id
            WHERE p.id = :id
        """
        private const val SELECT_WORKSPACES = """
            SELECT wm.workspace_id, w.name AS workspace_name, wm.status AS membership_status, wm.created_at AS joined_at
            FROM workspace_memberships wm
            JOIN workspaces w ON w.id = wm.workspace_id
            WHERE wm.principal_id = :principalId
            ORDER BY wm.created_at DESC
        """
    }
}
