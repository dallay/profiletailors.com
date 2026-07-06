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

class UpdateWorkspaceIconHandlerTest {
    private val resourceContextProvider = mockk<ResourceContextProvider>()
    private val workspaceMutationRepository = mockk<WorkspaceMutationRepository>()
    private val workspaceAuthorizationDecider = mockk<WorkspaceAuthorizationDecider>()
    private val handler = UpdateWorkspaceIconHandler(
        resourceContextProvider,
        workspaceMutationRepository,
        workspaceAuthorizationDecider,
    )

    @Test
    fun `should update icon when authorized`() = runBlocking {
        val workspaceId = "ws-1"
        val icon = "rocket-ship"

        mockkStatic("com.profiletailors.smp.tenancy.application.TenancyInternalSupportKt")

        coEvery {
            workspaceAuthorizationDecider.decideDetailed(any(), any(), any())
        } returns AuthorizationDecisionResult(AuthorizationDecision.ALLOW, AuthorizationReasonCode.ROLE_PERMISSION)

        val ctx = ResourceContext(type = ResourceContextType.WORKSPACE, workspaceId = workspaceId)
        every { resourceContextProvider.requireWorkspaceContext() } returns ctx

        coEvery { workspaceMutationRepository.updateIcon(workspaceId, any()) } returns true

        val result = handler.handle(UpdateWorkspaceIconCommand(icon))

        assertEquals(workspaceId, result.workspaceId)
        assertEquals(icon, result.icon)
    }

    @Test
    fun `should throw exception when icon name is invalid`() {
        val workspaceId = "ws-1"
        mockkStatic("com.profiletailors.smp.tenancy.application.TenancyInternalSupportKt")

        coEvery { workspaceAuthorizationDecider.decideDetailed(any(), any(), any()) } returns
            AuthorizationDecisionResult(AuthorizationDecision.ALLOW, AuthorizationReasonCode.ROLE_PERMISSION)

        val ctx = ResourceContext(type = ResourceContextType.WORKSPACE, workspaceId = workspaceId)
        every { resourceContextProvider.requireWorkspaceContext() } returns ctx

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { handler.handle(UpdateWorkspaceIconCommand("Invalid_Icon!")) }
        }
    }
}
