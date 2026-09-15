package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.leadcapture.common.CaptureSource
import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.LeadMetadata
import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.domain.WaitlistConsent
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntry
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryStatus
import com.profiletailors.leadcapture.waitlist.domain.WaitlistId
import com.profiletailors.smp.platformadmin.application.command.CancelWaitlistEntryCommand
import com.profiletailors.smp.platformadmin.application.contracts.AdminWaitlistQuery
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.WaitlistEntryAdmin
import com.profiletailors.smp.platformadmin.application.contracts.WaitlistInvitationRepository
import com.profiletailors.smp.platformadmin.application.model.AdminWaitlistEntryDetail
import com.profiletailors.smp.platformadmin.domain.InvitationDeliveryStatus
import com.profiletailors.smp.platformadmin.domain.PlatformAccessDeniedException
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryAlreadyConvertedException
import com.profiletailors.smp.platformadmin.domain.WaitlistEntryNotFoundException
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitation
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationId
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class CancelWaitlistEntryHandlerTest {

    private val clock = Clock.fixed(Instant.parse("2026-07-30T10:00:00Z"), ZoneOffset.UTC)
    private val operatorId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val entryId = "entry-xyz-456"

    private val waitlistEntryAdmin = mockk<WaitlistEntryAdmin>()
    private val invitationRepository = mockk<WaitlistInvitationRepository>()
    private val auditPublisher = mockk<AdministrativeAuditPublisher>(relaxed = true)
    private val adminWaitlistQuery = mockk<AdminWaitlistQuery>()

    private val handler = CancelWaitlistEntryHandler(
        waitlistEntryAdmin = waitlistEntryAdmin,
        invitationRepository = invitationRepository,
        auditPublisher = auditPublisher,
        adminWaitlistQuery = adminWaitlistQuery,
        clock = clock,
    )

    private val operatorRoles = setOf(PlatformRole.PLATFORM_OPERATOR)
    private val supportRoles = setOf(PlatformRole.SUPPORT_AGENT)

    private fun entryDetail(version: Long = 1) = AdminWaitlistEntryDetail(
        id = entryId,
        waitlistId = "wl-1",
        waitlistKey = "test-key",
        email = "test@example.com",
        normalizedEmail = "test@example.com",
        status = "PENDING",
        joinedAt = Instant.now(),
        invitedAt = null,
        convertedAt = null,
        cancelledAt = null,
        preferredLocale = null,
        earlyAccessConsent = true,
        marketingConsent = false,
        consentVersion = "v1",
        source = "web",
        metadataSummary = emptyMap(),
        invitationHistory = emptyList(),
        version = version,
    )

    @Test
    fun `throws PlatformAccessDeniedException when operator lacks cancel permission`() = runTest {
        assertThrows<PlatformAccessDeniedException> {
            handler.handle(command(roles = supportRoles))
        }
    }

    @Test
    fun `throws WaitlistEntryNotFoundException when entry does not exist`() = runTest {
        coEvery { waitlistEntryAdmin.findById(entryId) } returns null
        coEvery { adminWaitlistQuery.findById(entryId) } returns null
        assertThrows<WaitlistEntryNotFoundException> { handler.handle(command()) }
    }

    @Test
    fun `throws WaitlistEntryAlreadyConvertedException for converted entry`() = runTest {
        coEvery { waitlistEntryAdmin.findById(entryId) } returns entry(WaitlistEntryStatus.CONVERTED)
        coEvery { adminWaitlistQuery.findById(entryId) } returns entryDetail()
        assertThrows<WaitlistEntryAlreadyConvertedException> { handler.handle(command()) }
    }

    @Test
    fun `cancels pending entry and records audit event`() = runTest {
        coEvery { waitlistEntryAdmin.findById(entryId) } returns entry(WaitlistEntryStatus.PENDING)
        coEvery { adminWaitlistQuery.findById(entryId) } returns entryDetail()
        coEvery { invitationRepository.findActiveByWaitlistEntryId(entryId) } returns null
        coEvery { waitlistEntryAdmin.save(any()) } answers { firstArg() }

        handler.handle(command())

        coVerify { waitlistEntryAdmin.save(match { it.status == WaitlistEntryStatus.CANCELLED }) }
        coVerify { auditPublisher.publish(match { it.action.name == "WAITLIST_ENTRY_CANCELLED" }) }
    }

    @Test
    fun `cancels invited entry and revokes active invitation`() = runTest {
        val invitedEntry = entry(WaitlistEntryStatus.INVITED)
        val activeInv = WaitlistInvitation(
            id = WaitlistInvitationId.generate(),
            waitlistEntryId = entryId,
            tokenHash = "hash",
            status = WaitlistInvitationStatus.ACTIVE,
            issuedAt = clock.instant().minusSeconds(3600),
            expiresAt = clock.instant().plusSeconds(604_800),
            createdBy = operatorId,
            deliveryStatus = InvitationDeliveryStatus.SENT,
        )
        coEvery { waitlistEntryAdmin.findById(entryId) } returns invitedEntry
        coEvery { invitationRepository.findActiveByWaitlistEntryId(entryId) } returns activeInv
        coEvery { adminWaitlistQuery.findById(entryId) } returns entryDetail()
        coEvery { invitationRepository.update(any()) } answers { firstArg() }
        coEvery { waitlistEntryAdmin.save(any()) } answers { firstArg() }

        handler.handle(command())

        coVerify { invitationRepository.update(match { it.status == WaitlistInvitationStatus.REVOKED }) }
        coVerify { waitlistEntryAdmin.save(match { it.status == WaitlistEntryStatus.CANCELLED }) }
    }

    @Test
    fun `cancellation reason is included in audit event`() = runTest {
        coEvery { waitlistEntryAdmin.findById(entryId) } returns entry(WaitlistEntryStatus.PENDING)
        coEvery { adminWaitlistQuery.findById(entryId) } returns entryDetail()
        coEvery { invitationRepository.findActiveByWaitlistEntryId(entryId) } returns null
        coEvery { waitlistEntryAdmin.save(any()) } answers { firstArg() }

        handler.handle(command(reason = "spam"))

        coVerify { auditPublisher.publish(match { it.reason == "spam" }) }
    }

    private fun command(
        roles: Set<PlatformRole> = operatorRoles,
        reason: String = "test reason",
        expectedVersion: Long = 1,
    ) = CancelWaitlistEntryCommand(
        operatorPrincipalId = operatorId,
        operatorRoles = roles,
        waitlistEntryId = entryId,
        reason = reason,
        expectedVersion = expectedVersion,
    )

    private fun entry(status: WaitlistEntryStatus) = WaitlistEntry(
        id = WaitlistEntryId(entryId),
        waitlistId = WaitlistId("waitlist-1"),
        email = EmailAddress("candidate@example.com"),
        normalizedEmail = NormalizedEmail.fromPersisted("candidate@example.com"),
        source = CaptureSource("web"),
        formId = null,
        locale = null,
        metadata = LeadMetadata(),
        consent = WaitlistConsent(earlyAccess = true, marketing = false, version = "1.0"),
        joinedAt = clock.instant().minusSeconds(7200),
        status = status,
        invitedAt = if (status == WaitlistEntryStatus.INVITED ||
            status == WaitlistEntryStatus.CONVERTED
        ) {
            clock.instant().minusSeconds(3600)
        } else {
            null
        },
        convertedAt = if (status == WaitlistEntryStatus.CONVERTED) clock.instant().minusSeconds(1800) else null,
    )
}
