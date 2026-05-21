package com.profiletailors.smp.tenancy.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.PublishStrategy
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import com.profiletailors.smp.tenancy.application.UpdateWorkspaceMembershipStatusCommand
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipStatusResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorkspaceMembershipControllerTest {

    @Test
    fun `dispatches membership status update command`() = runTest {
        val mediator = CapturingMediator(
            WorkspaceMembershipStatusResult(
                workspaceId = "workspace-1",
                principalId = "member-2",
                status = WorkspaceMembershipStatus.SUSPENDED,
            ),
        )
        val controller = WorkspaceMembershipController(mediator)

        val response = controller.updateMembershipStatus(
            principalId = "member-2",
            request = WorkspaceMembershipStatusRequest(status = "SUSPENDED"),
        )

        assertEquals("workspace-1", response.workspaceId)
        assertEquals(
            UpdateWorkspaceMembershipStatusCommand(
                targetPrincipalId = "member-2",
                targetStatus = WorkspaceMembershipStatus.SUSPENDED,
            ),
            mediator.lastRequest,
        )
    }

    private class CapturingMediator(
        private val result: WorkspaceMembershipStatusResult,
    ) : Mediator {
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
