package com.profiletailors.smp.publishing.infrastructure.http

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.common.domain.workspace.WorkspaceMembershipSnapshot
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import com.profiletailors.smp.authorization.application.WorkspaceMembershipGate
import com.profiletailors.smp.authorization.domain.AuthorizationDeniedException
import com.profiletailors.smp.authorization.domain.WorkspaceMembershipResolver
import com.profiletailors.smp.publishing.domain.PublicationEvent
import com.profiletailors.smp.publishing.domain.PublicationEventType
import com.profiletailors.smp.publishing.infrastructure.events.PublicationEventStreamRegistry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import reactor.core.publisher.Flux
import java.time.Instant

class PublishingPublicationSseControllerTest {

    @Test
    fun `constructs SSE stream scoped to active workspace`() = runTest {
        val controller = PublishingPublicationSseController(
            resourceContextProvider = FixedResourceContextProvider("workspace-1"),
            publicationEventStreamRegistry = FakePublicationEventStreamRegistry(
                listOf(
                    PublicationEvent(
                        type = PublicationEventType.STATUS_CHANGED,
                        workspaceId = "workspace-2",
                        publicationId = "pub-2",
                        occurredAt = Instant.parse("2026-09-25T12:00:00Z"),
                    ),
                    PublicationEvent(
                        type = PublicationEventType.UPDATED,
                        workspaceId = "workspace-1",
                        publicationId = "pub-1",
                        socialAccountId = "account-1",
                        occurredAt = Instant.parse("2026-09-25T12:00:00Z"),
                    ),
                ),
            ),
            membershipGate = WorkspaceMembershipGate(
                fixedPrincipal(),
                memberResolver(WorkspaceMembershipStatus.ACTIVE),
            ),
        )

        val firstEvent = controller.streamEvents().filter { it.event() != "heartbeat" }.blockFirst()

        assertEquals("publication.updated", firstEvent?.event())
        assertEquals("pub-1", firstEvent?.data()?.publicationId)
        assertEquals("workspace-1", firstEvent?.data()?.workspaceId)
    }

    @Test
    fun `denies subscription for authenticated non-members`() = runTest {
        val controller = PublishingPublicationSseController(
            resourceContextProvider = FixedResourceContextProvider("workspace-2"),
            publicationEventStreamRegistry = FakePublicationEventStreamRegistry(emptyList()),
            membershipGate = WorkspaceMembershipGate(fixedPrincipal(), memberResolver(null)),
        )

        assertThrows<AuthorizationDeniedException> {
            controller.streamEvents()
        }
    }

    private class FixedResourceContextProvider(private val workspaceId: String) : ResourceContextProvider {
        override fun current(): ResourceContext = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = workspaceId,
        )
    }

    private class FakePublicationEventStreamRegistry(private val events: List<PublicationEvent>) :
        PublicationEventStreamRegistry {
        override fun stream(): Flux<PublicationEvent> = Flux.fromIterable(events)
    }

    private fun fixedPrincipal(): PrincipalContextProvider = object : PrincipalContextProvider {
        override suspend fun current(): PrincipalContext = PrincipalContext(
            principalId = "principal-1",
            principalType = PrincipalType.USER,
            subject = "local:owner@example.com",
        )
    }

    private fun memberResolver(status: WorkspaceMembershipStatus?): WorkspaceMembershipResolver =
        WorkspaceMembershipResolver { _, resource ->
            status?.let {
                object : WorkspaceMembershipSnapshot {
                    override val id: String = "membership-1"
                    override val workspaceId: String = resource.workspaceId.orEmpty()
                    override val principalId: String = "principal-1"
                    override val principalType: PrincipalType = PrincipalType.USER
                    override val status: WorkspaceMembershipStatus = it
                    override val roleKeys: Set<String> = emptySet()
                }
            }
        }
}
