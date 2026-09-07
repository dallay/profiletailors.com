package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.common.domain.workspace.WorkspaceMembershipSnapshot
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
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
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.domain.PrincipalIdentityFacts
import com.profiletailors.smp.platformadmin.application.contracts.InvitationRepository
import com.profiletailors.smp.platformadmin.application.contracts.InvitationTokenCandidateKey
import com.profiletailors.smp.platformadmin.application.contracts.TokenHasher
import com.profiletailors.smp.platformadmin.application.contracts.WaitlistEntryAdmin
import com.profiletailors.smp.platformadmin.domain.Invitation
import com.profiletailors.smp.platformadmin.domain.InvitationId
import com.profiletailors.smp.platformadmin.domain.InvitationSource
import com.profiletailors.smp.platformadmin.domain.InvitationStatus
import com.profiletailors.smp.platformadmin.domain.InvitationTarget
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipProvisioner
import com.profiletailors.smp.tenancy.application.WorkspaceProvisioningService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class InvitationActivationCoordinatorTest {
    private val now = Instant.parse("2026-08-09T10:00:00Z")
    private val invitationId = InvitationId(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))

    @Test
    fun `converts waitlist entry when accepting a new workspace invitation`() = runTest {
        val invitationRepository = mockk<InvitationRepository>()
        val identityLookup = mockk<PrincipalIdentityLookup>()
        val workspaceProvisioningService = mockk<WorkspaceProvisioningService>()
        val membershipProvisioner = mockk<WorkspaceMembershipProvisioner>()
        val waitlistEntryAdmin = mockk<WaitlistEntryAdmin>()
        val entry = invitedEntry()
        val invitation = waitlistInvitation()

        coEvery { invitationRepository.findByCandidateKeyForUpdate("candidate-key") } returns invitation
        coEvery { identityLookup.findByPrincipalId("principal-1") } returns identity()
        coEvery {
            workspaceProvisioningService.provisionDefaultWorkspace("principal-1", "candidate@example.com")
        } returns WorkspaceProvisioningService.ProvisionedWorkspace(
            workspaceId = "workspace-1",
            name = "Candidate's Workspace",
            membershipStatus = WorkspaceMembershipStatus.ACTIVE,
        )
        coEvery { membershipProvisioner.reconcile("workspace-1", "principal-1") } returns activeMembership()
        coEvery { waitlistEntryAdmin.findById("entry-1") } returns entry
        coEvery { waitlistEntryAdmin.save(entry) } returns entry
        coEvery { invitationRepository.updateIfVersionMatches(any()) } returns true

        val result = coordinator(
            invitationRepository = invitationRepository,
            identityLookup = identityLookup,
            workspaceProvisioningService = workspaceProvisioningService,
            membershipProvisioner = membershipProvisioner,
            waitlistEntryAdmin = waitlistEntryAdmin,
        ).activateForRegistration(
            rawToken = "raw-token",
            email = "candidate@example.com",
            principalId = "principal-1",
        )

        assertEquals(WaitlistEntryStatus.CONVERTED, entry.status)
        assertEquals(now, entry.convertedAt)
        assertEquals(InvitationStatus.ACCEPTED, result.invitation.status)
        assertEquals("workspace-1", result.invitation.workspaceId)
        assertEquals(WorkspaceMembershipStatus.ACTIVE, result.membershipStatus)
        coVerify(exactly = 1) { waitlistEntryAdmin.findById("entry-1") }
        coVerify(exactly = 1) { waitlistEntryAdmin.save(entry) }
    }

    private fun coordinator(
        invitationRepository: InvitationRepository,
        identityLookup: PrincipalIdentityLookup,
        workspaceProvisioningService: WorkspaceProvisioningService,
        membershipProvisioner: WorkspaceMembershipProvisioner,
        waitlistEntryAdmin: WaitlistEntryAdmin,
    ) = InvitationActivationCoordinator(
        invitationRepository = invitationRepository,
        tokenHasher = InvitationTokenFixture,
        principalIdentityLookup = identityLookup,
        workspaceProvisioningService = workspaceProvisioningService,
        membershipProvisioner = membershipProvisioner,
        transactionRunner = InlineTransactionRunner,
        clock = Clock.fixed(now, ZoneOffset.UTC),
        waitlistEntryAdmin = waitlistEntryAdmin,
    )

    private fun identity() = PrincipalIdentityFacts(
        principalId = "principal-1",
        principalType = PrincipalType.USER,
        subject = "subject-1",
        provider = "local",
        displayIdentity = "Candidate",
        email = "candidate@example.com",
        username = "candidate",
    )

    private fun waitlistInvitation() = Invitation(
        id = invitationId,
        source = InvitationSource.WAITLIST,
        sourceReferenceId = "entry-1",
        target = InvitationTarget.NEW_WORKSPACE,
        workspaceId = null,
        invitedEmailNormalized = "candidate@example.com",
        tokenHash = "token-hash",
        status = InvitationStatus.ACTIVE,
        issuedBy = "operator-1",
        createdAt = now.minusSeconds(60),
        expiresAt = now.plusSeconds(3600),
    )

    private fun invitedEntry() = WaitlistEntry(
        id = WaitlistEntryId("entry-1"),
        waitlistId = WaitlistId("waitlist-1"),
        email = EmailAddress("candidate@example.com"),
        normalizedEmail = NormalizedEmail.fromPersisted("candidate@example.com"),
        source = CaptureSource("web"),
        formId = null,
        locale = CaptureLocale("en"),
        metadata = LeadMetadata(),
        consent = WaitlistConsent(earlyAccess = true, marketing = false, version = "1.0"),
        joinedAt = now.minusSeconds(3600),
        status = WaitlistEntryStatus.INVITED,
        invitedAt = now.minusSeconds(1800),
    )

    private fun activeMembership() = object : WorkspaceMembershipSnapshot {
        override val id = "membership-1"
        override val workspaceId = "workspace-1"
        override val principalId = "principal-1"
        override val principalType = PrincipalType.USER
        override val status = WorkspaceMembershipStatus.ACTIVE
        override val roleKeys = emptySet<String>()
    }
}

private object InvitationTokenFixture : TokenHasher, InvitationTokenCandidateKey {
    override fun hash(rawToken: String): String = "token-hash"

    override fun matches(rawToken: String, storedHash: String): Boolean =
        rawToken == "raw-token" && storedHash == "token-hash"

    override fun candidateKey(rawToken: String): String = "candidate-key"
}

private object InlineTransactionRunner : AtomicTransactionRunner {
    override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
}
