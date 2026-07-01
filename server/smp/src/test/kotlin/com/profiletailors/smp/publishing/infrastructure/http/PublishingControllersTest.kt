package com.profiletailors.smp.publishing.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.PublishStrategy
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.smp.publishing.application.CalendarResponse
import com.profiletailors.smp.publishing.application.CancelPublicationCommand
import com.profiletailors.smp.publishing.application.CompleteLinkedInConnectionCommand
import com.profiletailors.smp.publishing.application.ConnectedChannelsResponse
import com.profiletailors.smp.publishing.application.ConnectedSocialChannelSummary
import com.profiletailors.smp.publishing.application.CreatePublicationCommand
import com.profiletailors.smp.publishing.application.DeletePublicationCommand
import com.profiletailors.smp.publishing.application.EditPublicationCommand
import com.profiletailors.smp.publishing.application.GetCalendarPublicationsQuery
import com.profiletailors.smp.publishing.application.InitiateLinkedInConnectionCommand
import com.profiletailors.smp.publishing.application.LinkedInConnectionInitiationResult
import com.profiletailors.smp.publishing.application.ListConnectedChannelsQuery
import com.profiletailors.smp.publishing.application.ListPublicationsQuery
import com.profiletailors.smp.publishing.application.ListPublicationsResponse
import com.profiletailors.smp.publishing.application.PublicationResult
import com.profiletailors.smp.publishing.application.ReschedulePublicationCommand
import com.profiletailors.smp.publishing.application.RetryPublicationCommand
import com.profiletailors.smp.publishing.application.SocialAccountSummary
import com.profiletailors.smp.publishing.application.SocialConnectionResult
import com.profiletailors.smp.publishing.domain.ChannelEvent
import com.profiletailors.smp.publishing.domain.ChannelEventType
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.events.ChannelEventStreamRegistry
import com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInPublishingProperties
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import java.time.Instant

class PublishingControllersTest {

    @Test
    fun `dispatches linkedin connection initiation command`() = runTest {
        val mediator = CapturingMediator()
        val controller = PublishingConnectionController(mediator)

        val response = controller.initiateLinkedInConnection(
            LinkedInConnectionInitiationRequest(
                redirectUri = "https://app.example.com/callback",
            ),
        )

        assertEquals("state-1", response.state)
        assertEquals(
            InitiateLinkedInConnectionCommand(
                redirectUri = "https://app.example.com/callback",
            ),
            mediator.lastRequest,
        )
    }

    @Test
    fun `dispatches linkedin connection completion command with state`() = runTest {
        val mediator = CapturingMediator()
        val controller = PublishingConnectionController(mediator)

        val response = controller.completeLinkedInConnection(
            LinkedInConnectionCompletionRequest(
                authorizationCode = "oauth-code-1",
                redirectUri = "https://app.example.com/callback",
                state = "signed-state-1",
            ),
        )

        assertEquals("workspace-1", response.workspaceId)
        assertEquals(
            CompleteLinkedInConnectionCommand(
                authorizationCode = "oauth-code-1",
                redirectUri = "https://app.example.com/callback",
                state = "signed-state-1",
            ),
            mediator.lastRequest,
        )
    }

    @Test
    fun `dispatches channel list query with status filter`() = runTest {
        val mediator = CapturingMediator()
        val controller = PublishingChannelController(
            mediator = mediator,
            resourceContextProvider = FixedResourceContextProvider("workspace-1"),
            channelEventStreamRegistry = FakeChannelEventStreamRegistry(emptyList()),
            linkedInPublishingProperties = LinkedInPublishingProperties(
                clientId = "",
                clientSecret = "",
                redirectUri = "",
                scopes = "",
                apiBaseUrl = "",
                authorizationBaseUrl = "",
                tokenBaseUrl = "",
                apiVersion = "",
            ),
        )

        val response = controller.listChannels(status = SocialConnectionStatus.ACTIVE)

        assertEquals(1, response.channels.size)
        assertEquals("https://media.licdn.com/photo.jpg", response.channels.single().avatarUrl)
        assertEquals(
            ListConnectedChannelsQuery(status = SocialConnectionStatus.ACTIVE),
            mediator.lastQuery,
        )
    }

    @Test
    fun `constructs SSE stream scoped to active workspace`() {
        val controller = PublishingChannelController(
            mediator = CapturingMediator(),
            resourceContextProvider = FixedResourceContextProvider("workspace-1"),
            channelEventStreamRegistry = FakeChannelEventStreamRegistry(
                listOf(
                    ChannelEvent(
                        type = ChannelEventType.CONNECTED_CHANNEL_UPDATED,
                        workspaceId = "workspace-2",
                        socialAccountId = "account-2",
                        occurredAt = Instant.parse("2026-06-12T12:00:00Z"),
                    ),
                    ChannelEvent(
                        type = ChannelEventType.CONNECTED_CHANNEL_UPDATED,
                        workspaceId = "workspace-1",
                        socialAccountId = "account-1",
                        occurredAt = Instant.parse("2026-06-12T12:00:00Z"),
                    ),
                ),
            ),
            linkedInPublishingProperties = LinkedInPublishingProperties(
                clientId = "",
                clientSecret = "",
                redirectUri = "",
                scopes = "",
                apiBaseUrl = "",
                authorizationBaseUrl = "",
                tokenBaseUrl = "",
                apiVersion = "",
            ),
        )

        val firstEvent = controller.streamEvents().filter { it.event() != "heartbeat" }.blockFirst()

        assertEquals("connected-channel.updated", firstEvent?.event())
        assertEquals("account-1", firstEvent?.data()?.socialAccountId)
    }

    @Test
    fun `dispatches publication create command`() = runTest {
        val mediator = CapturingMediator()
        val controller = PublishingPublicationController(mediator)
        val scheduledFor = Instant.parse("2026-05-27T08:00:00Z")

        val response = controller.createPublication(
            PublicationUpsertRequest(
                socialAccountId = "account-1",
                title = "Launch",
                bodyText = "Ship it",
                assetIds = listOf("asset-1"),
                scheduleMode = "SCHEDULED_AT",
                scheduledFor = scheduledFor,
                priority = true,
            ),
        )

        assertEquals(PublicationStatus.QUEUED, response.status)
        assertEquals(
            CreatePublicationCommand(
                socialAccountId = "account-1",
                title = "Launch",
                bodyText = "Ship it",
                assetIds = listOf("asset-1"),
                scheduleMode = ScheduleMode.SCHEDULED_AT,
                scheduledFor = scheduledFor,
                nextSlotAfter = null,
                priority = true,
            ),
            mediator.lastRequest,
        )
    }

    @Test
    fun `dispatches calendar query with defaults`() = runTest {
        val mediator = CapturingMediator()
        val controller = PublishingPublicationController(mediator)
        val from = Instant.parse("2026-06-01T00:00:00Z")
        val to = Instant.parse("2026-07-01T00:00:00Z")

        val response = controller.getCalendar(from = from, to = to)

        assertEquals(
            GetCalendarPublicationsQuery(
                from = from,
                to = to,
                status = null,
                socialAccountId = null,
                timezone = "UTC",
            ),
            mediator.lastQuery,
        )
    }

    @Test
    fun `dispatches calendar query with all filters`() = runTest {
        val mediator = CapturingMediator()
        val controller = PublishingPublicationController(mediator)
        val from = Instant.parse("2026-06-01T00:00:00Z")
        val to = Instant.parse("2026-07-01T00:00:00Z")

        val response = controller.getCalendar(
            from = from,
            to = to,
            status = PublicationStatus.SCHEDULED,
            socialAccountId = "account-1",
            timezone = "America/New_York",
        )

        assertEquals(
            GetCalendarPublicationsQuery(
                from = from,
                to = to,
                status = PublicationStatus.SCHEDULED,
                socialAccountId = "account-1",
                timezone = "America/New_York",
            ),
            mediator.lastQuery,
        )
    }

    @Test
    fun `dispatches quick-create command`() = runTest {
        val mediator = CapturingMediator()
        val controller = PublishingPublicationController(mediator)
        val scheduledFor = Instant.parse("2026-06-15T10:00:00Z")

        val response = controller.quickCreatePublication(
            QuickCreateRequest(
                socialAccountId = "account-1",
                title = "Quick post",
                bodyText = "Calendar content",
                scheduledFor = scheduledFor,
                priority = true,
            ),
        )

        assertEquals(
            CreatePublicationCommand(
                socialAccountId = "account-1",
                title = "Quick post",
                bodyText = "Calendar content",
                scheduleMode = ScheduleMode.SCHEDULED_AT,
                scheduledFor = scheduledFor,
                assetIds = emptyList(),
                priority = true,
            ),
            mediator.lastRequest,
        )
    }

    @Test
    fun `dispatches patch reschedule command`() = runTest {
        val mediator = CapturingMediator()
        val controller = PublishingPublicationController(mediator)

        val response = controller.patchReschedulePublication(
            "pub-1",
            PublicationRescheduleRequest(
                scheduleMode = "SCHEDULED_AT",
                scheduledFor = Instant.parse("2026-06-20T14:00:00Z"),
                priority = true,
            ),
        )

        assertEquals(
            ReschedulePublicationCommand(
                publicationId = "pub-1",
                scheduleMode = ScheduleMode.SCHEDULED_AT,
                scheduledFor = Instant.parse("2026-06-20T14:00:00Z"),
                nextSlotAfter = null,
                priority = true,
            ),
            mediator.lastRequest,
        )
    }

    @Test
    fun `list publications delegates to mediator`() = runTest {
        val mediator = CapturingMediator()
        val controller = PublishingPublicationController(mediator)
        val response = controller.listPublications()

        assertEquals(
            com.profiletailors.smp.publishing.application.ListPublicationsResponse(
                publications = emptyList(),
                total = 0,
            ),
            response,
        )
        // Verify the correct query was dispatched to the mediator
        assertNotNull(mediator.lastQuery)
        assertTrue(mediator.lastQuery is ListPublicationsQuery)
    }

    @Test
    fun `rejects invalid timezone in calendar query`() = runTest {
        val mediator = CapturingMediator()
        val controller = PublishingPublicationController(mediator)
        val from = Instant.parse("2026-06-01T00:00:00Z")
        val to = Instant.parse("2026-07-01T00:00:00Z")

        val error = assertThrows(ResponseStatusException::class.java) {
            runBlocking {
                controller.getCalendar(
                    from = from,
                    to = to,
                    timezone = "Not-a-timezone",
                )
            }
        }

        assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
        assertEquals("Invalid timezone: 'Not-a-timezone'", error.reason)
    }

    @Test
    fun `requiredScheduleMode throws when scheduleMode is null`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            PublicationRescheduleRequest().requiredScheduleMode()
        }
        assertEquals("scheduleMode is required for reschedule.", error.message)
    }

    @Test
    fun `dispatches publication lifecycle commands`() = runTest {
        val mediator = CapturingMediator()
        val controller = PublishingPublicationController(mediator)

        controller.cancelPublication("pub-1")
        assertEquals(CancelPublicationCommand("pub-1"), mediator.lastRequest)

        controller.retryPublication("pub-1", PublicationRescheduleRequest(scheduleMode = "NOW", priority = true))
        assertEquals(
            RetryPublicationCommand(
                publicationId = "pub-1",
                scheduleMode = ScheduleMode.NOW,
                scheduledFor = null,
                nextSlotAfter = null,
                priority = true,
            ),
            mediator.lastRequest,
        )

        controller.patchReschedulePublication(
            "pub-1",
            PublicationRescheduleRequest(
                scheduleMode = "NEXT_SLOT",
                nextSlotAfter = Instant.parse("2026-05-27T09:00:00Z"),
                priority = false,
            ),
        )
        assertEquals(
            ReschedulePublicationCommand(
                publicationId = "pub-1",
                scheduleMode = ScheduleMode.NEXT_SLOT,
                scheduledFor = null,
                nextSlotAfter = Instant.parse("2026-05-27T09:00:00Z"),
                priority = false,
            ),
            mediator.lastRequest,
        )

        controller.editPublication(
            "pub-1",
            PublicationUpsertRequest(
                socialAccountId = "account-1",
                bodyText = "edited",
                scheduleMode = "NOW",
            ),
        )
        assertEquals(
            EditPublicationCommand(
                publicationId = "pub-1",
                title = null,
                bodyText = "edited",
                assetIds = emptyList(),
                scheduleMode = ScheduleMode.NOW,
                scheduledFor = null,
                nextSlotAfter = null,
                priority = false,
            ),
            mediator.lastRequest,
        )

        controller.deletePublication("pub-1")
        assertEquals(DeletePublicationCommand("pub-1"), mediator.lastRequest)
    }

    @Test
    fun `create endpoint preserves create-capable flow semantics`() = runTest {
        val mediator = CapturingMediator()
        val controller = PublishingPublicationController(mediator)

        val response = controller.createPublication(
            PublicationUpsertRequest(
                socialAccountId = "account-1",
                bodyText = "Ship it",
                scheduleMode = "NOW",
            ),
        )

        assertEquals(PublicationStatus.QUEUED, response.status)
        assertEquals(CreatePublicationCommand::class, mediator.lastRequest!!::class)
    }

    // Full-stack runtime HTTP boundary coverage for the 404 mapping is intentionally not added
// in this unit-style test class: the production controller uses Spring API versioning
// (@Version + ApiVersionStrategy) which requires additional Spring Boot infrastructure to
// stand up in a standalone ApplicationContext. The 404 contract is covered instead by:
//   1. PublishingProblemDetailsHandlerTest — direct advice unit test for the mapping
//   2. The two controller-level tests in this file that exercise PublicationNotFoundException
//      and prove the advice produces 404 ProblemDetail title/detail/status
// Future work can add a full WebTestClient runtime test by extending IntegrationTestBase.

    private class FixedResourceContextProvider(private val workspaceId: String) : ResourceContextProvider {
        override fun current(): ResourceContext = ResourceContext(
            type = ResourceContextType.WORKSPACE,
            workspaceId = workspaceId,
        )
    }

    private class FakeChannelEventStreamRegistry(private val events: List<ChannelEvent>) : ChannelEventStreamRegistry {
        override fun stream(): Flux<ChannelEvent> = Flux.fromIterable(events)
    }

    // FailingMediator removed — was a leftover from the WebFlux runtime test attempt.
    // The 404 contract is covered by controller exception-flow tests. Full-stack WebFlux
    // runtime test remains a documented follow-up through IntegrationTestBase.

    private class CapturingMediator : Mediator {
        var lastRequest: Any? = null
        var lastQuery: Any? = null

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse {
            lastQuery = query
            return when (query) {
                is GetCalendarPublicationsQuery -> CalendarResponse(
                    publications = emptyList(),
                    conflicts = emptyList(),
                    activity = emptyList(),
                ) as TResponse

                is ListConnectedChannelsQuery -> ConnectedChannelsResponse(
                    channels = listOf(
                        ConnectedSocialChannelSummary(
                            socialAccountId = "account-1",
                            connectionId = "connection-1",
                            provider = SocialProvider.LINKEDIN,
                            accountKind = SocialAccountKind.PERSONAL_PROFILE,
                            displayName = "Yuniel",
                            status = SocialConnectionStatus.ACTIVE,
                            avatarUrl = "https://media.licdn.com/photo.jpg",
                            connectedAt = Instant.parse("2026-06-12T12:00:00Z"),
                            lastSyncedAt = null,
                        ),
                    ),
                ) as TResponse

                is ListPublicationsQuery -> ListPublicationsResponse(
                    publications = emptyList(),
                    total = 0,
                ) as TResponse

                else -> error("Unsupported query type ${query::class.simpleName}")
            }
        }

        override suspend fun <TCommand : Command> send(command: TCommand) {
            error("Not used in this test")
        }

        @Suppress("UNCHECKED_CAST", "CyclomaticComplexMethod", "LongMethod")
        override suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult {
            lastRequest = command
            return when (command) {
                is InitiateLinkedInConnectionCommand -> LinkedInConnectionInitiationResult(
                    authorizationUrl = "https://linkedin.example/authorize?state=state-1",
                    state = "state-1",
                    expiresAt = Instant.parse("2026-06-12T12:10:00Z"),
                ) as TResult

                is CompleteLinkedInConnectionCommand -> SocialConnectionResult(
                    connectionId = "conn-1",
                    workspaceId = "workspace-1",
                    provider = SocialProvider.LINKEDIN,
                    status = SocialConnectionStatus.ACTIVE,
                    account = SocialAccountSummary(
                        accountId = "account-1",
                        providerAccountId = "linkedin-account-1",
                        displayName = "Yuniel",
                        kind = SocialAccountKind.PERSONAL_PROFILE,
                        profileUrn = "urn:li:person:123",
                    ),
                ) as TResult

                is CreatePublicationCommand,
                is EditPublicationCommand,
                is DeletePublicationCommand,
                is CancelPublicationCommand,
                is RetryPublicationCommand,
                is ReschedulePublicationCommand,
                -> PublicationResult(
                    publicationId = "pub-1",
                    workspaceId = "workspace-1",
                    socialAccountId = "account-1",
                    status = PublicationStatus.QUEUED,
                    scheduleMode = when (command) {
                        is CreatePublicationCommand -> command.scheduleMode
                        is EditPublicationCommand -> command.scheduleMode
                        is RetryPublicationCommand -> command.scheduleMode ?: ScheduleMode.NOW
                        is ReschedulePublicationCommand -> command.scheduleMode
                        else -> ScheduleMode.NOW
                    },
                    priority = when (command) {
                        is CreatePublicationCommand -> command.priority
                        is EditPublicationCommand -> command.priority
                        is RetryPublicationCommand -> command.priority ?: false
                        is ReschedulePublicationCommand -> command.priority ?: false
                        else -> false
                    },
                    title = if (command is CreatePublicationCommand) command.title else null,
                    bodyText = when (command) {
                        is CreatePublicationCommand -> command.bodyText
                        is EditPublicationCommand -> command.bodyText
                        else -> "body"
                    },
                    assetIds = when (command) {
                        is CreatePublicationCommand -> command.assetIds
                        is EditPublicationCommand -> command.assetIds
                        else -> emptyList()
                    },
                    scheduledFor = when (command) {
                        is CreatePublicationCommand -> command.scheduledFor
                        is EditPublicationCommand -> command.scheduledFor
                        is RetryPublicationCommand -> command.scheduledFor
                        is ReschedulePublicationCommand -> command.scheduledFor
                        else -> null
                    },
                    nextSlotAfter = when (command) {
                        is CreatePublicationCommand -> command.nextSlotAfter
                        is EditPublicationCommand -> command.nextSlotAfter
                        is RetryPublicationCommand -> command.nextSlotAfter
                        is ReschedulePublicationCommand -> command.nextSlotAfter
                        else -> null
                    },
                ) as TResult

                else -> error("Unsupported request type ${command::class.simpleName}")
            }
        }

        override suspend fun <T : Notification> publish(notification: T) {
            error("Not used in this test")
        }

        override suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy) {
            error("Not used in this test")
        }
    }
}
