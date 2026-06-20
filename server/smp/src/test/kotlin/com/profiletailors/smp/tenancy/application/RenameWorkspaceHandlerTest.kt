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
        ) = AuthorizationDecisionResult(
            decision = AuthorizationDecision.ALLOW,
            reasonCode = AuthorizationReasonCode.ROLE_PERMISSION,
            roleKeys = setOf("owner"),
        )
    }

    @Test
    fun `renames workspace successfully`() = runTest {
        val repository = FakeWorkspaceMutationRepository()
        val handler = RenameWorkspaceHandler(resourceContextProvider, repository, allowDecider)

        val result = handler.handle(RenameWorkspaceCommand(newName = "New Name"))

        assertEquals("ws-rname", result.workspaceId)
        assertEquals("New Name", result.name)
    }

    @Test
    fun `trims whitespace from name`() = runTest {
        val repository = FakeWorkspaceMutationRepository()
        val handler = RenameWorkspaceHandler(resourceContextProvider, repository, allowDecider)

        val result = handler.handle(RenameWorkspaceCommand(newName = "  Trimmed  "))

        assertEquals("Trimmed", result.name)
    }

    @Test
    fun `rejects blank name`() = runTest {
        val repository = FakeWorkspaceMutationRepository()
        val handler = RenameWorkspaceHandler(resourceContextProvider, repository, allowDecider)

        val ex = runCatching { handler.handle(RenameWorkspaceCommand(newName = "   ")) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertTrue(ex!!.message!!.contains("cannot be blank"))
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
        val handler = RenameWorkspaceHandler(badContextProvider, repository, allowDecider)

        val ex = runCatching { handler.handle(RenameWorkspaceCommand(newName = "New Name")) }.exceptionOrNull()
        assertTrue(ex is IllegalStateException)
        assertTrue(ex!!.message!!.contains("not found"))
    }

    @Test
    fun `rejects name exceeding max length`() = runTest {
        val repository = FakeWorkspaceMutationRepository()
        val handler = RenameWorkspaceHandler(resourceContextProvider, repository, allowDecider)

        val longName = "x".repeat(256)
        val ex = runCatching { handler.handle(RenameWorkspaceCommand(newName = longName)) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertTrue(ex!!.message!!.contains("cannot exceed"))
    }

    private class FakeWorkspaceMutationRepository : WorkspaceMutationRepository {
        private val workspaces = mutableMapOf(
            "ws-rname" to "Original Name",
        )

        override suspend fun rename(workspaceId: String, newName: String): Boolean {
            if (!workspaces.containsKey(workspaceId)) return false
            workspaces[workspaceId] = newName
            return true
        }

        override suspend fun updateIcon(workspaceId: String, icon: String?): Boolean {
            return workspaces.containsKey(workspaceId)
        }
    }
}
