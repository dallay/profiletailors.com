package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.context.ResourceContext
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.context.ResourceContextType
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.publishing.domain.DeliveryAttemptRepository
import com.profiletailors.smp.publishing.domain.ProviderPublishResult
import com.profiletailors.smp.publishing.domain.PublicationDraft
import com.profiletailors.smp.publishing.domain.PublicationEvent
import com.profiletailors.smp.publishing.domain.PublicationEventPublisher
import com.profiletailors.smp.publishing.domain.PublicationEventType
import com.profiletailors.smp.publishing.domain.PublicationJobClaim
import com.profiletailors.smp.publishing.domain.PublicationRepository
import com.profiletailors.smp.publishing.domain.PublicationSchedulingPolicy
import com.profiletailors.smp.publishing.domain.PublicationStatus
import com.profiletailors.smp.publishing.domain.ScheduleMode
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.scheduling.PublishingJobExecutor
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PublicationEventEmissionTest {

    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC)
    private val principalContext = PrincipalContext(
        principalId = "principal-1",
        principalType = PrincipalType.USER,
        subject = "local:owner@example.com",
    )
    private val workspaceContext = ResourceContext(
        type = ResourceContextType.WORKSPACE,
        workspaceId = "workspace-1",
    )

    @Test
    fun `reschedule emits rescheduled event after commit`() = runTest {
        val events = RecordingPublicationEventPublisher()
        val handler = rescheduleHandler(
            publicationRepository = mockk {
                coEvery { findByWorkspaceAndId("workspace-1", "pub-1") } returns queuedPublication()
                coEvery { updateEditableDraft(any()) } answers { firstArg() }
            },
            transactionRunner = passThroughTransactionRunner(),
            eventPublisher = events,
        )

        handler.handle(
            ReschedulePublicationCommand(
                publicationId = "pub-1",
                scheduleMode = ScheduleMode.SCHEDULED_AT,
                scheduledFor = Instant.parse("2026-10-01T10:00:00Z"),
                nextSlotAfter = null,
                priority = false,
            ),
        )

        assertEquals(1, events.received.size)
        val event = events.received.single()
        assertEquals(PublicationEventType.RESCHEDULED, event.type)
        assertEquals("workspace-1", event.workspaceId)
        assertEquals("pub-1", event.publicationId)
    }

    @Test
    fun `rolled-back reschedule emits zero events`() = runTest {
        val events = RecordingPublicationEventPublisher()
        val handler = rescheduleHandler(
            publicationRepository = mockk {
                coEvery { findByWorkspaceAndId("workspace-1", "pub-1") } returns queuedPublication()
            },
            transactionRunner = failingTransactionRunner(),
            eventPublisher = events,
        )

        assertThrows<IllegalStateException> {
            handler.handle(
                ReschedulePublicationCommand(
                    publicationId = "pub-1",
                    scheduleMode = ScheduleMode.SCHEDULED_AT,
                    scheduledFor = Instant.parse("2026-10-01T10:00:00Z"),
                    nextSlotAfter = null,
                    priority = false,
                ),
            )
        }
        assertTrue(events.received.isEmpty())
    }

    @Test
    fun `delete emits deleted event after single atomic boundary`() = runTest {
        val events = RecordingPublicationEventPublisher()
        val transactionRunner = passThroughTransactionRunner()
        val handler = DeletePublicationHandler(
            principalContextProvider = FixedPrincipalContextProvider(principalContext),
            resourceContextProvider = FixedResourceContextProvider(workspaceContext),
            publicationRepository = mockk {
                coEvery { findByWorkspaceAndId("workspace-1", "pub-1") } returns queuedPublication()
                coEvery { deleteUnpublished("workspace-1", "pub-1") } returns true
            },
            publicationJobRepository = mockk(),
            transactionRunner = transactionRunner,
            clock = fixedClock,
            publicationEventPublisher = events,
        )

        handler.handle(DeletePublicationCommand("pub-1"))

        assertEquals(1, transactionRunner.invocations)
        assertEquals(1, events.received.size)
        assertEquals(PublicationEventType.DELETED, events.received.single().type)
        assertEquals("pub-1", events.received.single().publicationId)
    }

    @Test
    fun `worker success emits status-changed event after commit`() = runTest {
        val events = RecordingPublicationEventPublisher()
        val executor = PublishingJobExecutor(
            publicationJobRepository = mockk {
                coEvery { complete("job-1", 1, fixedClock.instant()) } returns true
            },
            publicationRepository = mockk {
                coEvery { findByWorkspaceAndId("workspace-1", "pub-1") } returns queuedPublication()
                coEvery { markPublished("pub-1", "urn:li:share:1", fixedClock.instant()) } returns Unit
            },
            socialAccountRepository = mockk {
                coEvery { findByWorkspaceAndId("workspace-1", "account-1") } returns activeAccount()
            },
            mediaAssetResolver = mockk {
                coEvery { resolveReadyAssets("workspace-1", emptyList()) } returns emptyList()
            },
            deliveryAttemptRepository = mockk<DeliveryAttemptRepository> {
                coEvery { findByOperationKey(any()) } returns null
                coEvery { record(any()) } answers { firstArg() }
                coEvery { update(any()) } returns true
            },
            notificationEventRepository = null,
            providerCapabilityValidator = mockk {
                coEvery { validate(any()) } returns Unit
            },
            socialPublisher = mockk {
                coEvery { publish(any()) } returns ProviderPublishResult(externalPublicationId = "urn:li:share:1")
            },
            retryPolicy = mockk(),
            transactionRunner = passThroughTransactionRunner(),
            clock = fixedClock,
            publicationEventPublisher = events,
        )

        executor.executeClaim(
            PublicationJobClaim(
                jobId = "job-1",
                publicationId = "pub-1",
                workspaceId = "workspace-1",
                attemptNumber = 1,
                claimedAt = fixedClock.instant(),
            ),
        )

        assertEquals(1, events.received.size)
        val event = events.received.single()
        assertEquals(PublicationEventType.STATUS_CHANGED, event.type)
        assertEquals("workspace-1", event.workspaceId)
        assertEquals("pub-1", event.publicationId)
    }

    @Test
    fun `publisher failure after commit keeps the committed result`() = runTest {
        val handler = rescheduleHandler(
            publicationRepository = mockk {
                coEvery { findByWorkspaceAndId("workspace-1", "pub-1") } returns queuedPublication()
                coEvery { updateEditableDraft(any()) } answers { firstArg() }
            },
            transactionRunner = passThroughTransactionRunner(),
            eventPublisher = PublicationEventPublisher { throw IllegalStateException("sink exploded") },
        )

        val result = handler.handle(
            ReschedulePublicationCommand(
                publicationId = "pub-1",
                scheduleMode = ScheduleMode.SCHEDULED_AT,
                scheduledFor = Instant.parse("2026-10-01T10:00:00Z"),
                nextSlotAfter = null,
                priority = false,
            ),
        )

        assertEquals("pub-1", result.publicationId)
    }

    private fun queuedPublication(): PublicationDraft = PublicationDraft(
        id = "pub-1",
        workspaceId = "workspace-1",
        authorPrincipalId = "principal-1",
        provider = SocialProvider.LINKEDIN,
        socialAccountId = "account-1",
        status = PublicationStatus.QUEUED,
        scheduleMode = ScheduleMode.SCHEDULED_AT,
        priority = false,
        bodyText = "Hello",
        scheduledFor = Instant.parse("2026-10-01T10:00:00Z"),
    )

    private fun activeAccount(): SocialAccount = SocialAccount(
        id = "account-1",
        socialConnectionId = "connection-1",
        workspaceId = "workspace-1",
        provider = SocialProvider.LINKEDIN,
        providerAccountId = "linkedin-account-1",
        kind = SocialAccountKind.PERSONAL_PROFILE,
        displayName = "Tester",
        status = SocialConnectionStatus.ACTIVE,
    )

    private fun rescheduleHandler(
        publicationRepository: PublicationRepository,
        transactionRunner: AtomicTransactionRunner,
        eventPublisher: PublicationEventPublisher,
    ): ReschedulePublicationHandler = ReschedulePublicationHandler(
        principalContextProvider = FixedPrincipalContextProvider(principalContext),
        resourceContextProvider = FixedResourceContextProvider(workspaceContext),
        publicationRepository = publicationRepository,
        publicationJobRepository = mockk {
            coEvery { replaceForPublication(any()) } returns Unit
        },
        transactionRunner = transactionRunner,
        schedulingPolicy = PublicationSchedulingPolicy(),
        clock = fixedClock,
        publicationEventPublisher = eventPublisher,
    )

    private fun passThroughTransactionRunner(): RecordingTransactionRunner = RecordingTransactionRunner(fail = false)

    private fun failingTransactionRunner(): RecordingTransactionRunner = RecordingTransactionRunner(fail = true)

    private class RecordingTransactionRunner(private val fail: Boolean) : AtomicTransactionRunner {
        var invocations: Int = 0

        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T {
            invocations += 1
            if (fail) throw IllegalStateException("transaction rolled back")
            return block()
        }
    }

    private class RecordingPublicationEventPublisher : PublicationEventPublisher {
        val received = mutableListOf<PublicationEvent>()

        override fun publish(event: PublicationEvent) {
            received.add(event)
        }
    }

    private class FixedPrincipalContextProvider(private val principalContext: PrincipalContext) :
        PrincipalContextProvider {
        override suspend fun current(): PrincipalContext = principalContext
    }

    private class FixedResourceContextProvider(private val resourceContext: ResourceContext) :
        ResourceContextProvider {
        override fun current(): ResourceContext = resourceContext
    }
}
