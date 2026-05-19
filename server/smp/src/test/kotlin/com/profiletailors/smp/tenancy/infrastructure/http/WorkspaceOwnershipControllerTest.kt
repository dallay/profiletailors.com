package com.profiletailors.smp.tenancy.infrastructure.http

import com.profiletailors.smp.platform.application.Mediator
import com.profiletailors.smp.tenancy.application.AddWorkspaceOwnerCommand
import com.profiletailors.smp.tenancy.application.RemoveWorkspaceOwnerCommand
import com.profiletailors.smp.tenancy.application.TransferWorkspaceOwnershipCommand
import com.profiletailors.smp.tenancy.application.WorkspaceOwnershipResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorkspaceOwnershipControllerTest {

    @Test
    fun `dispatches add workspace owner command`() = runTest {
        val mediator = CapturingMediator(
            result = WorkspaceOwnershipResult("workspace-1", listOf("owner-1", "owner-2")),
        )
        val controller = WorkspaceOwnershipController(mediator)

        val response = controller.addOwner(WorkspaceOwnerRequest(principalId = "owner-2"))

        assertEquals(WorkspaceOwnershipResult("workspace-1", listOf("owner-1", "owner-2")), response)
        assertEquals(AddWorkspaceOwnerCommand(targetPrincipalId = "owner-2"), mediator.lastRequest)
    }

    @Test
    fun `dispatches remove workspace owner command`() = runTest {
        val mediator = CapturingMediator(
            result = WorkspaceOwnershipResult("workspace-1", listOf("owner-2")),
        )
        val controller = WorkspaceOwnershipController(mediator)

        val response = controller.removeOwner("owner-1")

        assertEquals(WorkspaceOwnershipResult("workspace-1", listOf("owner-2")), response)
        assertEquals(RemoveWorkspaceOwnerCommand(targetPrincipalId = "owner-1"), mediator.lastRequest)
    }

    @Test
    fun `dispatches transfer workspace ownership command`() = runTest {
        val mediator = CapturingMediator(
            result = WorkspaceOwnershipResult("workspace-1", listOf("owner-2")),
        )
        val controller = WorkspaceOwnershipController(mediator)

        val response = controller.transferOwnership(WorkspaceOwnerRequest(principalId = "owner-2"))

        assertEquals(WorkspaceOwnershipResult("workspace-1", listOf("owner-2")), response)
        assertEquals(TransferWorkspaceOwnershipCommand(targetPrincipalId = "owner-2"), mediator.lastRequest)
    }

    private class CapturingMediator(
        private val result: WorkspaceOwnershipResult,
    ) : Mediator {
        var lastRequest: Any? = null

        override suspend fun <RESPONSE> dispatch(request: com.profiletailors.smp.platform.application.Request<RESPONSE>): RESPONSE {
            lastRequest = request
            @Suppress("UNCHECKED_CAST")
            return result as RESPONSE
        }
    }
}
