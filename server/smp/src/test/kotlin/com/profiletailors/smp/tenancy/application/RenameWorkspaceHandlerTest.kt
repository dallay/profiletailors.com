package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.authorization.domain.WorkspaceAuthorizationDecider
import io.r2dbc.h2.H2ConnectionConfiguration
import io.r2dbc.h2.H2ConnectionFactory
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import java.util.UUID

class RenameWorkspaceHandlerTest {

    private val workspaceContext = ResourceContext(
        type = ResourceContextType.WORKSPACE,
        workspaceId = "ws-rname",
    )

    private val resourceContextProvider = object : ResourceContextProvider {
        override fun current(): ResourceContext = workspaceContext
    }

    private val allowDecider = object : WorkspaceAuthorizationDecider {
        override suspend fun decide(
            requiredPermission: PermissionKey,
            requiredEntitlementKey: String?,
            resourceContextOverride: ResourceContext?,
        ) = AuthorizationDecision.ALLOW

        override suspend fun decideDetailed(
            requiredPermission: PermissionKey,
            requiredEntitlementKey: String?,
            resourceContextOverride: ResourceContext?,
        ) = com.profiletailors.smp.authorization.domain.AuthorizationDecisionResult(
            decision = AuthorizationDecision.ALLOW,
            reasonCode = com.profiletailors.smp.authorization.domain.AuthorizationReasonCode.ROLE_PERMISSION,
            roleKeys = setOf("owner"),
        )
    }

    private suspend fun createDb(): DatabaseClient {
        val uid = UUID.randomUUID().toString().substring(0, 8)
        val r2dbcUrl = "r2dbc:h2:mem:///rname_$uid?options=MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"

        val connectionFactory = H2ConnectionFactory(
            H2ConnectionConfiguration.builder()
                .url(r2dbcUrl)
                .username("sa")
                .password("")
                .build(),
        )

        // Create schema and seed data via R2DBC
        val db = DatabaseClient.create(connectionFactory)
        db.sql(
            """CREATE TABLE workspaces (
                id VARCHAR(36) PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                icon VARCHAR(64) NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )""",
        ).then().awaitSingleOrNull()

        db.sql("INSERT INTO workspaces (id, name, status, icon) VALUES ('ws-rname', 'Original Name', 'ACTIVE', NULL)")
            .then().awaitSingleOrNull()

        return db
    }

    @Test
    fun `renames workspace successfully`() = runTest {
        val db = createDb()
        val handler = RenameWorkspaceHandler(resourceContextProvider, db, allowDecider)

        val result = handler.handle(RenameWorkspaceCommand(newName = "New Name"))

        assertEquals("ws-rname", result.workspaceId)
        assertEquals("New Name", result.name)
    }

    @Test
    fun `trims whitespace from name`() = runTest {
        val db = createDb()
        val handler = RenameWorkspaceHandler(resourceContextProvider, db, allowDecider)

        val result = handler.handle(RenameWorkspaceCommand(newName = "  Trimmed  "))

        assertEquals("Trimmed", result.name)
    }

    @Test
    fun `rejects blank name`() = runTest {
        val db = createDb()
        val handler = RenameWorkspaceHandler(resourceContextProvider, db, allowDecider)

        val ex = runCatching { handler.handle(RenameWorkspaceCommand(newName = "   ")) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertTrue(ex!!.message!!.contains("cannot be blank"))
    }

    @Test
    fun `throws on non-existent workspace`() = runTest {
        val db = createDb()
        val badContext = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = "i-dont-exist",
        )
        val badContextProvider = object : ResourceContextProvider {
            override fun current(): ResourceContext = badContext
        }
        val handler = RenameWorkspaceHandler(badContextProvider, db, allowDecider)

        val ex = runCatching { handler.handle(RenameWorkspaceCommand(newName = "New Name")) }.exceptionOrNull()
        assertTrue(ex is IllegalStateException)
        assertTrue(ex!!.message!!.contains("not found"))
    }

    @Test
    fun `rejects name exceeding max length`() = runTest {
        val db = createDb()
        val handler = RenameWorkspaceHandler(resourceContextProvider, db, allowDecider)

        val longName = "x".repeat(256)
        val ex = runCatching { handler.handle(RenameWorkspaceCommand(newName = longName)) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertTrue(ex!!.message!!.contains("cannot exceed"))
    }
}
