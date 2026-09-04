package com.profiletailors.smp.platformadmin.application.handler

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
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
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.InvitationRepository
import com.profiletailors.smp.platformadmin.application.contracts.InvitationTokenCandidateKey
import com.profiletailors.smp.platformadmin.application.contracts.TokenHasher
import com.profiletailors.smp.platformadmin.application.contracts.WaitlistEntryAdmin
import com.profiletailors.smp.platformadmin.application.contracts.WaitlistInvitationContext
import com.profiletailors.smp.platformadmin.application.contracts.WaitlistInvitationRepository
import com.profiletailors.smp.platformadmin.domain.AdminAuditEvent
import com.profiletailors.smp.platformadmin.domain.Invitation
import com.profiletailors.smp.platformadmin.domain.InvitationAlreadyActiveException
import com.profiletailors.smp.platformadmin.domain.InvitationDeliveryStatus
import com.profiletailors.smp.platformadmin.domain.InvitationId
import com.profiletailors.smp.platformadmin.domain.InvitationIssued
import com.profiletailors.smp.platformadmin.domain.InvitationSource
import com.profiletailors.smp.platformadmin.domain.InvitationStatus
import com.profiletailors.smp.platformadmin.domain.InvitationTarget
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
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
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
    private val entryId = "abcdef01-2345-6789-abcd-ef0123456789"

    private val waitlistEntryAdmin = mockk<WaitlistEntryAdmin>()
    private val invitationRepository = mockk<WaitlistInvitationRepository>()
    private val newInvitationRepository = mockk<InvitationRepository>()
    private val auditPublisher = mockk<AdministrativeAuditPublisher>(relaxed = true)
    private val eventPublisher = mockk<EventPublisher<DomainEvent>>()

    private val tokenHasher = object : TokenHasher, InvitationTokenCandidateKey {
        override fun hash(rawToken: String): String = "hashed-$rawToken"
        override fun matches(rawToken: String, storedHash: String): Boolean = false
        override fun candidateKey(rawToken: String): String = "candidate-$rawToken"
    }

    private val handler = InviteWaitlistEntryHandler(
        waitlistEntryAdmin = waitlistEntryAdmin,
        invitationRepository = invitationRepository,
        newInvitationRepository = newInvitationRepository,
        auditPublisher = auditPublisher,
        eventPublisher = eventPublisher,
        clock = clock,
        invitationTtl = ttl,
        tokenHasher = tokenHasher,
    )

    private val ownerRoles = setOf(PlatformRole.PLATFORM_OWNER)
    private val operatorRoles = setOf(PlatformRole.PLATFORM_OPERATOR)
    private val auditorRoles = setOf(PlatformRole.AUDITOR)

    private val invitationContext = WaitlistInvitationContext(
        recipientEmail = "candidate@example.com",
        workspaceName = "Profile Tailors Beta",
        locale = "en",
    )

    @Test
    fun `throws PlatformAccessDeniedException when operator lacks invite permission`() = runTest {
        val command = command(roles = auditorRoles)
        assertThrows<PlatformAccessDeniedException> { handler.handle(command) }
    }

    @Test
    fun `throws WaitlistEntryNotFoundException when entry does not exist`() = runTest {
        coEvery { waitlistEntryAdmin.findById(entryId) } returns null
        assertThrows<WaitlistEntryNotFoundException> { handler.handle(command()) }
    }

    @Test
    fun `throws WaitlistEntryNotFoundException when invitation context cannot be resolved`() = runTest {
        coEvery { waitlistEntryAdmin.findById(entryId) } returns entry(WaitlistEntryStatus.PENDING)
        coEvery { waitlistEntryAdmin.findInvitationContext(entryId) } returns null
        assertThrows<WaitlistEntryNotFoundException> { handler.handle(command()) }
    }

    @Test
    fun `throws WaitlistEntryAlreadyConvertedException for converted entry`() = runTest {
        coEvery { waitlistEntryAdmin.findById(entryId) } returns entry(WaitlistEntryStatus.CONVERTED)
        coEvery { waitlistEntryAdmin.findInvitationContext(entryId) } returns invitationContext
        assertThrows<WaitlistEntryAlreadyConvertedException> { handler.handle(command()) }
    }

    @Test
    fun `throws InvitationAlreadyActiveException when active invitation exists for pending entry`() = runTest {
        coEvery { waitlistEntryAdmin.findById(entryId) } returns entry(WaitlistEntryStatus.PENDING)
        coEvery { waitlistEntryAdmin.findInvitationContext(entryId) } returns invitationContext
        coEvery { invitationRepository.findActiveByWaitlistEntryId(entryId) } returns activeInvitation()
        assertThrows<InvitationAlreadyActiveException> { handler.handle(command()) }
    }

    @Test
    fun `creates invitation, transitions entry from PENDING to INVITED, and publishes InvitationIssued`() = runTest {
        val pendingEntry = entry(WaitlistEntryStatus.PENDING)
        coEvery { waitlistEntryAdmin.findById(entryId) } returns pendingEntry
        coEvery { waitlistEntryAdmin.findInvitationContext(entryId) } returns invitationContext
        coEvery { invitationRepository.findActiveByWaitlistEntryId(entryId) } returns null
        val savedEntrySlot = slot<WaitlistEntry>()
        coEvery { invitationRepository.save(any()) } answers { firstArg() }
        coEvery { newInvitationRepository.save(any(), any()) } answers { firstArg() }
        coEvery { waitlistEntryAdmin.save(capture(savedEntrySlot)) } answers { savedEntrySlot.captured }
        val eventSlot = slot<DomainEvent>()
        coEvery { eventPublisher.publish(capture(eventSlot)) } returns Unit

        val result = handler.handle(command())

        assertNotNull(result.id)
        assertEquals(WaitlistInvitationStatus.ACTIVE.name, result.status)
        assertEquals(InvitationDeliveryStatus.PENDING.name, result.deliveryStatus)
        assertEquals(clock.instant(), result.issuedAt)
        assertEquals(clock.instant() + ttl, result.expiresAt)

        assertThat(savedEntrySlot.captured.status).isEqualTo(WaitlistEntryStatus.INVITED)
        coVerify { auditPublisher.publish(any<AdminAuditEvent>()) }
        assertThat(eventSlot.captured).isInstanceOf(InvitationIssued::class.java)
        val published = eventSlot.captured as InvitationIssued
        assertThat(published.recipientEmail).isEqualTo("candidate@example.com")
        assertThat(published.workspaceName).isEqualTo("Profile Tailors Beta")
        assertThat(published.locale).isEqualTo("en")
        assertThat(published.rawToken).isNotBlank()
    }

    @Test
    fun `supersedes existing active invitation when entry is already INVITED`() = runTest {
        val invitedEntry = entry(WaitlistEntryStatus.INVITED)
        val existing = existingInvitation()
        coEvery { waitlistEntryAdmin.findById(entryId) } returns invitedEntry
        coEvery { waitlistEntryAdmin.findInvitationContext(entryId) } returns invitationContext
        coEvery { invitationRepository.findActiveByWaitlistEntryId(entryId) } returns activeInvitation()
        coEvery { newInvitationRepository.findBySourceReferenceId(entryId) } returns existing
        val revokedSlot = slot<Invitation>()
        coEvery { newInvitationRepository.updateIfVersionMatches(capture(revokedSlot)) } answers { true }

        handler.handle(command())

        assertThat(revokedSlot.captured.status).isEqualTo(InvitationStatus.REVOKED)
    }

    @Test
    fun `audit event is published after successful invitation`() = runTest {
        coEvery { waitlistEntryAdmin.findById(entryId) } returns entry(WaitlistEntryStatus.PENDING)
        coEvery { waitlistEntryAdmin.findInvitationContext(entryId) } returns invitationContext
        coEvery { invitationRepository.findActiveByWaitlistEntryId(entryId) } returns null
        coEvery { invitationRepository.save(any()) } answers { firstArg() }
        coEvery { newInvitationRepository.save(any(), any()) } answers { firstArg() }
        coEvery { waitlistEntryAdmin.save(any()) } answers { firstArg() }
        coEvery { eventPublisher.publish(any<DomainEvent>()) } returns Unit

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

    private fun existingInvitation(status: InvitationStatus = InvitationStatus.ACTIVE) = Invitation(
        id = InvitationId(UUID.randomUUID()),
        source = InvitationSource.WAITLIST,
        sourceReferenceId = entryId,
        target = InvitationTarget.NEW_WORKSPACE,
        workspaceId = null,
        invitedEmailNormalized = "candidate@example.com",
        tokenHash = "existing-hash",
        status = status,
        issuedBy = operatorId.toString(),
        createdAt = clock.instant().minusSeconds(3600),
        expiresAt = clock.instant().plusSeconds(604_800),
        version = 0,
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
