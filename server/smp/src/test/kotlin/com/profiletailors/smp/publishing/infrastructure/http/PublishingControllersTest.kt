package com.profiletailors.smp.publishing.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.PublishStrategy
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.smp.publishing.application.CalendarResponse
import com.profiletailors.smp.publishing.application.CancelPublicationCommand
import com.profiletailors.smp.publishing.application.CompleteLinkedInConnectionCommand
import com.profiletailors.smp.publishing.application.CreatePublicationCommand
import com.profiletailors.smp.publishing.application.EditPublicationCommand
import com.profiletailors.smp.publishing.application.GetCalendarPublicationsQuery
import com.profiletailors.smp.publishing.application.PublicationResult
import com.profiletailors.smp.publishing.application.ReschedulePublicationCommand
import com.profiletailors.smp.publishing.application.RetryPublicationCommand
import com.profiletailors.smp.publishing.application.SocialAccountSummary
import com.profiletailors.smp.publishing.application.SocialConnectionResult
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class PublishingControllersTest {

    @Test
    fun `dispatches linkedin connection completion command`() = runTest {
        val mediator = CapturingMediator()
        val controller = PublishingConnectionController(mediator)

        val response = controller.completeLinkedInConnection(
            LinkedInConnectionCompletionRequest(
                authorizationCode = "oauth-code-1",
                redirectUri = "https://app.example.com/callback",
            ),
        )

        assertEquals("workspace-1", response.workspaceId)
        assertEquals(
            CompleteLinkedInConnectionCommand(
                authorizationCode = "oauth-code-1",
                redirectUri = "https://app.example.com/callback",
            ),
            mediator.lastRequest,
        )
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
    }

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
