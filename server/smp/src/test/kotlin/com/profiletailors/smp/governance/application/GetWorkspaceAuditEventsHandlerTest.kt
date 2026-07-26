package com.profiletailors.smp.governance.application

import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import com.profiletailors.smp.governance.domain.AuditEventCursor
import com.profiletailors.smp.governance.domain.AuditEventCursorCodec
import com.profiletailors.smp.governance.domain.AuditEventFilter
import com.profiletailors.smp.governance.domain.AuditEventItem
import com.profiletailors.smp.governance.domain.AuditEventPageRequest
import com.profiletailors.smp.governance.domain.AuditEventReader
import com.profiletailors.smp.governance.domain.InvalidAuditEventCursorException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class GetWorkspaceAuditEventsHandlerTest {

    private val authorizationService: GovernanceAuthorizationService = mockk()

    @Test
    fun `reads workspace audit events with filters normalized limit and page metadata`() = runTest {
        val createdAfter = Instant.parse("2026-05-20T11:00:00Z")
        val createdBefore = Instant.parse("2026-05-20T13:00:00Z")
        val handler = GetWorkspaceAuditEventsHandler(
            resourceContextProvider = object : ResourceContextProvider {
                override fun current(): ResourceContext = ResourceContext(
                    type = ResourceContextType.WORKSPACE,
                    workspaceId = "workspace-1",
                )
            },
            auditEventReader = object : AuditEventReader {
                override suspend fun readWorkspaceEvents(
                    workspaceId: String,
                    filter: AuditEventFilter,
                    pageRequest: AuditEventPageRequest,
                ): List<AuditEventItem> {
                    assertEquals("workspace-1", workspaceId)
                    assertEquals("WORKSPACE_OWNER", filter.targetType)
                    assertEquals("workspace.owner.add", filter.action)
                    assertEquals("MUTATION", filter.eventType)
                    assertEquals("owner-1", filter.actorPrincipalId)
                    assertEquals(Instant.parse("2026-05-20T11:00:00Z"), filter.createdAfter)
                    assertEquals(Instant.parse("2026-05-20T13:00:00Z"), filter.createdBefore)
                    assertEquals(
                        AuditEventCursor(
                            createdAt = Instant.parse("2026-05-20T12:00:00Z"),
                            id = "audit-5",
                        ),
                        pageRequest.cursor,
                    )
                    assertEquals(4, pageRequest.limit)
                    return listOf(
                        auditItem("audit-3"),
                        auditItem("audit-2"),
                        auditItem("audit-1"),
                        auditItem("audit-0"),
                    )
                }
            },
            authorizationService = authorizationService,
        )

        coEvery { authorizationService.authorizeAuditRead() } returns Unit

        val response = handler.handle(
            GetWorkspaceAuditEventsQuery(
                targetType = "WORKSPACE_OWNER",
                action = "workspace.owner.add",
                eventType = "MUTATION",
                actorPrincipalId = "owner-1",
                createdAfter = createdAfter,
                createdBefore = createdBefore,
                cursor = AuditEventCursorCodec.encode(
                    AuditEventCursor(
                        createdAt = Instant.parse("2026-05-20T12:00:00Z"),
                        id = "audit-5",
                    ),
                ),
                limit = 3,
            ),
        )

        assertEquals("workspace-1", response.workspaceId)
        assertEquals(3, response.items.size)
        assertEquals("audit-3", response.items.first().id)
        assertEquals(
            AuditEventCursorCodec.encode(
                AuditEventCursor(
                    createdAt = Instant.parse("2026-05-20T12:00:00Z"),
                    id = "audit-5",
                ),
            ),
            response.page.cursor,
        )
        assertEquals(3, response.page.limit)
        assertEquals(3, response.page.returned)
        assertTrue(response.page.hasMore)
        assertEquals(
            AuditEventCursorCodec.encode(
                AuditEventCursor(
                    createdAt = Instant.parse("2026-05-20T12:00:00Z"),
                    id = "audit-1",
                ),
            ),
            response.page.nextCursor,
        )
    }

    @Test
    fun `page metadata reports no more items when reader returns within limit`() = runTest {
        val handler = GetWorkspaceAuditEventsHandler(
            resourceContextProvider = object : ResourceContextProvider {
                override fun current(): ResourceContext = ResourceContext(
                    type = ResourceContextType.WORKSPACE,
                    workspaceId = "workspace-1",
                )
            },
            auditEventReader = object : AuditEventReader {
                override suspend fun readWorkspaceEvents(
                    workspaceId: String,
                    filter: AuditEventFilter,
                    pageRequest: AuditEventPageRequest,
                ): List<AuditEventItem> = listOf(auditItem("audit-1"))
            },
            authorizationService = authorizationService,
        )

        coEvery { authorizationService.authorizeAuditRead() } returns Unit

        val response = handler.handle(GetWorkspaceAuditEventsQuery(limit = 10))

        assertEquals(1, response.page.returned)
        assertFalse(response.page.hasMore)
        assertEquals(null, response.page.nextCursor)
    }

    @Test
    fun `denies workspace audit events when cursor is invalid`() = runTest {
        val handler = GetWorkspaceAuditEventsHandler(
            resourceContextProvider = object : ResourceContextProvider {
                override fun current(): ResourceContext = ResourceContext(
                    type = ResourceContextType.WORKSPACE,
                    workspaceId = "workspace-1",
                )
            },
            auditEventReader = object : AuditEventReader {
                override suspend fun readWorkspaceEvents(
                    workspaceId: String,
                    filter: AuditEventFilter,
                    pageRequest: AuditEventPageRequest,
                ): List<AuditEventItem> = emptyList()
            },
            authorizationService = authorizationService,
        )

        coEvery { authorizationService.authorizeAuditRead() } returns Unit

        assertThrows(InvalidAuditEventCursorException::class.java) {
            kotlinx.coroutines.runBlocking {
                handler.handle(GetWorkspaceAuditEventsQuery(cursor = "%%%"))
            }
        }
    }

    @Test
    fun `denies workspace audit events when permission is missing`() = runTest {
        val handler = GetWorkspaceAuditEventsHandler(
            resourceContextProvider = object : ResourceContextProvider {
                override fun current(): ResourceContext = ResourceContext(
                    type = ResourceContextType.WORKSPACE,
                    workspaceId = "workspace-1",
                )
            },
            auditEventReader = object : AuditEventReader {
                override suspend fun readWorkspaceEvents(
                    workspaceId: String,
                    filter: AuditEventFilter,
                    pageRequest: AuditEventPageRequest,
                ): List<AuditEventItem> = emptyList()
            },
            authorizationService = authorizationService,
        )

        coEvery { authorizationService.authorizeAuditRead() } throws AuthorizationDeniedException("Denied")

        assertThrows(AuthorizationDeniedException::class.java) {
            kotlinx.coroutines.runBlocking { handler.handle(GetWorkspaceAuditEventsQuery()) }
        }
    }

    private fun auditItem(id: String) = AuditEventItem(
        id = id,
        eventType = "MUTATION",
        action = "workspace.owner.add",
        requestName = null,
        requestPath = null,
        permission = null,
        actorPrincipalId = "owner-1",
        workspaceId = "workspace-1",
        targetType = "WORKSPACE_OWNER",
        targetId = "owner-2",
        outcome = "SUCCESS",
        reasonCode = null,
        roleKeys = emptyList(),
        details = mapOf("ownerPrincipalId" to "owner-2"),
        createdAt = Instant.parse("2026-05-20T12:00:00Z"),
    )
}
