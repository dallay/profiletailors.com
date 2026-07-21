package com.profiletailors.smp.governance.infrastructure.email

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.notifications.application.ports.EmailDispatchResult
import com.profiletailors.notifications.application.ports.EmailDispatcher
import com.profiletailors.notifications.domain.IdempotencyKey
import com.profiletailors.notifications.domain.Notification
import com.profiletailors.notifications.domain.NotificationRepository
import com.profiletailors.notifications.domain.NotificationStatus
import com.profiletailors.smp.governance.domain.event.TakedownApproved
import com.profiletailors.smp.governance.domain.event.TakedownRejected
import com.profiletailors.smp.governance.domain.event.TakedownReported
import com.profiletailors.smp.identity.application.NoOpPrincipalIdentityLookup
import com.profiletailors.smp.identity.application.PrincipalIdentityFacts
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.tenancy.application.WorkspaceOwnershipRepository
import com.profiletailors.smp.tenancy.domain.WorkspaceOwnership
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class TakedownEmailConsumersTest {

    private val fixedNow: Instant = Instant.parse("2026-07-21T10:00:00Z")
    private val clock: Clock = Clock.fixed(fixedNow, ZoneOffset.UTC)

    // ---------------------- TakedownReportedConsumer ----------------------

    @Test
    fun `reported consumer dispatches email to every workspace owner and records SENT`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        val ownershipRepository = mockk<WorkspaceOwnershipRepository>()
        val identityLookup = mockk<PrincipalIdentityLookup>()

        coEvery { ownershipRepository.findByWorkspaceId("ws-001") } returns setOf(
            WorkspaceOwnership(
                workspaceId = "ws-001",
                ownerPrincipalId = "owner-1",
                ownerPrincipalType = PrincipalType.USER,
            ),
            WorkspaceOwnership(
                workspaceId = "ws-001",
                ownerPrincipalId = "owner-2",
                ownerPrincipalType = PrincipalType.USER,
            ),
        )
        coEvery { identityLookup.findByPrincipalId("owner-1") } returns PrincipalIdentityFacts(
            principalId = "owner-1",
            principalType = PrincipalType.USER,
            subject = "owner1@example.com",
            provider = null,
            displayIdentity = null,
            email = "owner1@example.com",
            username = null,
        )
        coEvery { identityLookup.findByPrincipalId("owner-2") } returns PrincipalIdentityFacts(
            principalId = "owner-2",
            principalType = PrincipalType.USER,
            subject = "owner2@example.com",
            provider = null,
            displayIdentity = null,
            email = "owner2@example.com",
            username = null,
        )
        coEvery { repository.findByIdempotencyKey(any()) } returns null
        coEvery { repository.save(any()) } answers { firstArg() }
        coEvery { repository.update(any()) } answers { firstArg() }
        coEvery { dispatcher.dispatch(any(), any()) } returns EmailDispatchResult.Success

        val consumer = SendTakedownReportedEmailConsumer(
            emailDispatcher = dispatcher,
            notificationRepository = repository,
            workspaceOwnershipRepository = ownershipRepository,
            principalIdentityLookup = identityLookup,
            clock = clock,
        )
        consumer.consume(reportedEvent())

        coVerify(exactly = 1) { dispatcher.dispatch("owner1@example.com", any()) }
        coVerify(exactly = 1) { dispatcher.dispatch("owner2@example.com", any()) }
        coVerify(exactly = 2) { repository.update(match { it.status == NotificationStatus.SENT }) }
    }

    @Test
    fun `reported consumer skips owners with no resolvable email`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        val ownershipRepository = mockk<WorkspaceOwnershipRepository>()
        val identityLookup = NoOpPrincipalIdentityLookup()

        coEvery { ownershipRepository.findByWorkspaceId("ws-001") } returns setOf(
            WorkspaceOwnership(
                workspaceId = "ws-001",
                ownerPrincipalId = "owner-1",
                ownerPrincipalType = PrincipalType.USER,
            ),
        )
        coEvery { repository.findByIdempotencyKey(any()) } returns null

        val consumer = SendTakedownReportedEmailConsumer(
            emailDispatcher = dispatcher,
            notificationRepository = repository,
            workspaceOwnershipRepository = ownershipRepository,
            principalIdentityLookup = identityLookup,
            clock = clock,
        )
        consumer.consume(reportedEvent())

        coVerify(exactly = 0) { dispatcher.dispatch(any(), any()) }
    }

    @Test
    fun `reported consumer skips dispatch when notification with idempotency key already exists`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        val ownershipRepository = mockk<WorkspaceOwnershipRepository>()
        val identityLookup = mockk<PrincipalIdentityLookup>()

        coEvery { ownershipRepository.findByWorkspaceId("ws-001") } returns setOf(
            WorkspaceOwnership(
                workspaceId = "ws-001",
                ownerPrincipalId = "owner-1",
                ownerPrincipalType = PrincipalType.USER,
            ),
        )
        coEvery { identityLookup.findByPrincipalId("owner-1") } returns PrincipalIdentityFacts(
            principalId = "owner-1",
            principalType = PrincipalType.USER,
            subject = "owner1@example.com",
            provider = null,
            displayIdentity = null,
            email = "owner1@example.com",
            username = null,
        )
        coEvery { repository.findByIdempotencyKey(any()) } returns mockk<Notification>(relaxed = true)

        val consumer = SendTakedownReportedEmailConsumer(
            emailDispatcher = dispatcher,
            notificationRepository = repository,
            workspaceOwnershipRepository = ownershipRepository,
            principalIdentityLookup = identityLookup,
            clock = clock,
        )
        consumer.consume(reportedEvent())

        coVerify(exactly = 0) { dispatcher.dispatch(any(), any()) }
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `reported consumer computes idempotency key per report and recipient`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        val ownershipRepository = mockk<WorkspaceOwnershipRepository>()
        val identityLookup = mockk<PrincipalIdentityLookup>()

        coEvery { ownershipRepository.findByWorkspaceId("ws-001") } returns setOf(
            WorkspaceOwnership(
                workspaceId = "ws-001",
                ownerPrincipalId = "owner-1",
                ownerPrincipalType = PrincipalType.USER,
            ),
        )
        coEvery { identityLookup.findByPrincipalId("owner-1") } returns PrincipalIdentityFacts(
            principalId = "owner-1",
            principalType = PrincipalType.USER,
            subject = "owner1@example.com",
            provider = null,
            displayIdentity = null,
            email = "owner1@example.com",
            username = null,
        )
        val seenKey = slot<IdempotencyKey>()
        coEvery { repository.findByIdempotencyKey(capture(seenKey)) } returns null
        coEvery { repository.save(any()) } answers { firstArg() }
        coEvery { repository.update(any()) } answers { firstArg() }
        coEvery { dispatcher.dispatch(any(), any()) } returns EmailDispatchResult.Success

        val consumer = SendTakedownReportedEmailConsumer(
            emailDispatcher = dispatcher,
            notificationRepository = repository,
            workspaceOwnershipRepository = ownershipRepository,
            principalIdentityLookup = identityLookup,
            clock = clock,
        )
        consumer.consume(reportedEvent(reportId = "report-42"))

        assertEquals("governance.takedown.reported:report-42:owner1@example.com", seenKey.captured.value)
    }

    @Test
    fun `reported consumer records FAILED when dispatcher returns failure`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        val ownershipRepository = mockk<WorkspaceOwnershipRepository>()
        val identityLookup = mockk<PrincipalIdentityLookup>()

        coEvery { ownershipRepository.findByWorkspaceId("ws-001") } returns setOf(
            WorkspaceOwnership(
                workspaceId = "ws-001",
                ownerPrincipalId = "owner-1",
                ownerPrincipalType = PrincipalType.USER,
            ),
        )
        coEvery { identityLookup.findByPrincipalId("owner-1") } returns PrincipalIdentityFacts(
            principalId = "owner-1",
            principalType = PrincipalType.USER,
            subject = "owner1@example.com",
            provider = null,
            displayIdentity = null,
            email = "owner1@example.com",
            username = null,
        )
        coEvery { repository.findByIdempotencyKey(any()) } returns null
        coEvery { repository.save(any()) } answers { firstArg() }
        coEvery { repository.update(any()) } answers { firstArg() }
        coEvery { dispatcher.dispatch(any(), any()) } returns EmailDispatchResult.Failure("smtp 5xx")

        val consumer = SendTakedownReportedEmailConsumer(
            emailDispatcher = dispatcher,
            notificationRepository = repository,
            workspaceOwnershipRepository = ownershipRepository,
            principalIdentityLookup = identityLookup,
            clock = clock,
        )
        consumer.consume(reportedEvent())

        coVerify(exactly = 1) {
            repository.update(
                match { it.status == NotificationStatus.FAILED && it.errorMessage == "smtp 5xx" },
            )
        }
    }

    @Test
    fun `reported consumer warns and skips when workspace has no owners`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        val ownershipRepository = mockk<WorkspaceOwnershipRepository>()
        val identityLookup = mockk<PrincipalIdentityLookup>()

        coEvery { ownershipRepository.findByWorkspaceId("ws-001") } returns emptySet()

        val consumer = SendTakedownReportedEmailConsumer(
            emailDispatcher = dispatcher,
            notificationRepository = repository,
            workspaceOwnershipRepository = ownershipRepository,
            principalIdentityLookup = identityLookup,
            clock = clock,
        )
        consumer.consume(reportedEvent())

        coVerify(exactly = 0) { dispatcher.dispatch(any(), any()) }
    }

    // ---------------------- TakedownApprovedConsumer ----------------------

    @Test
    fun `approved consumer dispatches email to reporter and records SENT`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        coEvery { repository.findByIdempotencyKey(any()) } returns null
        coEvery { repository.save(any()) } answers { firstArg() }
        coEvery { repository.update(any()) } answers { firstArg() }
        coEvery { dispatcher.dispatch(any(), any()) } returns EmailDispatchResult.Success

        val consumer = SendTakedownApprovedEmailConsumer(dispatcher, repository, clock)
        consumer.consume(approvedEvent())

        coVerify(exactly = 1) {
            dispatcher.dispatch(
                "reporter@example.com",
                match { it.subject.contains("approved", ignoreCase = true) && it.html != null },
            )
        }
        coVerify(exactly = 1) { repository.update(match { it.status == NotificationStatus.SENT }) }
    }

    @Test
    fun `approved consumer skips dispatch when notification already exists`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        coEvery { repository.findByIdempotencyKey(any()) } returns mockk<Notification>(relaxed = true)

        val consumer = SendTakedownApprovedEmailConsumer(dispatcher, repository, clock)
        consumer.consume(approvedEvent())

        coVerify(exactly = 0) { dispatcher.dispatch(any(), any()) }
    }

    // ---------------------- TakedownRejectedConsumer ----------------------

    @Test
    fun `rejected consumer dispatches email to reporter and records SENT`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        coEvery { repository.findByIdempotencyKey(any()) } returns null
        coEvery { repository.save(any()) } answers { firstArg() }
        coEvery { repository.update(any()) } answers { firstArg() }
        coEvery { dispatcher.dispatch(any(), any()) } returns EmailDispatchResult.Success

        val consumer = SendTakedownRejectedEmailConsumer(dispatcher, repository, clock)
        consumer.consume(rejectedEvent(rejectionReason = "Insufficient evidence"))

        coVerify(exactly = 1) {
            dispatcher.dispatch(
                "reporter@example.com",
                match { it.subject.contains("rejected", ignoreCase = true) && it.html != null },
            )
        }
        coVerify(exactly = 1) { repository.update(match { it.status == NotificationStatus.SENT }) }
    }

    @Test
    fun `rejected consumer skips dispatch when notification already exists`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        coEvery { repository.findByIdempotencyKey(any()) } returns mockk<Notification>(relaxed = true)

        val consumer = SendTakedownRejectedEmailConsumer(dispatcher, repository, clock)
        consumer.consume(rejectedEvent())

        coVerify(exactly = 0) { dispatcher.dispatch(any(), any()) }
    }

    @Test
    fun `rejected consumer handles missing rejection reason`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        coEvery { repository.findByIdempotencyKey(any()) } returns null
        val saved = slot<Notification>()
        coEvery { repository.save(capture(saved)) } answers { saved.captured }
        coEvery { repository.update(any()) } answers { firstArg() }
        coEvery { dispatcher.dispatch(any(), any()) } returns EmailDispatchResult.Success

        val consumer = SendTakedownRejectedEmailConsumer(dispatcher, repository, clock)
        consumer.consume(rejectedEvent(rejectionReason = null))

        assertEquals("", saved.captured.payload["rejectionReason"])
        coVerify(exactly = 1) { dispatcher.dispatch(any(), any()) }
    }

    @Test
    fun `approved consumer records FAILED when dispatcher returns failure`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        coEvery { repository.findByIdempotencyKey(any()) } returns null
        coEvery { repository.save(any()) } answers { firstArg() }
        coEvery { repository.update(any()) } answers { firstArg() }
        coEvery { dispatcher.dispatch(any(), any()) } returns EmailDispatchResult.Failure("smtp 5xx")

        val consumer = SendTakedownApprovedEmailConsumer(dispatcher, repository, clock)
        consumer.consume(approvedEvent())

        coVerify(exactly = 1) {
            repository.update(
                match { it.status == NotificationStatus.FAILED && it.errorMessage == "smtp 5xx" },
            )
        }
    }

    @Test
    fun `approved consumer computes correct idempotency key`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        val seenKey = slot<IdempotencyKey>()
        coEvery { repository.findByIdempotencyKey(capture(seenKey)) } returns null
        coEvery { repository.save(any()) } answers { firstArg() }
        coEvery { repository.update(any()) } answers { firstArg() }
        coEvery { dispatcher.dispatch(any(), any()) } returns EmailDispatchResult.Success

        val consumer = SendTakedownApprovedEmailConsumer(dispatcher, repository, clock)
        consumer.consume(approvedEvent(reportId = "report-99"))

        assertEquals("governance.takedown.approved:report-99:reporter@example.com", seenKey.captured.value)
    }

    @Test
    fun `rejected consumer computes correct idempotency key`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        val seenKey = slot<IdempotencyKey>()
        coEvery { repository.findByIdempotencyKey(capture(seenKey)) } returns null
        coEvery { repository.save(any()) } answers { firstArg() }
        coEvery { repository.update(any()) } answers { firstArg() }
        coEvery { dispatcher.dispatch(any(), any()) } returns EmailDispatchResult.Success

        val consumer = SendTakedownRejectedEmailConsumer(dispatcher, repository, clock)
        consumer.consume(rejectedEvent(reportId = "report-77"))

        assertEquals("governance.takedown.rejected:report-77:reporter@example.com", seenKey.captured.value)
    }

    @Test
    fun `reported consumer subject contains requires review`() = runTest {
        val repository = mockk<NotificationRepository>()
        val dispatcher = mockk<EmailDispatcher>()
        val ownershipRepository = mockk<WorkspaceOwnershipRepository>()
        val identityLookup = mockk<PrincipalIdentityLookup>()

        coEvery { ownershipRepository.findByWorkspaceId("ws-001") } returns setOf(
            WorkspaceOwnership(
                workspaceId = "ws-001",
                ownerPrincipalId = "owner-1",
                ownerPrincipalType = PrincipalType.USER,
            ),
        )
        coEvery { identityLookup.findByPrincipalId("owner-1") } returns PrincipalIdentityFacts(
            principalId = "owner-1",
            principalType = PrincipalType.USER,
            subject = "owner1@example.com",
            provider = null,
            displayIdentity = null,
            email = "owner1@example.com",
            username = null,
        )
        coEvery { repository.findByIdempotencyKey(any()) } returns null
        coEvery { repository.save(any()) } answers { firstArg() }
        coEvery { repository.update(any()) } answers { firstArg() }
        coEvery { dispatcher.dispatch(any(), any()) } returns EmailDispatchResult.Success

        val consumer = SendTakedownReportedEmailConsumer(
            emailDispatcher = dispatcher,
            notificationRepository = repository,
            workspaceOwnershipRepository = ownershipRepository,
            principalIdentityLookup = identityLookup,
            clock = clock,
        )
        consumer.consume(reportedEvent())

        coVerify(exactly = 1) {
            dispatcher.dispatch("owner1@example.com", match { it.subject.contains("review", ignoreCase = true) })
        }
        // sanity: payload contains the expected fields
        coVerify(exactly = 1) {
            repository.save(
                match {
                    it.payload["reportId"] == "report-001" &&
                        it.payload["assetId"] == "asset-001" &&
                        it.payload["reason"] == "Copyright infringement"
                },
            )
        }
    }

    // ---------------------- Helpers ----------------------

    private fun reportedEvent(reportId: String = "report-001") = TakedownReported(
        reportId = reportId,
        workspaceId = "ws-001",
        assetId = "asset-001",
        reportedById = "user-001",
        reason = "Copyright infringement",
        reporterEmail = "reporter@example.com",
        mediaReferenceUrl = "https://example.test/source",
        occurredAt = LocalDateTime.ofInstant(fixedNow, ZoneOffset.UTC),
    )

    private fun approvedEvent(reportId: String = "report-001") = TakedownApproved(
        reportId = reportId,
        workspaceId = "ws-001",
        assetId = "asset-001",
        reporterEmail = "reporter@example.com",
        reviewedById = "reviewer-001",
        occurredAt = LocalDateTime.ofInstant(fixedNow, ZoneOffset.UTC),
    )

    private fun rejectedEvent(reportId: String = "report-001", rejectionReason: String? = "Insufficient evidence") =
        TakedownRejected(
            reportId = reportId,
            workspaceId = "ws-001",
            assetId = "asset-001",
            reporterEmail = "reporter@example.com",
            reviewedById = "reviewer-001",
            rejectionReason = rejectionReason,
            occurredAt = LocalDateTime.ofInstant(fixedNow, ZoneOffset.UTC),
        )

    @Suppress("unused")
    private fun unusedAssertions() {
        assertTrue(true)
    }
}
