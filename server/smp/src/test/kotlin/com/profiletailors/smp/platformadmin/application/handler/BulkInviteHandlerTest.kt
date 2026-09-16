package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.leadcapture.common.CaptureLocale
import com.profiletailors.leadcapture.common.CaptureSource
import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.LeadMetadata
import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.domain.WaitlistConsent
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntry
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryStatus
import com.profiletailors.leadcapture.waitlist.domain.WaitlistId
import com.profiletailors.smp.platformadmin.application.OptimisticLockException
import com.profiletailors.smp.platformadmin.application.command.BulkInviteOutcome
import com.profiletailors.smp.platformadmin.application.command.BulkInviteWaitlistEntriesCommand
import com.profiletailors.smp.platformadmin.application.command.InviteWaitlistEntryCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.InvitationTelemetry
import com.profiletailors.smp.platformadmin.application.contracts.WaitlistEntryAdmin
import com.profiletailors.smp.platformadmin.application.model.AdminInvitationSummary
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.AdminAuditResult
import com.profiletailors.smp.platformadmin.domain.InvitationAlreadyActiveException
import com.profiletailors.smp.platformadmin.domain.InvitationDeliveryStatus
import com.profiletailors.smp.platformadmin.domain.InvitationVersionConflictException
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryAlreadyConvertedException
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryNotFoundException
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryNotInvitableException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class BulkInviteHandlerTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-01T10:00:00Z"), ZoneOffset.UTC)
    private val operatorId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val operatorRoles = setOf(PlatformRole.PLATFORM_OPERATOR)
    private val auditorRoles = setOf(PlatformRole.AUDITOR)

    private val singleHandler = mockk<InviteWaitlistEntryHandler>()
    private val waitlistEntryAdmin = mockk<WaitlistEntryAdmin>()
    private val transactionRunner = PassThroughTransactionRunner()
    private val auditPublisher = mockk<AdministrativeAuditPublisher>(relaxed = true)
    private val telemetry = mockk<InvitationTelemetry>(relaxed = true)

    private val handler = BulkInviteWaitlistEntriesHandler(
        singleHandler = singleHandler,
        waitlistEntryAdmin = waitlistEntryAdmin,
        transactionRunner = transactionRunner,
        auditPublisher = auditPublisher,
        telemetry = telemetry,
        clock = clock,
    )

    @Test
    fun `rejects empty entry list before any work`() = runTest {
        assertThrows<IllegalArgumentException> {
            handler.handle(command(entryIds = emptyList()))
        }
        coVerify(exactly = 0) { waitlistEntryAdmin.findById(any()) }
        coVerify(exactly = 0) { singleHandler.handle(any()) }
    }

    @Test
    fun `rejects batch over fifty entries before any work`() = runTest {
        val ids = (1..51).map { "entry-$it" }
        assertThrows<IllegalArgumentException> {
            handler.handle(command(entryIds = ids))
        }
        coVerify(exactly = 0) { waitlistEntryAdmin.findById(any()) }
        coVerify(exactly = 0) { singleHandler.handle(any()) }
    }

    @Test
    fun `rejects blank entry ids`() = runTest {
        assertThrows<IllegalArgumentException> {
            handler.handle(command(entryIds = listOf("   ")))
        }
        coVerify(exactly = 0) { singleHandler.handle(any()) }
    }

    @Test
    fun `denies bulk invite without WAITLIST_INVITE before touching entries`() = runTest {
        assertThrows<PlatformAccessDeniedException> {
            handler.handle(command(roles = auditorRoles, entryIds = listOf("entry-1")))
        }
        coVerify(exactly = 0) { waitlistEntryAdmin.findById(any()) }
        coVerify(exactly = 0) { singleHandler.handle(any()) }
    }

    @Test
    fun `dedupes repeated ids preserving first occurrence order`() = runTest {
        val first = "entry-1"
        val second = "entry-2"
        coEvery { waitlistEntryAdmin.findById(any()) } answers {
            entry(firstArg(), WaitlistEntryStatus.PENDING)
        }
        coEvery { singleHandler.handle(any()) } answers {
            summary(firstArg<InviteWaitlistEntryCommand>().waitlistEntryId)
        }

        val result = handler.handle(command(entryIds = listOf(first, second, first)))

        assertThat(result.results.map { it.entryId }).containsExactly(first, second)
        assertThat(result.summary.requested).isEqualTo(2)
        coVerify(exactly = 1) { singleHandler.handle(match { it.waitlistEntryId == first }) }
    }

    @Test
    fun `mixed batch reports invited skipped and failed with matching summary`() = runTest {
        val pending = "entry-pending"
        val invited = "entry-invited"
        val converted = "entry-converted"
        coEvery { waitlistEntryAdmin.findById(pending) } returns entry(pending, WaitlistEntryStatus.PENDING)
        coEvery { waitlistEntryAdmin.findById(invited) } returns entry(invited, WaitlistEntryStatus.INVITED)
        coEvery { waitlistEntryAdmin.findById(converted) } returns entry(converted, WaitlistEntryStatus.CONVERTED)
        coEvery { singleHandler.handle(match { it.waitlistEntryId == pending }) } returns summary(pending)
        coEvery { singleHandler.handle(match { it.waitlistEntryId == converted }) } throws
            WaitlistEntryAlreadyConvertedException(converted)

        val result = handler.handle(command(entryIds = listOf(pending, invited, converted)))

        assertThat(result.results.map { it.outcome }).containsExactly(
            BulkInviteOutcome.INVITED,
            BulkInviteOutcome.SKIPPED,
            BulkInviteOutcome.FAILED,
        )
        assertThat(result.results[0].invitationId).isNotNull()
        assertThat(result.results[1].code).isEqualTo("ALREADY_INVITED")
        assertThat(result.results[2].code).isEqualTo("ENTRY_ALREADY_CONVERTED")
        assertThat(result.summary.requested).isEqualTo(3)
        assertThat(result.summary.invited).isEqualTo(1)
        assertThat(result.summary.skipped).isEqualTo(1)
        assertThat(result.summary.failed).isEqualTo(1)
        coVerify(exactly = 0) { singleHandler.handle(match { it.waitlistEntryId == invited }) }
    }

    @Test
    fun `maps active invitation to skipped without issuing events`() = runTest {
        val entryId = "entry-active"
        coEvery { waitlistEntryAdmin.findById(entryId) } returns entry(entryId, WaitlistEntryStatus.PENDING)
        coEvery { singleHandler.handle(any()) } throws InvitationAlreadyActiveException(entryId)

        val result = handler.handle(command(entryIds = listOf(entryId)))

        assertThat(result.results.single().outcome).isEqualTo(BulkInviteOutcome.SKIPPED)
        assertThat(result.results.single().code).isEqualTo("INVITATION_ALREADY_ACTIVE")
    }

    @Test
    fun `maps missing entry to failed with stable code`() = runTest {
        val entryId = "entry-missing"
        coEvery { waitlistEntryAdmin.findById(entryId) } returns entry(entryId, WaitlistEntryStatus.PENDING)
        coEvery { singleHandler.handle(any()) } throws WaitlistEntryNotFoundException(entryId)

        val result = handler.handle(command(entryIds = listOf(entryId)))

        assertThat(result.results.single().outcome).isEqualTo(BulkInviteOutcome.FAILED)
        assertThat(result.results.single().code).isEqualTo("ENTRY_NOT_FOUND")
    }

    @Test
    fun `maps cancelled entry to failed with stable code`() = runTest {
        val entryId = "entry-cancelled"
        coEvery { waitlistEntryAdmin.findById(entryId) } returns entry(entryId, WaitlistEntryStatus.PENDING)
        coEvery { singleHandler.handle(any()) } throws
            WaitlistEntryNotInvitableException(entryId, "Entry is cancelled")

        val result = handler.handle(command(entryIds = listOf(entryId)))

        assertThat(result.results.single().outcome).isEqualTo(BulkInviteOutcome.FAILED)
        assertThat(result.results.single().code).isEqualTo("ENTRY_NOT_INVITABLE")
    }

    @Test
    fun `maps version conflicts to failed`() = runTest {
        val first = "entry-conflict-a"
        val second = "entry-conflict-b"
        coEvery { waitlistEntryAdmin.findById(any()) } answers {
            entry(firstArg(), WaitlistEntryStatus.PENDING)
        }
        coEvery { singleHandler.handle(match { it.waitlistEntryId == first }) } throws
            InvitationVersionConflictException(first)
        coEvery { singleHandler.handle(match { it.waitlistEntryId == second }) } throws
            OptimisticLockException()

        val result = handler.handle(command(entryIds = listOf(first, second)))

        assertThat(result.results.map { it.code }).containsExactly("VERSION_CONFLICT", "VERSION_CONFLICT")
        assertThat(result.results.map { it.outcome }).containsExactly(
            BulkInviteOutcome.FAILED,
            BulkInviteOutcome.FAILED,
        )
    }

    @Test
    fun `maps raw unique constraint violations to version conflict`() = runTest {
        val entryId = "entry-race"
        coEvery { waitlistEntryAdmin.findById(entryId) } returns entry(entryId, WaitlistEntryStatus.PENDING)
        coEvery { singleHandler.handle(any()) } throws
            RuntimeException("duplicate key value violates unique constraint \"uq_waitlist_invitations_one_active\"")

        val result = handler.handle(command(entryIds = listOf(entryId)))

        assertThat(result.results.single().outcome).isEqualTo(BulkInviteOutcome.FAILED)
        assertThat(result.results.single().code).isEqualTo("VERSION_CONFLICT")
    }

    @Test
    fun `maps unexpected errors to failed without failing the batch`() = runTest {
        val failing = "entry-boom"
        val passing = "entry-ok"
        coEvery { waitlistEntryAdmin.findById(any()) } answers {
            entry(firstArg(), WaitlistEntryStatus.PENDING)
        }
        coEvery { singleHandler.handle(match { it.waitlistEntryId == failing }) } throws
            IllegalStateException("token service down")
        coEvery { singleHandler.handle(match { it.waitlistEntryId == passing }) } returns summary(passing)

        val result = handler.handle(command(entryIds = listOf(failing, passing)))

        assertThat(result.results[0].outcome).isEqualTo(BulkInviteOutcome.FAILED)
        assertThat(result.results[0].code).isEqualTo("UNEXPECTED_ERROR")
        assertThat(result.results[1].outcome).isEqualTo(BulkInviteOutcome.INVITED)
        assertThat(result.summary.invited).isEqualTo(1)
        assertThat(result.summary.failed).isEqualTo(1)
    }

    @Test
    fun `retry of invited entries yields skipped without new single handler calls`() = runTest {
        val entryId = "entry-retry"
        coEvery { waitlistEntryAdmin.findById(entryId) } returns entry(entryId, WaitlistEntryStatus.INVITED)

        val result = handler.handle(command(entryIds = listOf(entryId)))

        assertThat(result.results.single().outcome).isEqualTo(BulkInviteOutcome.SKIPPED)
        coVerify(exactly = 0) { singleHandler.handle(any()) }
    }

    @Test
    fun `runs each entry in its own transaction sequentially`() = runTest {
        val ids = listOf("entry-1", "entry-2", "entry-3")
        coEvery { waitlistEntryAdmin.findById(any()) } answers {
            entry(firstArg(), WaitlistEntryStatus.PENDING)
        }
        coEvery { singleHandler.handle(any()) } answers {
            summary(firstArg<InviteWaitlistEntryCommand>().waitlistEntryId)
        }

        handler.handle(command(entryIds = ids))

        assertThat(transactionRunner.calls).isEqualTo(3)
    }

    @Test
    fun `publishes rejected audit for skipped entries outside the entry transaction`() = runTest {
        val entryId = "entry-invited"
        coEvery { waitlistEntryAdmin.findById(entryId) } returns entry(entryId, WaitlistEntryStatus.INVITED)

        handler.handle(command(entryIds = listOf(entryId)))

        coVerify {
            auditPublisher.publish(
                match<AdminAuditEvent> { event ->
                    event.targetId == entryId &&
                        event.result == AdminAuditResult.REJECTED &&
                        event.reason == "ALREADY_INVITED"
                },
            )
        }
    }

    @Test
    fun `publishes failed audit for failed entries outside the entry transaction`() = runTest {
        val entryId = "entry-gone"
        coEvery { waitlistEntryAdmin.findById(entryId) } returns entry(entryId, WaitlistEntryStatus.PENDING)
        coEvery { singleHandler.handle(any()) } throws WaitlistEntryNotFoundException(entryId)

        handler.handle(command(entryIds = listOf(entryId)))

        coVerify {
            auditPublisher.publish(
                match<AdminAuditEvent> { event ->
                    event.targetId == entryId &&
                        event.result == AdminAuditResult.FAILED &&
                        event.reason == "ENTRY_NOT_FOUND"
                },
            )
        }
    }

    @Test
    fun `records bulk telemetry with outcome counts`() = runTest {
        val pending = "entry-pending"
        val invited = "entry-invited"
        coEvery { waitlistEntryAdmin.findById(pending) } returns entry(pending, WaitlistEntryStatus.PENDING)
        coEvery { waitlistEntryAdmin.findById(invited) } returns entry(invited, WaitlistEntryStatus.INVITED)
        coEvery { singleHandler.handle(any()) } returns summary(pending)

        handler.handle(command(entryIds = listOf(pending, invited)))

        coVerify { telemetry.recordBulkInvite(requested = 2, invited = 1, skipped = 1, failed = 0) }
        coVerify(exactly = 1) { telemetry.recordInvitationCreated() }
    }

    private fun command(roles: Set<PlatformRole> = operatorRoles, entryIds: List<String> = listOf("entry-1")) =
        BulkInviteWaitlistEntriesCommand(
            operatorPrincipalId = operatorId,
            operatorRoles = roles,
            entryIds = entryIds,
        )

    private fun entry(id: String, status: WaitlistEntryStatus) = WaitlistEntry(
        id = WaitlistEntryId(id),
        waitlistId = WaitlistId("waitlist-1"),
        email = EmailAddress("candidate@example.com"),
        normalizedEmail = NormalizedEmail.fromPersisted("candidate@example.com"),
        source = CaptureSource("web"),
        formId = null,
        locale = CaptureLocale("en"),
        metadata = LeadMetadata(),
        consent = WaitlistConsent(earlyAccess = true, marketing = false, version = "1.0"),
        joinedAt = clock.instant().minusSeconds(3600),
        status = status,
        invitedAt = if (status == WaitlistEntryStatus.INVITED ||
            status == WaitlistEntryStatus.CONVERTED
        ) {
            clock.instant().minusSeconds(1800)
        } else {
            null
        },
        convertedAt = if (status == WaitlistEntryStatus.CONVERTED) clock.instant().minusSeconds(900) else null,
    )

    private fun summary(entryId: String) = AdminInvitationSummary(
        id = UUID.fromString("00000000-0000-0000-0000-0000000000a1"),
        waitlistEntryId = entryId,
        status = "ACTIVE",
        issuedAt = clock.instant(),
        expiresAt = clock.instant().plusSeconds(604_800),
        acceptedAt = null,
        revokedAt = null,
        revokedBy = null,
        createdBy = operatorId,
        deliveryStatus = InvitationDeliveryStatus.PENDING.name,
        deliveryAttemptCount = 0,
        version = 0,
    )

    private class PassThroughTransactionRunner : AtomicTransactionRunner {
        var calls = 0

        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T {
            calls++
            return block()
        }
    }
}
