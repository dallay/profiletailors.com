package com.profiletailors.smp.audit.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.common.domain.observability.RequestOutcome
import com.profiletailors.smp.audit.domain.AuditHook
import com.profiletailors.smp.audit.domain.AuthorizationDecisionAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditFact
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import java.time.Clock
import java.util.UUID

class R2dbcAuditHook(
    private val databaseClient: DatabaseClient,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC(),
) : AuditHook {
    override suspend fun onRequestHandled(requestName: String, outcome: RequestOutcome) = Unit

    override suspend fun onAuthorizationDecision(fact: AuthorizationDecisionAuditFact) {
        databaseClient.sql(
            """
            INSERT INTO audit_events (
                id,
                event_type,
                action,
                request_name,
                request_path,
                permission,
                actor_principal_id,
                workspace_id,
                target_type,
                target_id,
                outcome,
                reason_code,
                role_keys_json,
                details_json,
                created_at
            ) VALUES (
                :id,
                'AUTHORIZATION_DECISION',
                NULL,
                :requestName,
                :requestPath,
                :permission,
                :actorPrincipalId,
                :workspaceId,
                NULL,
                NULL,
                :outcome,
                :reasonCode,
                :roleKeysJson,
                :detailsJson,
                :createdAt
            )
            """.trimIndent(),
        )
            .bind("id", nextId())
            .bind("requestName", fact.requestName)
            .bind("requestPath", fact.requestPath)
            .bind("permission", fact.permission)
            .bind("actorPrincipalId", UUID.fromString(fact.principalId))
            .bindNullable("workspaceId", fact.workspaceId?.let(UUID::fromString), UUID::class.java)
            .bind("outcome", fact.decision)
            .bind("reasonCode", fact.reasonCode)
            .bind("roleKeysJson", objectMapper.writeValueAsString(fact.roleKeys))
            .bind("detailsJson", objectMapper.writeValueAsString(emptyMap<String, String>()))
            .bind("createdAt", clock.instant())
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun onMutation(fact: MutationAuditFact) {
        databaseClient.sql(
            """
            INSERT INTO audit_events (
                id,
                event_type,
                action,
                request_name,
                request_path,
                permission,
                actor_principal_id,
                workspace_id,
                target_type,
                target_id,
                outcome,
                reason_code,
                role_keys_json,
                details_json,
                created_at
            ) VALUES (
                :id,
                'MUTATION',
                :action,
                NULL,
                NULL,
                NULL,
                :actorPrincipalId,
                :workspaceId,
                :targetType,
                :targetId,
                :outcome,
                NULL,
                :roleKeysJson,
                :detailsJson,
                :createdAt
            )
            """.trimIndent(),
        )
            .bind("id", nextId())
            .bind("action", fact.action)
            .bind("actorPrincipalId", UUID.fromString(fact.actorPrincipalId))
            .bindNullable("workspaceId", fact.workspaceId?.let(UUID::fromString), UUID::class.java)
            .bind("targetType", fact.targetType)
            .bind("targetId", fact.targetId)
            .bind("outcome", fact.outcome.name)
            .bind("roleKeysJson", objectMapper.writeValueAsString(emptyList<String>()))
            .bind("detailsJson", objectMapper.writeValueAsString(fact.details))
            .bind("createdAt", clock.instant())
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private fun nextId(): UUID = UUID.randomUUID()

    private fun org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec.bindNullable(
        name: String,
        value: UUID?,
        type: Class<UUID>,
    ): org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec =
        value?.let { bind(name, it) } ?: bindNull(name, type)
}
