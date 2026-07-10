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
                principalId = "principal-1",
                workspaceId = "workspace-1",
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
            WHERE actor_principal_id = 'principal-1'
            """.trimIndent(),
        ).map { row, _ ->
            mapOf(
                "event_type" to row.get("event_type", String::class.java),
                "request_name" to row.get("request_name", String::class.java),
                "request_path" to row.get("request_path", String::class.java),
                "permission" to row.get("permission", String::class.java),
                "workspace_id" to row.get("workspace_id", String::class.java),
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
        assertEquals("workspace-1", row["workspace_id"])
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
                principalId = "principal-2",
                workspaceId = null,
                decision = "DENY",
                reasonCode = AuthorizationReasonCode.MISSING_PERMISSION.name,
                roleKeys = emptyList(),
            ),
        )

        val row = databaseClient.sql(
            "SELECT workspace_id FROM audit_events WHERE actor_principal_id = 'principal-2'",
        ).map { row, _ ->
            mapOf("workspace_id" to row.get("workspace_id", String::class.java))
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
                actorPrincipalId = "admin-1",
                workspaceId = "workspace-1",
                outcome = MutationAuditOutcome.SUCCESS,
                details = mapOf("field" to "name", "newValue" to "Profile Tailors"),
            ),
        )

        val row = databaseClient.sql(
            """
            SELECT event_type, action, actor_principal_id, workspace_id,
                   target_type, target_id, outcome, role_keys_json, details_json, created_at
            FROM audit_events
            WHERE actor_principal_id = 'admin-1'
            """.trimIndent(),
        ).map { row, _ ->
            mapOf(
                "event_type" to row.get("event_type", String::class.java),
                "action" to row.get("action", String::class.java),
                "workspace_id" to row.get("workspace_id", String::class.java),
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
        assertEquals("workspace-1", row["workspace_id"])
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
                actorPrincipalId = "admin-2",
                workspaceId = null,
                outcome = MutationAuditOutcome.REJECTED,
                details = emptyMap(),
            ),
        )

        val row = databaseClient.sql(
            "SELECT outcome, workspace_id FROM audit_events WHERE actor_principal_id = 'admin-2'",
        ).map { row, _ ->
            mapOf(
                "outcome" to row.get("outcome", String::class.java),
                "workspace_id" to row.get("workspace_id", String::class.java),
            )
        }.one().awaitSingle()

        assertEquals("REJECTED", row["outcome"])
        assertEquals(null, row["workspace_id"])
    }

    @Test
    fun `generated ids use audit prefix`() = runTest {
        hook.onMutation(
            MutationAuditFact(
                action = "workspace.create",
                targetType = "WORKSPACE",
                targetId = "ws-3",
                actorPrincipalId = "admin-3",
                workspaceId = "workspace-3",
                outcome = MutationAuditOutcome.SUCCESS,
                details = emptyMap(),
            ),
        )

        val row = databaseClient.sql(
            "SELECT id FROM audit_events WHERE actor_principal_id = 'admin-3'",
        ).map { row, _ ->
            mapOf("id" to row.get("id", String::class.java))
        }.one().awaitSingle()

        assertTrue((row["id"] as String).startsWith("audit-"))
    }

    private fun deleteAllRows() = runTest {
        databaseClient.sql("DELETE FROM audit_events").fetch().rowsUpdated().awaitSingle()
    }

    companion object {
        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("audit_hook")
    }
}
