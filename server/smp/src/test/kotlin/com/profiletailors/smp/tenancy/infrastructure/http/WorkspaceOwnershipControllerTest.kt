package com.profiletailors.smp.tenancy.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.PublishStrategy
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.query.Query
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

    private class CapturingMediator(private val result: WorkspaceOwnershipResult) : Mediator {
        var lastRequest: Any? = null

        override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse =
            error("Not used in this test")

        override suspend fun <TCommand : Command> send(command: TCommand) {
            error("Not used in this test")
        }

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult {
            lastRequest = command
            return result as TResult
        }

        override suspend fun <T : Notification> publish(notification: T) {
            error("Not used in this test")
        }

        override suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy) {
            error("Not used in this test")
        }
    }
}
