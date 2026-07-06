package com.profiletailors.smp.tenancy.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.authorization.domain.AuthorizationDecision
import com.profiletailors.smp.authorization.domain.AuthorizationDecisionResult
import com.profiletailors.smp.authorization.domain.AuthorizationReasonCode
import com.profiletailors.smp.authorization.domain.WorkspaceAuthorizationDecider
import com.profiletailors.smp.tenancy.domain.WorkspaceMutationRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RenameWorkspaceHandlerTest {
    private val resourceContextProvider = mockk<ResourceContextProvider>()
    private val workspaceMutationRepository = mockk<WorkspaceMutationRepository>()
    private val workspaceAuthorizationDecider = mockk<WorkspaceAuthorizationDecider>()
    private val handler = RenameWorkspaceHandler(
        resourceContextProvider,
        workspaceMutationRepository,
        workspaceAuthorizationDecider,
    )

    @Test
    fun `should rename workspace when authorized`() = runBlocking {
        val workspaceId = "ws-1"
        val newName = "New Studio Name"

        mockkStatic("com.profiletailors.smp.tenancy.application.TenancyInternalSupportKt")

        coEvery {
            workspaceAuthorizationDecider.decideDetailed(any(), any(), any())
        } returns AuthorizationDecisionResult(AuthorizationDecision.ALLOW, AuthorizationReasonCode.ROLE_PERMISSION)

        val ctx = ResourceContext(type = ResourceContextType.WORKSPACE, workspaceId = workspaceId)
        every { resourceContextProvider.requireWorkspaceContext() } returns ctx

        coEvery { workspaceMutationRepository.rename(workspaceId, any()) } returns true

        val result = handler.handle(RenameWorkspaceCommand(newName))

        assertEquals(workspaceId, result.workspaceId)
        assertEquals(newName, result.name)
    }

    @Test
    fun `should throw exception when workspace name is blank`() {
        val workspaceId = "ws-1"
        mockkStatic("com.profiletailors.smp.tenancy.application.TenancyInternalSupportKt")

        coEvery { workspaceAuthorizationDecider.decideDetailed(any(), any(), any()) } returns
            AuthorizationDecisionResult(AuthorizationDecision.ALLOW, AuthorizationReasonCode.ROLE_PERMISSION)

        val ctx = ResourceContext(type = ResourceContextType.WORKSPACE, workspaceId = workspaceId)
        every { resourceContextProvider.requireWorkspaceContext() } returns ctx

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { handler.handle(RenameWorkspaceCommand("   ")) }
        }
    }
}
