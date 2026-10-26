package com.profiletailors.smp.governance.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.audit.domain.AuthorizationDecisionAuditFact
import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.AuthorizationReasonCode
import com.profiletailors.smp.governance.domain.AuditEventCursor
import com.profiletailors.smp.governance.domain.AuditEventFilter
import com.profiletailors.smp.governance.domain.AuditEventPageRequest
import com.profiletailors.smp.integration.support.PostgresDatabaseTestBase
import com.profiletailors.smp.integration.support.PostgresTestContainerSupport
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcAuditEventReaderTest : PostgresDatabaseTestBase() {

    override val postgres = postgresContainer

    private val objectMapper = ObjectMapper()
    private val auditHook by lazy {
        com.profiletailors.smp.audit.infrastructure.R2dbcAuditHook(
            databaseClient = databaseClient,
            objectMapper = objectMapper,
            clock = Clock.fixed(Instant.parse("2026-05-20T12:00:00Z"), ZoneOffset.UTC),
        )
    }
    private val reader by lazy { R2dbcAuditEventReader(databaseClient, objectMapper) }

    @Test
    fun `reads persisted workspace audit events with cursor pagination and filters`() = runTest {
        auditHook.onAuthorizationDecision(
            AuthorizationDecisionAuditFact(
                requestName = "request-1",
                requestPath = "/api/authorization/workspace-access/current",
                permission = "workspace:access:read",
                principalId = PRINCIPAL_1,
                workspaceId = WORKSPACE_1,
                decision = AuthorizationDecision.ALLOW.name,
                reasonCode = com.profiletailors.smp.authorization.domain.AuthorizationReasonCode.ROLE_PERMISSION.name,
                roleKeys = listOf("member"),
            ),
        )
        databaseClient.sql(
            """
            INSERT INTO audit_events (
                id, event_type, action, actor_principal_id, workspace_id,
                target_type, target_id, outcome, role_keys_json, details_json, created_at
            ) VALUES (
                '$AUDIT_MANUAL_1', 'MUTATION', 'workspace.owner.add', '$OWNER_1',
                '$WORKSPACE_1', 'WORKSPACE_OWNER', 'owner-2', 'SUCCESS', '[]',
                '{"ownerPrincipalId":"owner-2"}', TIMESTAMP WITH TIME ZONE '2026-05-20T12:05:00Z'
            )
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO audit_events (
                id, event_type, action, actor_principal_id, workspace_id,
                target_type, target_id, outcome, role_keys_json, details_json, created_at
            ) VALUES (
                '$AUDIT_MANUAL_2', 'MUTATION', 'workspace.owner.add', '$OWNER_1',
                '$WORKSPACE_1', 'WORKSPACE_OWNER', 'owner-3', 'SUCCESS', '[]',
                '{"ownerPrincipalId":"owner-3"}', TIMESTAMP WITH TIME ZONE '2026-05-20T12:06:00Z'
            )
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        val items = reader.readWorkspaceEvents(
            workspaceId = WORKSPACE_1,
            filter = AuditEventFilter(
                targetType = "WORKSPACE_OWNER",
                action = "workspace.owner.add",
                eventType = "MUTATION",
                actorPrincipalId = OWNER_1,
                createdAfter = Instant.parse("2026-05-20T12:04:00Z"),
                createdBefore = Instant.parse("2026-05-20T12:07:00Z"),
            ),
            pageRequest = AuditEventPageRequest(
                cursor = AuditEventCursor(
                    createdAt = Instant.parse("2026-05-20T12:06:00Z"),
                    id = AUDIT_MANUAL_2,
                ),
                limit = 1,
            ),
        )

        assertEquals(1, items.size)
        assertEquals(AUDIT_MANUAL_1, items.first().id)
    }

    @Test
    fun `reads all events when no filters and no cursor provided`() = runTest {
        auditHook.onAuthorizationDecision(
            AuthorizationDecisionAuditFact(
                requestName = "request-1",
                requestPath = "/api/authorization/workspace-access/current",
                permission = "workspace:access:read",
                principalId = PRINCIPAL_1,
                workspaceId = WORKSPACE_2,
                decision = AuthorizationDecision.ALLOW.name,
                reasonCode = AuthorizationReasonCode.ROLE_PERMISSION.name,
                roleKeys = listOf("member"),
            ),
        )
        databaseClient.sql(
            """
            INSERT INTO audit_events (
                id, event_type, action, actor_principal_id, workspace_id,
                target_type, target_id, outcome, role_keys_json, details_json, created_at
            ) VALUES (
                '$AUDIT_PLAIN_1', 'MUTATION', 'workspace.update', '$ADMIN_1',
                '$WORKSPACE_2', 'WORKSPACE', 'ws-1', 'SUCCESS', '[]', '{}',
                TIMESTAMP WITH TIME ZONE '2026-05-20T12:10:00Z'
            )
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        val items = reader.readWorkspaceEvents(
            workspaceId = WORKSPACE_2,
            filter = AuditEventFilter(), // all defaults → all null filters
            pageRequest = AuditEventPageRequest(), // default page request
        )

        // Should return both events in order (most recent first)
        assertEquals(2, items.size)
        assertEquals(AUDIT_PLAIN_1, items.first().id)
        assertEquals(WORKSPACE_2, items.first().workspaceId)
    }

    @Test
    fun `reads events with only action filter`() = runTest {
        databaseClient.sql(
            """
            INSERT INTO audit_events (
                id, event_type, action, actor_principal_id, workspace_id,
                target_type, target_id, outcome, role_keys_json, details_json, created_at
            ) VALUES
                ('$ACTION_1', 'MUTATION', 'workspace.update', '$ADMIN_1', '$WORKSPACE_3',
                 'WORKSPACE', 'ws-1', 'SUCCESS', '[]', '{}',
                 TIMESTAMP WITH TIME ZONE '2026-05-20T12:20:00Z'),
                ('$ACTION_2', 'MUTATION', 'workspace.delete', '$ADMIN_1', '$WORKSPACE_3',
                 'WORKSPACE', 'ws-1', 'SUCCESS', '[]', '{}',
                 TIMESTAMP WITH TIME ZONE '2026-05-20T12:21:00Z'),
                ('$ACTION_3', 'MUTATION', 'workspace.update', '$ADMIN_2', '$WORKSPACE_3',
                 'WORKSPACE', 'ws-1', 'SUCCESS', '[]', '{}',
                 TIMESTAMP WITH TIME ZONE '2026-05-20T12:22:00Z')
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        // Filter by action = "workspace.update" only — covers partial filter branches
        val items = reader.readWorkspaceEvents(
            workspaceId = WORKSPACE_3,
            filter = AuditEventFilter(action = "workspace.update"),
            pageRequest = AuditEventPageRequest(limit = 10),
        )

        assertEquals(2, items.size)
        kotlin.test.assertTrue(items.all { it.action == "workspace.update" })
    }

    @AfterEach
    fun cleanAuditEvents() {
        deleteAllRows()
    }

    private fun deleteAllRows() = runTest {
        databaseClient.sql("DELETE FROM audit_events").fetch().rowsUpdated().awaitSingle()
    }

    companion object {
        private const val PRINCIPAL_1 = "f4b0c946-5bac-5e21-a5af-5213b71804cc"
        private const val WORKSPACE_1 = "d3d9800f-120a-501e-ad01-401e54e8f766"
        private const val AUDIT_MANUAL_1 = "2a4e64dd-7889-5acc-a298-15f02db74aa0"
        private const val OWNER_1 = "35205e1e-1d3d-54b7-9ea0-5d1e92734d4d"
        private const val AUDIT_MANUAL_2 = "167c7bd2-67c9-563d-82c9-3ce9201fe8f3"
        private const val WORKSPACE_2 = "57af0326-afba-57ec-83e4-15ec87ecc8bc"
        private const val AUDIT_PLAIN_1 = "9cff472f-46fc-5450-8487-88dc921a6dca"
        private const val ADMIN_1 = "4c936d83-145a-54dc-9054-3a7fc0a70b85"
        private const val ACTION_1 = "27cb732b-6610-584a-ba72-5ef3814df67d"
        private const val WORKSPACE_3 = "5bcd87e0-6e75-5367-b969-99c74c860cdc"
        private const val ACTION_2 = "752b8e4e-0224-5b11-a994-3a985e202b58"
        private const val ACTION_3 = "aaf38802-7e48-5d45-a9b5-6d70969ae341"
        private const val ADMIN_2 = "b264d79e-8ff6-54c2-906a-59c99195a252"

        @Container
        val postgresContainer = PostgresTestContainerSupport.newContainer("audit_reader")
    }
}
