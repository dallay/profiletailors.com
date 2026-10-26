package com.profiletailors.smp.audit.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.audit.domain.AuthorizationDecisionAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditFact
import com.profiletailors.smp.audit.domain.MutationAuditOutcome
import com.profiletailors.smp.authorization.domain.AuthorizationReasonCode
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcAuditHookTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private val objectMapper = ObjectMapper()
    private val fixedClock = Clock.fixed(Instant.parse("2026-06-20T12:00:00Z"), ZoneOffset.UTC)
    private val hook by lazy { R2dbcAuditHook(databaseClient, objectMapper, fixedClock) }

    @Test
    fun `persists authorization decision audit events`() = runTest {
        hook.onAuthorizationDecision(
            AuthorizationDecisionAuditFact(
                requestName = "WorkspaceAccessSummaryEndpoint",
                requestPath = "/api/authorization/workspace-access/current",
                permission = "workspace:access:read",
                principalId = PRINCIPAL_1,
                workspaceId = WORKSPACE_1,
                decision = "ALLOW",
                reasonCode = AuthorizationReasonCode.ROLE_PERMISSION.name,
                roleKeys = listOf("member", "viewer"),
            ),
        )

        val row = databaseClient.sql(
            """
            SELECT event_type, request_name, request_path, permission, actor_principal_id,
                   workspace_id, outcome, reason_code, role_keys_json, details_json, created_at
            FROM audit_events
            WHERE actor_principal_id = '$PRINCIPAL_1'
            """.trimIndent(),
        ).map { row, _ ->
            mapOf(
                "event_type" to row.get("event_type", String::class.java),
                "request_name" to row.get("request_name", String::class.java),
                "request_path" to row.get("request_path", String::class.java),
                "permission" to row.get("permission", String::class.java),
                "workspace_id" to row.get("workspace_id", UUID::class.java)?.toString(),
                "outcome" to row.get("outcome", String::class.java),
                "reason_code" to row.get("reason_code", String::class.java),
                "role_keys_json" to row.get("role_keys_json", String::class.java),
                "details_json" to row.get("details_json", String::class.java),
                "created_at" to row.get("created_at", OffsetDateTime::class.java)?.toInstant(),
            )
        }.one().awaitSingle()

        assertEquals("AUTHORIZATION_DECISION", row["event_type"])
        assertEquals("WorkspaceAccessSummaryEndpoint", row["request_name"])
        assertEquals("/api/authorization/workspace-access/current", row["request_path"])
        assertEquals("workspace:access:read", row["permission"])
        assertEquals(WORKSPACE_1, row["workspace_id"])
        assertEquals("ALLOW", row["outcome"])
        assertEquals(AuthorizationReasonCode.ROLE_PERMISSION.name, row["reason_code"])
        assertEquals("[\"member\",\"viewer\"]", row["role_keys_json"])
        assertEquals("{}", row["details_json"])
        assertEquals(fixedClock.instant(), row["created_at"])
    }

    @Test
    fun `persists authorization decision with null workspace id`() = runTest {
        hook.onAuthorizationDecision(
            AuthorizationDecisionAuditFact(
                requestName = "GlobalEndpoint",
                requestPath = "/api/health",
                permission = "health:read",
                principalId = PRINCIPAL_2,
                workspaceId = null,
                decision = "DENY",
                reasonCode = AuthorizationReasonCode.MISSING_PERMISSION.name,
                roleKeys = emptyList(),
            ),
        )

        val row = databaseClient.sql(
            "SELECT workspace_id FROM audit_events WHERE actor_principal_id = '$PRINCIPAL_2'",
        ).map { row, _ ->
            mapOf("workspace_id" to row.get("workspace_id", UUID::class.java)?.toString())
        }.one().awaitSingle()

        assertEquals(null, row["workspace_id"])
    }

    @Test
    fun `persists mutation audit events`() = runTest {
        hook.onMutation(
            MutationAuditFact(
                action = "workspace.update",
                targetType = "WORKSPACE",
                targetId = "ws-1",
                actorPrincipalId = ADMIN_1,
                workspaceId = WORKSPACE_1,
                outcome = MutationAuditOutcome.SUCCESS,
                details = mapOf("field" to "name", "newValue" to "Profile Tailors"),
            ),
        )

        val row = databaseClient.sql(
            """
            SELECT event_type, action, actor_principal_id, workspace_id,
                   target_type, target_id, outcome, role_keys_json, details_json, created_at
            FROM audit_events
            WHERE actor_principal_id = '$ADMIN_1'
            """.trimIndent(),
        ).map { row, _ ->
            mapOf(
                "event_type" to row.get("event_type", String::class.java),
                "action" to row.get("action", String::class.java),
                "workspace_id" to row.get("workspace_id", UUID::class.java)?.toString(),
                "target_type" to row.get("target_type", String::class.java),
                "target_id" to row.get("target_id", String::class.java),
                "outcome" to row.get("outcome", String::class.java),
                "role_keys_json" to row.get("role_keys_json", String::class.java),
                "details_json" to row.get("details_json", String::class.java),
                "created_at" to row.get("created_at", OffsetDateTime::class.java)?.toInstant(),
            )
        }.one().awaitSingle()

        assertEquals("MUTATION", row["event_type"])
        assertEquals("workspace.update", row["action"])
        assertEquals(WORKSPACE_1, row["workspace_id"])
        assertEquals("WORKSPACE", row["target_type"])
        assertEquals("ws-1", row["target_id"])
        assertEquals("SUCCESS", row["outcome"])
        assertEquals("[]", row["role_keys_json"])
        assertTrue((row["details_json"] as String).contains("Profile Tailors"))
        assertEquals(fixedClock.instant(), row["created_at"])
    }

    @Test
    fun `persists mutation audit events with rejected outcome`() = runTest {
        hook.onMutation(
            MutationAuditFact(
                action = "workspace.delete",
                targetType = "WORKSPACE",
                targetId = "ws-2",
                actorPrincipalId = ADMIN_2,
                workspaceId = null,
                outcome = MutationAuditOutcome.REJECTED,
                details = emptyMap(),
            ),
        )

        val row = databaseClient.sql(
            "SELECT outcome, workspace_id FROM audit_events WHERE actor_principal_id = '$ADMIN_2'",
        ).map { row, _ ->
            mapOf(
                "outcome" to row.get("outcome", String::class.java),
                "workspace_id" to row.get("workspace_id", UUID::class.java)?.toString(),
            )
        }.one().awaitSingle()

        assertEquals("REJECTED", row["outcome"])
        assertEquals(null, row["workspace_id"])
    }

    @Test
    fun `generated ids are UUIDs`() = runTest {
        hook.onMutation(
            MutationAuditFact(
                action = "workspace.create",
                targetType = "WORKSPACE",
                targetId = "ws-3",
                actorPrincipalId = ADMIN_3,
                workspaceId = WORKSPACE_3,
                outcome = MutationAuditOutcome.SUCCESS,
                details = emptyMap(),
            ),
        )

        val row = databaseClient.sql(
            "SELECT id FROM audit_events WHERE actor_principal_id = '$ADMIN_3'",
        ).map { row, _ ->
            mapOf("id" to row.get("id", UUID::class.java)?.toString())
        }.one().awaitSingle()

        java.util.UUID.fromString(row["id"] as String)
    }

    private fun deleteAllRows() = runTest {
        databaseClient.sql("DELETE FROM audit_events").fetch().rowsUpdated().awaitSingle()
    }

    companion object {
        private const val PRINCIPAL_1 = "f4b0c946-5bac-5e21-a5af-5213b71804cc"
        private const val WORKSPACE_1 = "d3d9800f-120a-501e-ad01-401e54e8f766"
        private const val PRINCIPAL_2 = "7a878b88-f89d-5e15-8c16-200e06a13313"
        private const val ADMIN_1 = "4c936d83-145a-54dc-9054-3a7fc0a70b85"
        private const val ADMIN_2 = "b264d79e-8ff6-54c2-906a-59c99195a252"
        private const val ADMIN_3 = "22fe1ef4-fc68-5dae-b9e1-fcf8b88bd9bb"
        private const val WORKSPACE_3 = "5bcd87e0-6e75-5367-b969-99c74c860cdc"

        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("audit_hook")
    }
}
