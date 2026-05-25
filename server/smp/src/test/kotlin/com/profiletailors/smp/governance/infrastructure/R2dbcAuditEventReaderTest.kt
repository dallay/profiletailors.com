package com.profiletailors.smp.governance.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.governance.application.AuditEventCursor
import com.profiletailors.smp.audit.domain.AuthorizationDecisionAuditFact
import com.profiletailors.smp.authorization.domain.AuthorizationReasonCode
import io.r2dbc.h2.H2ConnectionConfiguration
import io.r2dbc.h2.H2ConnectionFactory
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.resource.ClassLoaderResourceAccessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import java.sql.DriverManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class R2dbcAuditEventReaderTest {

    private val jdbcUrl = "jdbc:h2:mem:audit_reader;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    private val connectionFactory = H2ConnectionFactory(
        H2ConnectionConfiguration.builder()
            .inMemory("audit_reader")
            .property("MODE", "PostgreSQL")
            .property("DB_CLOSE_DELAY", "-1")
            .property("DB_CLOSE_ON_EXIT", "FALSE")
            .username("sa")
            .build(),
    )
    private val databaseClient = DatabaseClient.create(connectionFactory)
    private val objectMapper = ObjectMapper()
    private val auditHook = com.profiletailors.smp.platform.infrastructure.R2dbcAuditHook(
        databaseClient = databaseClient,
        objectMapper = objectMapper,
        clock = Clock.fixed(Instant.parse("2026-05-20T12:00:00Z"), ZoneOffset.UTC),
    )
    private val reader = R2dbcAuditEventReader(databaseClient, objectMapper)

    @BeforeEach
    fun setUp() {
        applyLiquibaseBaseline()
        deleteAllRows()
    }

    @Test
    fun `reads persisted workspace audit events with cursor pagination and filters`() = runTest {
        auditHook.onAuthorizationDecision(
            AuthorizationDecisionAuditFact(
                requestName = "request-1",
                requestPath = "/api/authorization/workspace-access/current",
                permission = "workspace:access:read",
                principalId = "principal-1",
                workspaceId = "workspace-1",
                decision = AuthorizationDecision.ALLOW.name,
                reasonCode = com.profiletailors.smp.authorization.domain.AuthorizationReasonCode.ROLE_PERMISSION.name,
                roleKeys = listOf("member"),
            ),
        )
        databaseClient.sql(
            """
            INSERT INTO audit_events (
                id, event_type, action, actor_principal_id, workspace_id, target_type, target_id, outcome, role_keys_json, details_json, created_at
            ) VALUES (
                'audit-manual-1', 'MUTATION', 'workspace.owner.add', 'owner-1', 'workspace-1', 'WORKSPACE_OWNER', 'owner-2', 'SUCCESS', '[]', '{"ownerPrincipalId":"owner-2"}', TIMESTAMP WITH TIME ZONE '2026-05-20T12:05:00Z'
            )
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()
        databaseClient.sql(
            """
            INSERT INTO audit_events (
                id, event_type, action, actor_principal_id, workspace_id, target_type, target_id, outcome, role_keys_json, details_json, created_at
            ) VALUES (
                'audit-manual-2', 'MUTATION', 'workspace.owner.add', 'owner-1', 'workspace-1', 'WORKSPACE_OWNER', 'owner-3', 'SUCCESS', '[]', '{"ownerPrincipalId":"owner-3"}', TIMESTAMP WITH TIME ZONE '2026-05-20T12:06:00Z'
            )
            """.trimIndent(),
        ).fetch().rowsUpdated().awaitSingle()

        val items = reader.readWorkspaceEvents(
            workspaceId = "workspace-1",
            targetType = "WORKSPACE_OWNER",
            action = "workspace.owner.add",
            eventType = "MUTATION",
            actorPrincipalId = "owner-1",
            createdAfter = Instant.parse("2026-05-20T12:04:00Z"),
            createdBefore = Instant.parse("2026-05-20T12:07:00Z"),
            cursor = AuditEventCursor(
                createdAt = Instant.parse("2026-05-20T12:06:00Z"),
                id = "audit-manual-2",
            ),
            limit = 1,
        )

        assertEquals(1, items.size)
        assertEquals("audit-manual-1", items.first().id)
    }

    private fun applyLiquibaseBaseline() {
        DriverManager.getConnection(jdbcUrl, "sa", "").use { connection ->
            val database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(liquibase.database.jvm.JdbcConnection(connection))
            Liquibase(
                "db/changelog/db.changelog-master.yaml",
                ClassLoaderResourceAccessor(),
                database,
            ).update(Contexts(), LabelExpression())
        }
    }

    private fun deleteAllRows() = runTest {
        databaseClient.sql("DELETE FROM audit_events").fetch().rowsUpdated().awaitSingle()
    }
}
