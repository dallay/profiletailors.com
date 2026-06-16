package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.authorization.domain.WorkspaceAuthorizationDecider
import io.r2dbc.h2.H2ConnectionConfiguration
import io.r2dbc.h2.H2ConnectionFactory
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import java.util.UUID

class UpdateWorkspaceIconHandlerTest {

    private val workspaceContext = ResourceContext(
        type = ResourceContextType.WORKSPACE,
        workspaceId = "ws-icon",
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

    private val denyDecider = object : WorkspaceAuthorizationDecider {
        override suspend fun decide(
            requiredPermission: PermissionKey,
            requiredEntitlementKey: String?,
            resourceContextOverride: ResourceContext?,
        ) = AuthorizationDecision.DENY

        override suspend fun decideDetailed(
            requiredPermission: PermissionKey,
            requiredEntitlementKey: String?,
            resourceContextOverride: ResourceContext?,
        ) = com.profiletailors.smp.authorization.domain.AuthorizationDecisionResult(
            decision = AuthorizationDecision.DENY,
            reasonCode = com.profiletailors.smp.authorization.domain.AuthorizationReasonCode.MISSING_PERMISSION,
            roleKeys = emptySet(),
        )
    }

    private suspend fun createDb(): DatabaseClient {
        val uid = UUID.randomUUID().toString().substring(0, 8)
        val r2dbcUrl = "r2dbc:h2:mem:///wicon_$uid?options=MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"

        val connectionFactory = H2ConnectionFactory(
            H2ConnectionConfiguration.builder()
                .url(r2dbcUrl)
                .username("sa")
                .password("")
                .build(),
        )

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

        db.sql("INSERT INTO workspaces (id, name, status, icon) VALUES ('ws-icon', 'Test Workspace', 'ACTIVE', NULL)")
            .then().awaitSingleOrNull()

        return db
    }

    @Test
    fun `sets icon successfully`() = runTest {
        val db = createDb()
        val handler = UpdateWorkspaceIconHandler(resourceContextProvider, db, allowDecider)

        val result = handler.handle(UpdateWorkspaceIconCommand(icon = "briefcase"))

        assertEquals("ws-icon", result.workspaceId)
        assertEquals("briefcase", result.icon)
    }

    @Test
    fun `removes icon by setting null`() = runTest {
        val db = createDb()
        // First set an icon
        val handler = UpdateWorkspaceIconHandler(resourceContextProvider, db, allowDecider)
        handler.handle(UpdateWorkspaceIconCommand(icon = "rocket"))

        // Then remove it
        val result = handler.handle(UpdateWorkspaceIconCommand(icon = null))

        assertEquals("ws-icon", result.workspaceId)
        assertNull(result.icon)
    }

    @Test
    fun `accepts single-character icon name`() = runTest {
        val db = createDb()
        val handler = UpdateWorkspaceIconHandler(resourceContextProvider, db, allowDecider)

        val result = handler.handle(UpdateWorkspaceIconCommand(icon = "x"))

        assertEquals("x", result.icon)
    }

    @Test
    fun `accepts hyphenated icon name`() = runTest {
        val db = createDb()
        val handler = UpdateWorkspaceIconHandler(resourceContextProvider, db, allowDecider)

        val result = handler.handle(UpdateWorkspaceIconCommand(icon = "trending-up"))

        assertEquals("trending-up", result.icon)
    }

    @Test
    fun `rejects icon name with consecutive hyphens`() = runTest {
        val db = createDb()
        val handler = UpdateWorkspaceIconHandler(resourceContextProvider, db, allowDecider)

        val ex = runCatching { handler.handle(UpdateWorkspaceIconCommand(icon = "a--b")) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertTrue(ex!!.message!!.contains("Invalid icon name"))
    }

    @Test
    fun `rejects icon name starting with hyphen`() = runTest {
        val db = createDb()
        val handler = UpdateWorkspaceIconHandler(resourceContextProvider, db, allowDecider)

        val ex = runCatching { handler.handle(UpdateWorkspaceIconCommand(icon = "-briefcase")) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `rejects icon name ending with hyphen`() = runTest {
        val db = createDb()
        val handler = UpdateWorkspaceIconHandler(resourceContextProvider, db, allowDecider)

        val ex = runCatching { handler.handle(UpdateWorkspaceIconCommand(icon = "briefcase-")) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `rejects uppercase icon name`() = runTest {
        val db = createDb()
        val handler = UpdateWorkspaceIconHandler(resourceContextProvider, db, allowDecider)

        val ex = runCatching { handler.handle(UpdateWorkspaceIconCommand(icon = "Briefcase")) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `rejects icon name with spaces`() = runTest {
        val db = createDb()
        val handler = UpdateWorkspaceIconHandler(resourceContextProvider, db, allowDecider)

        val ex = runCatching { handler.handle(UpdateWorkspaceIconCommand(icon = "brief case")) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
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
        val handler = UpdateWorkspaceIconHandler(badContextProvider, db, allowDecider)

        val ex = runCatching { handler.handle(UpdateWorkspaceIconCommand(icon = "rocket")) }.exceptionOrNull()
        assertTrue(ex is IllegalStateException)
        assertTrue(ex!!.message!!.contains("not found"))
    }

    @Test
    fun `denies access when authorization fails`() = runTest {
        val db = createDb()
        val handler = UpdateWorkspaceIconHandler(resourceContextProvider, db, denyDecider)

        val ex = runCatching { handler.handle(UpdateWorkspaceIconCommand(icon = "rocket")) }.exceptionOrNull()
        assertTrue(ex is AuthorizationDeniedException)
    }
}
