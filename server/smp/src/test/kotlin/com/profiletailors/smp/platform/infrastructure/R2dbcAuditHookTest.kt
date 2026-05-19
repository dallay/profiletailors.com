package com.profiletailors.smp.platform.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.integration.support.DatabaseUnitTestBase
import com.profiletailors.smp.platform.application.AuthorizationDecisionAuditFact
import com.profiletailors.smp.platform.application.AuthorizationReasonCode
import com.profiletailors.smp.platform.application.MutationAuditFact
import com.profiletailors.smp.platform.application.MutationAuditOutcome
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class R2dbcAuditHookTest : DatabaseUnitTestBase() {

    override fun databaseName(): String = "audit_hook"

    private val hook by lazy {
        R2dbcAuditHook(
            databaseClient = databaseClient,
            objectMapper = ObjectMapper(),
            clock = Clock.fixed(Instant.parse("2026-05-20T12:00:00Z"), ZoneOffset.UTC),
        )
    }

    @Test
    fun `persists authorization decision audit event`() = runTest {
        hook.onAuthorizationDecision(
            AuthorizationDecisionAuditFact(
                requestName = "request-name",
                requestPath = "/api/authorization/workspace-access/current",
                permission = "workspace:access:read",
                principalId = "principal-1",
                workspaceId = "workspace-1",
                decision = AuthorizationDecision.ALLOW,
                reasonCode = AuthorizationReasonCode.ROLE_PERMISSION,
                roleKeys = listOf("member"),
            ),
        )

        val row = databaseClient.sql(
            "SELECT event_type, request_name, permission, actor_principal_id, workspace_id, outcome, reason_code FROM audit_events",
        )
            .map { result, _ ->
                mapOf(
                    "event_type" to result.get("event_type", String::class.java),
                    "request_name" to result.get("request_name", String::class.java),
                    "permission" to result.get("permission", String::class.java),
                    "actor_principal_id" to result.get("actor_principal_id", String::class.java),
                    "workspace_id" to result.get("workspace_id", String::class.java),
                    "outcome" to result.get("outcome", String::class.java),
                    "reason_code" to result.get("reason_code", String::class.java),
                )
            }
            .one()
            .awaitSingle()

        assertEquals("AUTHORIZATION_DECISION", row["event_type"])
        assertEquals("request-name", row["request_name"])
        assertEquals("workspace:access:read", row["permission"])
        assertEquals("principal-1", row["actor_principal_id"])
        assertEquals("workspace-1", row["workspace_id"])
        assertEquals("ALLOW", row["outcome"])
        assertEquals("ROLE_PERMISSION", row["reason_code"])
    }

    @Test
    fun `persists mutation audit event`() = runTest {
        hook.onMutation(
            MutationAuditFact(
                action = "workspace.owner.add",
                targetType = "WORKSPACE_OWNER",
                targetId = "owner-2",
                actorPrincipalId = "owner-1",
                workspaceId = "workspace-1",
                outcome = MutationAuditOutcome.SUCCESS,
                details = mapOf("ownerPrincipalId" to "owner-2"),
            ),
        )

        val row = databaseClient.sql(
            "SELECT event_type, action, actor_principal_id, workspace_id, target_type, target_id, outcome FROM audit_events",
        )
            .map { result, _ ->
                mapOf(
                    "event_type" to result.get("event_type", String::class.java),
                    "action" to result.get("action", String::class.java),
                    "actor_principal_id" to result.get("actor_principal_id", String::class.java),
                    "workspace_id" to result.get("workspace_id", String::class.java),
                    "target_type" to result.get("target_type", String::class.java),
                    "target_id" to result.get("target_id", String::class.java),
                    "outcome" to result.get("outcome", String::class.java),
                )
            }
            .one()
            .awaitSingle()

        assertEquals("MUTATION", row["event_type"])
        assertEquals("workspace.owner.add", row["action"])
        assertEquals("owner-1", row["actor_principal_id"])
        assertEquals("workspace-1", row["workspace_id"])
        assertEquals("WORKSPACE_OWNER", row["target_type"])
        assertEquals("owner-2", row["target_id"])
        assertEquals("SUCCESS", row["outcome"])
    }
}
