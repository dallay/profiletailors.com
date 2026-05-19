package com.profiletailors.smp.authorization.infrastructure

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.authorization.application.ScopeResolver
import com.profiletailors.smp.authorization.domain.AuthorizationScope
import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.identity.domain.PrincipalContext
import com.profiletailors.smp.platform.domain.ResourceContext
import com.profiletailors.smp.platform.domain.ResourceContextType
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class R2dbcWorkspaceTargetScopeResolver(
    private val databaseClient: DatabaseClient,
    private val objectMapper: ObjectMapper,
) : ScopeResolver {
    override suspend fun resolve(
        principalContext: PrincipalContext,
        resourceContext: ResourceContext,
    ): Set<AuthorizationScope> {
        val workspaceId = resourceContext.workspaceId
            ?.takeIf { resourceContext.type == ResourceContextType.WORKSPACE && it.isNotBlank() }
            ?: return emptySet()

        return databaseClient.sql(
            """
            SELECT p.permission_key,
                   s.target_resource_type,
                   s.allowed_target_ids_json
            FROM workspace_target_scopes s
            JOIN permissions p ON p.id = s.permission_id
            WHERE s.workspace_id = :workspaceId
              AND s.principal_id = :principalId
              AND s.principal_type = :principalType
            ORDER BY p.permission_key, s.target_resource_type
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("principalId", principalContext.principalId)
            .bind("principalType", principalContext.principalType.name)
            .map { row, _ ->
                AuthorizationScope(
                    permission = PermissionKey(requireNotNull(row.get("permission_key", String::class.java))),
                    resourceContextType = ResourceContextType.WORKSPACE,
                    targetResourceType = requireNotNull(row.get("target_resource_type", String::class.java)),
                    allowedTargetResourceIds = decodeAllowedTargetIds(
                        requireNotNull(row.get("allowed_target_ids_json", String::class.java)),
                    ),
                )
            }
            .all()
            .collectList()
            .awaitSingle()
            .toSet()
    }

    private fun decodeAllowedTargetIds(rawJson: String): Set<String> {
        val decoded = objectMapper.readValue(rawJson, ALLOWED_TARGET_IDS_TYPE)
        return decoded.filter { it.isNotBlank() }.toSet()
    }

    companion object {
        private val ALLOWED_TARGET_IDS_TYPE = object : TypeReference<List<String>>() {}
    }
}
