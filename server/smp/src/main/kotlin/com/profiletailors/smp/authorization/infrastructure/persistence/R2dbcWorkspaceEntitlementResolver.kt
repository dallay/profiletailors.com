package com.profiletailors.smp.authorization.infrastructure.persistence

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.authorization.domain.Entitlement
import com.profiletailors.smp.authorization.domain.EntitlementResolver
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class R2dbcWorkspaceEntitlementResolver(private val databaseClient: DatabaseClient) : EntitlementResolver {
    override suspend fun resolve(resourceContext: ResourceContext): Set<Entitlement> {
        val workspaceId = resourceContext.workspaceId
            ?.takeIf { resourceContext.type == ResourceContextType.WORKSPACE && it.isNotBlank() }
            ?: return emptySet()

        return databaseClient.sql(
            """
            SELECT entitlement_key, enabled
            FROM workspace_entitlements
            WHERE workspace_id = :workspaceId
            """.trimIndent(),
        )
            .bind("workspaceId", workspaceId)
            .map { row, _ ->
                Entitlement(
                    key = requireNotNull(row.get("entitlement_key", String::class.java)),
                    enabled = decodeEnabled(row.get("enabled")),
                )
            }
            .all()
            .collectList()
            .awaitSingle()
            .toSet()
    }

    private fun decodeEnabled(rawValue: Any?): Boolean = when (rawValue) {
        is Boolean -> rawValue
        is Number -> rawValue.toInt() != 0
        is String -> rawValue.equals("true", ignoreCase = true) || rawValue == "1"
        else -> throw IllegalArgumentException("Unsupported enabled value: $rawValue")
    }
}
