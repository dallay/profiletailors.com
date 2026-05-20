package com.profiletailors.smp.authorization.infrastructure

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.authorization.domain.DirectGrantResolver
import com.profiletailors.smp.authorization.domain.DirectGrant
import com.profiletailors.smp.authorization.domain.GrantEffect
import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.identity.domain.PrincipalContext
import com.profiletailors.smp.platform.domain.ResourceContext
import com.profiletailors.smp.platform.domain.ResourceContextType
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class R2dbcDirectGrantResolver(
    private val databaseClient: DatabaseClient,
    private val objectMapper: ObjectMapper,
) : DirectGrantResolver {
    override suspend fun resolve(
        principalContext: PrincipalContext,
        resourceContext: ResourceContext,
    ): Set<DirectGrant> {
        val workspaceId = resourceContext.workspaceId
            ?.takeIf { resourceContext.type == ResourceContextType.WORKSPACE && it.isNotBlank() }
            ?: return emptySet()

        val rows = databaseClient.sql(
            """
            SELECT p.permission_key,
                   dg.effect,
                   dg.expires_at,
                   dg.conditions_json
            FROM workspace_direct_grants dg
            JOIN permissions p ON p.id = dg.permission_id
            WHERE dg.workspace_id = :workspaceId
              AND dg.principal_id = :principalId
              AND dg.principal_type = :principalType
            ORDER BY p.permission_key, dg.effect
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .bind("principalId", principalContext.principalId)
            .bind("principalType", principalContext.principalType.name)
            .fetch()
            .all()
            .collectList()
            .awaitSingle()

        return rows.map { row ->
            DirectGrant(
                permission = PermissionKey(requireNotNull(row["permission_key"] as String)),
                effect = GrantEffect.valueOf(requireNotNull(row["effect"] as String)),
                resourceContext = ResourceContext(
                    type = ResourceContextType.WORKSPACE,
                    workspaceId = workspaceId,
                ),
                expiresAt = (row["expires_at"] as OffsetDateTime?)?.toInstant(),
                conditions = decodeConditions(row["conditions_json"] as String?),
            )
        }.toSet()
    }

    private fun decodeConditions(conditionsJson: String?): Map<String, String> {
        if (conditionsJson.isNullOrBlank()) {
            return emptyMap()
        }

        return objectMapper.readValue(conditionsJson, CONDITIONS_TYPE)
    }

    companion object {
        private val CONDITIONS_TYPE = object : TypeReference<Map<String, String>>() {}
    }
}
