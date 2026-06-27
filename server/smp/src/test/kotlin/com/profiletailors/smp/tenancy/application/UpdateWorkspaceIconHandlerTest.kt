package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.AuthorizationDecisionResult
import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import com.profiletailors.smp.authorization.domain.AuthorizationReasonCode
import com.profiletailors.smp.authorization.domain.PermissionKey
import com.profiletailors.smp.authorization.domain.WorkspaceAuthorizationDecider
import com.profiletailors.smp.tenancy.domain.WorkspaceMutationRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
        ) = AuthorizationDecisionResult(
            decision = AuthorizationDecision.ALLOW,
            reasonCode = AuthorizationReasonCode.ROLE_PERMISSION,
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
        ) = AuthorizationDecisionResult(
            decision = AuthorizationDecision.DENY,
            reasonCode = AuthorizationReasonCode.MISSING_PERMISSION,
            roleKeys = emptySet(),
        )
    }

    @Test
    fun `sets icon successfully`() = runTest {
        val repository = FakeWorkspaceMutationRepository()
        val handler = UpdateWorkspaceIconHandler(resourceContextProvider, repository, allowDecider)

        val result = handler.handle(UpdateWorkspaceIconCommand(icon = "briefcase"))

        assertEquals("ws-icon", result.workspaceId)
        assertEquals("briefcase", result.icon)
    }

    @Test
    fun `removes icon by setting null`() = runTest {
        val repository = FakeWorkspaceMutationRepository()
        // First set an icon
        val handler = UpdateWorkspaceIconHandler(resourceContextProvider, repository, allowDecider)
        handler.handle(UpdateWorkspaceIconCommand(icon = "rocket"))

        // Then remove it
        val result = handler.handle(UpdateWorkspaceIconCommand(icon = null))

        assertEquals("ws-icon", result.workspaceId)
        assertNull(result.icon)
    }

    @Test
    fun `accepts single-character icon name`() = runTest {
        val repository = FakeWorkspaceMutationRepository()
        val handler = UpdateWorkspaceIconHandler(resourceContextProvider, repository, allowDecider)

        val result = handler.handle(UpdateWorkspaceIconCommand(icon = "x"))

        assertEquals("x", result.icon)
    }

    @Test
    fun `accepts hyphenated icon name`() = runTest {
        val repository = FakeWorkspaceMutationRepository()
        val handler = UpdateWorkspaceIconHandler(resourceContextProvider, repository, allowDecider)

        val result = handler.handle(UpdateWorkspaceIconCommand(icon = "trending-up"))

        assertEquals("trending-up", result.icon)
    }

    @Test
    fun `rejects icon name with consecutive hyphens`() = runTest {
        val repository = FakeWorkspaceMutationRepository()
        val handler = UpdateWorkspaceIconHandler(resourceContextProvider, repository, allowDecider)

        val ex = runCatching { handler.handle(UpdateWorkspaceIconCommand(icon = "a--b")) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertTrue(ex!!.message!!.contains("Invalid icon name"))
    }

    @Test
    fun `rejects icon name starting with hyphen`() = runTest {
        val repository = FakeWorkspaceMutationRepository()
        val handler = UpdateWorkspaceIconHandler(resourceContextProvider, repository, allowDecider)

        val ex = runCatching { handler.handle(UpdateWorkspaceIconCommand(icon = "-briefcase")) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `rejects icon name ending with hyphen`() = runTest {
        val repository = FakeWorkspaceMutationRepository()
        val handler = UpdateWorkspaceIconHandler(resourceContextProvider, repository, allowDecider)

        val ex = runCatching { handler.handle(UpdateWorkspaceIconCommand(icon = "briefcase-")) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `rejects uppercase icon name`() = runTest {
        val repository = FakeWorkspaceMutationRepository()
        val handler = UpdateWorkspaceIconHandler(resourceContextProvider, repository, allowDecider)

        val ex = runCatching { handler.handle(UpdateWorkspaceIconCommand(icon = "Briefcase")) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `rejects icon name with spaces`() = runTest {
        val repository = FakeWorkspaceMutationRepository()
        val handler = UpdateWorkspaceIconHandler(resourceContextProvider, repository, allowDecider)

        val ex = runCatching { handler.handle(UpdateWorkspaceIconCommand(icon = "brief case")) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `throws on non-existent workspace`() = runTest {
        val badContext = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = "i-dont-exist",
        )
        val badContextProvider = object : ResourceContextProvider {
            override fun current(): ResourceContext = badContext
        }
        val repository = FakeWorkspaceMutationRepository()
        val handler = UpdateWorkspaceIconHandler(badContextProvider, repository, allowDecider)

        val ex = runCatching { handler.handle(UpdateWorkspaceIconCommand(icon = "rocket")) }.exceptionOrNull()
        assertTrue(ex is IllegalStateException)
        assertTrue(ex!!.message!!.contains("not found"))
    }

    @Test
    fun `denies access when authorization fails`() = runTest {
        val repository = FakeWorkspaceMutationRepository()
        val handler = UpdateWorkspaceIconHandler(resourceContextProvider, repository, denyDecider)

        val ex = runCatching { handler.handle(UpdateWorkspaceIconCommand(icon = "rocket")) }.exceptionOrNull()
        assertTrue(ex is AuthorizationDeniedException)
    }

    private class FakeWorkspaceMutationRepository : WorkspaceMutationRepository {
        private val icons = mutableMapOf<String, String?>("ws-icon" to null)

        override suspend fun rename(workspaceId: String, newName: String): Boolean = icons.containsKey(workspaceId)

        override suspend fun updateIcon(workspaceId: String, icon: String?): Boolean {
            if (!icons.containsKey(workspaceId)) return false
            icons[workspaceId] = icon
            return true
        }
    }
}
