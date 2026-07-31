package com.profiletailors.smp.platformadmin.application.handler

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
import com.profiletailors.smp.platformadmin.application.command.InviteWaitlistEntryCommand
import com.profiletailors.smp.platformadmin.application.ports.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.ports.TokenHasher
import com.profiletailors.smp.platformadmin.application.ports.WaitlistEntryAdminPort
import com.profiletailors.smp.platformadmin.application.ports.WaitlistInvitationRepository
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.InvitationAlreadyActiveException
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class InviteWaitlistEntryHandlerTest {

    private val clock = Clock.fixed(Instant.parse("2026-07-30T10:00:00Z"), ZoneOffset.UTC)
    private val ttl = Duration.ofDays(7)
    private val operatorId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val entryId = "entry-abc-123"

    private val waitlistEntryPort = mockk<WaitlistEntryAdminPort>()
    private val invitationRepository = mockk<WaitlistInvitationRepository>()
    private val auditPublisher = mockk<AdministrativeAuditPublisher>(relaxed = true)

    private val tokenHasher = object : TokenHasher {
        override fun hash(rawToken: String): String = "hashed-$rawToken"
        override fun matches(rawToken: String, storedHash: String): Boolean = false
    }

    private val handler = InviteWaitlistEntryHandler(
        waitlistEntryPort = waitlistEntryPort,
        invitationRepository = invitationRepository,
        auditPublisher = auditPublisher,
        clock = clock,
        invitationTtl = ttl,
        tokenHasher = tokenHasher,
    )

    private val ownerRoles = setOf(PlatformRole.PLATFORM_OWNER)
    private val operatorRoles = setOf(PlatformRole.PLATFORM_OPERATOR)
    private val auditorRoles = setOf(PlatformRole.AUDITOR)

    @Test
    fun `throws PlatformAccessDeniedException when operator lacks invite permission`() = runTest {
        val command = command(roles = auditorRoles)
        assertThrows<PlatformAccessDeniedException> { handler.handle(command) }
    }

    @Test
    fun `throws WaitlistEntryNotFoundException when entry does not exist`() = runTest {
        coEvery { waitlistEntryPort.findById(entryId) } returns null
        assertThrows<WaitlistEntryNotFoundException> { handler.handle(command()) }
    }

    @Test
    fun `throws WaitlistEntryAlreadyConvertedException for converted entry`() = runTest {
        coEvery { waitlistEntryPort.findById(entryId) } returns entry(WaitlistEntryStatus.CONVERTED)
        assertThrows<WaitlistEntryAlreadyConvertedException> { handler.handle(command()) }
    }

    @Test
    fun `throws InvitationAlreadyActiveException when active invitation exists for pending entry`() = runTest {
        coEvery { waitlistEntryPort.findById(entryId) } returns entry(WaitlistEntryStatus.PENDING)
        coEvery { invitationRepository.findActiveByWaitlistEntryId(entryId) } returns activeInvitation()
        assertThrows<InvitationAlreadyActiveException> { handler.handle(command()) }
    }

    @Test
    fun `creates invitation and transitions entry from PENDING to INVITED`() = runTest {
        val pendingEntry = entry(WaitlistEntryStatus.PENDING)
        coEvery { waitlistEntryPort.findById(entryId) } returns pendingEntry
        coEvery { invitationRepository.findActiveByWaitlistEntryId(entryId) } returns null
        coEvery { invitationRepository.save(any()) } answers { firstArg() }
        coEvery { waitlistEntryPort.save(any()) } answers { firstArg() }

        val result = handler.handle(command())

        assertNotNull(result.id)
        assertEquals(WaitlistInvitationStatus.ACTIVE.name, result.status)
        assertEquals(InvitationDeliveryStatus.PENDING.name, result.deliveryStatus)
        assertEquals(clock.instant(), result.issuedAt)
        assertEquals(clock.instant() + ttl, result.expiresAt)

        coVerify { waitlistEntryPort.save(match { it.status == WaitlistEntryStatus.INVITED }) }
        coVerify { auditPublisher.publish(any<AdminAuditEvent>()) }
    }

    @Test
    fun `supersedes existing active invitation when entry is already INVITED`() = runTest {
        val invitedEntry = entry(WaitlistEntryStatus.INVITED)
        val existing = activeInvitation()
        coEvery { waitlistEntryPort.findById(entryId) } returns invitedEntry
        coEvery { invitationRepository.findActiveByWaitlistEntryId(entryId) } returns existing
        coEvery { invitationRepository.update(any()) } answers { firstArg() }
        coEvery { invitationRepository.save(any()) } answers { firstArg() }

        handler.handle(command())

        coVerify { invitationRepository.update(match { it.status == WaitlistInvitationStatus.SUPERSEDED }) }
        coVerify { invitationRepository.save(match { it.status == WaitlistInvitationStatus.ACTIVE }) }
    }

    @Test
    fun `audit event is published after successful invitation`() = runTest {
        coEvery { waitlistEntryPort.findById(entryId) } returns entry(WaitlistEntryStatus.PENDING)
        coEvery { invitationRepository.findActiveByWaitlistEntryId(entryId) } returns null
        coEvery { invitationRepository.save(any()) } answers { firstArg() }
        coEvery { waitlistEntryPort.save(any()) } answers { firstArg() }

        handler.handle(command())

        coVerify {
            auditPublisher.publish(
                match { event ->
                    event.action.name == "WAITLIST_ENTRY_INVITED" &&
                        event.targetId == entryId &&
                        event.operatorPrincipalId == operatorId
                },
            )
        }
    }

    private fun command(roles: Set<PlatformRole> = operatorRoles) = InviteWaitlistEntryCommand(
        operatorPrincipalId = operatorId,
        operatorRoles = roles,
        waitlistEntryId = entryId,
    )

    private fun entry(status: WaitlistEntryStatus) = WaitlistEntry(
        id = WaitlistEntryId(entryId),
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

    private fun activeInvitation() = WaitlistInvitation(
        id = WaitlistInvitationId.generate(),
        waitlistEntryId = entryId,
        tokenHash = "existing-hash",
        status = WaitlistInvitationStatus.ACTIVE,
        issuedAt = clock.instant().minusSeconds(3600),
        expiresAt = clock.instant().plusSeconds(604_800),
        createdBy = operatorId,
        deliveryStatus = InvitationDeliveryStatus.SENT,
    )
}
