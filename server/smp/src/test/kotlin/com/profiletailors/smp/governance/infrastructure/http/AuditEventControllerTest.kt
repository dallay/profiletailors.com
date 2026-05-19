package com.profiletailors.smp.governance.infrastructure.http

import com.profiletailors.smp.governance.application.AuditEventPage
import com.profiletailors.smp.governance.application.GetWorkspaceAuditEventsQuery
import com.profiletailors.smp.governance.application.WorkspaceAuditEventsResponse
import com.profiletailors.smp.platform.application.Mediator
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class AuditEventControllerTest {

    @Test
    fun `dispatches workspace audit event query with pagination`() = runTest {
        val mediator = CapturingMediator(
            WorkspaceAuditEventsResponse(
                workspaceId = "workspace-1",
                items = emptyList(),
                page = AuditEventPage(
                    cursor = "cursor-token",
                    limit = 10,
                    returned = 0,
                    hasMore = false,
                    nextCursor = null,
                ),
            ),
        )
        val controller = AuditEventController(mediator)
        val createdAfter = Instant.parse("2026-05-20T11:00:00Z")
        val createdBefore = Instant.parse("2026-05-20T13:00:00Z")

        val response = controller.listWorkspaceAuditEvents(
            targetType = "WORKSPACE_OWNER",
            action = "workspace.owner.add",
            eventType = "MUTATION",
            actorPrincipalId = "owner-1",
            createdAfter = createdAfter,
            createdBefore = createdBefore,
            cursor = "cursor-token",
            limit = 10,
        )

        assertEquals("workspace-1", response.workspaceId)
        assertEquals(
            GetWorkspaceAuditEventsQuery(
                targetType = "WORKSPACE_OWNER",
                action = "workspace.owner.add",
                eventType = "MUTATION",
                actorPrincipalId = "owner-1",
                createdAfter = createdAfter,
                createdBefore = createdBefore,
                cursor = "cursor-token",
                limit = 10,
            ),
            mediator.lastRequest,
        )
    }

    private class CapturingMediator(
        private val result: WorkspaceAuditEventsResponse,
    ) : Mediator {
        var lastRequest: Any? = null

        override suspend fun <RESPONSE> dispatch(request: com.profiletailors.smp.platform.application.Request<RESPONSE>): RESPONSE {
            lastRequest = request
            @Suppress("UNCHECKED_CAST")
            return result as RESPONSE
        }
    }
}
