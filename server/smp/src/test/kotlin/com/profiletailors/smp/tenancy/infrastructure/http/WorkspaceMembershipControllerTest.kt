package com.profiletailors.smp.tenancy.infrastructure.http

import com.profiletailors.smp.platform.application.Mediator
import com.profiletailors.smp.tenancy.application.UpdateWorkspaceMembershipStatusCommand
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipStatusResult
import com.profiletailors.smp.tenancy.domain.WorkspaceMembershipStatus
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

        override suspend fun <RESPONSE> dispatch(request: com.profiletailors.smp.platform.application.Request<RESPONSE>): RESPONSE {
            lastRequest = request
            @Suppress("UNCHECKED_CAST")
            return result as RESPONSE
        }
    }
}
