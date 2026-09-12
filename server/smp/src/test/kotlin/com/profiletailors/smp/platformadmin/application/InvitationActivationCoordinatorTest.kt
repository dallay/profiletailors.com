package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.context.PrincipalType
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
import com.profiletailors.smp.identity.application.InvitationRegistrationContext
import com.profiletailors.smp.identity.application.InvitationRegistrationSource
import com.profiletailors.smp.identity.application.InvitationRegistrationTarget
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.identity.domain.PrincipalIdentityFacts
import com.profiletailors.smp.platformadmin.application.contracts.InvitationRepository
import com.profiletailors.smp.platformadmin.application.contracts.InvitationTelemetry
import com.profiletailors.smp.platformadmin.application.contracts.InvitationTokenCandidateKey
import com.profiletailors.smp.platformadmin.application.contracts.TokenHasher
import com.profiletailors.smp.platformadmin.application.contracts.WaitlistEntryAdmin
import com.profiletailors.smp.platformadmin.domain.Invitation
import com.profiletailors.smp.platformadmin.domain.InvitationAcceptanceFailureCode
import com.profiletailors.smp.platformadmin.domain.InvitationId
import com.profiletailors.smp.platformadmin.domain.InvitationNotAcceptableException
import com.profiletailors.smp.platformadmin.domain.InvitationSource
import com.profiletailors.smp.platformadmin.domain.InvitationStatus
import com.profiletailors.smp.platformadmin.domain.InvitationTarget
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipProvisioner
import com.profiletailors.smp.tenancy.application.WorkspaceProvisioningService
import com.profiletailors.smp.tenancy.domain.WorkspaceMembership
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class InvitationActivationCoordinatorTest {

    private val invitationRepository = mockk<InvitationRepository>()
    private val tokenHasher = mockk<CandidateKeyTokenHasher>()
    private val principalIdentityLookup = mockk<PrincipalIdentityLookup>()
    private val workspaceProvisioningService = mockk<WorkspaceProvisioningService>()
    private val membershipProvisioner = mockk<WorkspaceMembershipProvisioner>()
    private val waitlistEntryAdmin = mockk<WaitlistEntryAdmin>()
    private val telemetry = mockk<InvitationTelemetry>(relaxed = true)
    private val now = Instant.parse("2026-08-15T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val coordinator = InvitationActivationCoordinator(
        invitationRepository = invitationRepository,
        tokenHasher = tokenHasher,
        principalIdentityLookup = principalIdentityLookup,
        workspaceProvisioningService = workspaceProvisioningService,
        waitlistEntryAdmin = waitlistEntryAdmin,
        membershipProvisioner = membershipProvisioner,
        clock = clock,
        telemetry = telemetry,
    )

    @Test
    fun `activates NEW_WORKSPACE invitation successfully`() = runTest {
        val rawToken = "secret-token"
        val candidateKey = "cand-123"
        val email = "User@Example.com"
        val principalId = "principal-123"

        val invitation = createInvitation(
            target = InvitationTarget.NEW_WORKSPACE,
            workspaceId = null,
            status = InvitationStatus.ACTIVE,
        )

        val principalFacts = PrincipalIdentityFacts(
            principalId = principalId,
            principalType = PrincipalType.USER,
            subject = "sub-1",
            provider = "local",
            displayIdentity = "User",
            email = "user@example.com",
            username = "user",
            emailStatus = EmailStatus.VERIFIED,
        )

        coEvery { tokenHasher.candidateKey(rawToken) } returns candidateKey
        coEvery { invitationRepository.findByCandidateKeyForUpdate(candidateKey) } returns invitation
        coEvery { tokenHasher.matches(rawToken, invitation.tokenHash) } returns true
        coEvery { principalIdentityLookup.findByPrincipalId(principalId) } returns principalFacts
        coEvery {
            workspaceProvisioningService.provisionDefaultWorkspace(principalId, "user@example.com")
        } returns
            WorkspaceProvisioningService.ProvisionedWorkspace("ws-new", "Default", WorkspaceMembershipStatus.ACTIVE)
        coEvery { invitationRepository.updateIfVersionMatches(any()) } returns true
        coEvery {
            membershipProvisioner.reconcile("ws-new", principalId)
        } returns
            WorkspaceMembership("wm-1", "ws-new", principalId, PrincipalType.USER, WorkspaceMembershipStatus.ACTIVE)

        val result = coordinator.activateForRegistration(rawToken, email, principalId)

        assertEquals(InvitationStatus.ACCEPTED, result.invitation.status)
        assertEquals("ws-new", result.invitation.workspaceId)
        assertEquals(WorkspaceMembershipStatus.ACTIVE, result.membershipStatus)

        coVerify { workspaceProvisioningService.provisionDefaultWorkspace(principalId, "user@example.com") }
    }

    @Test
    fun `activates EXISTING_WORKSPACE invitation successfully`() = runTest {
        val rawToken = "secret-token"
        val candidateKey = "cand-123"
        val email = "user@example.com"
        val principalId = "principal-123"

        val invitation = createInvitation(
            target = InvitationTarget.EXISTING_WORKSPACE,
            workspaceId = "ws-existing",
            status = InvitationStatus.ACTIVE,
        )

        val principalFacts = PrincipalIdentityFacts(
            principalId = principalId,
            principalType = PrincipalType.USER,
            subject = "sub-1",
            provider = "local",
            displayIdentity = "User",
            email = "user@example.com",
            username = "user",
            emailStatus = EmailStatus.VERIFIED,
        )

        coEvery { tokenHasher.candidateKey(rawToken) } returns candidateKey
        coEvery { invitationRepository.findByCandidateKeyForUpdate(candidateKey) } returns invitation
        coEvery { tokenHasher.matches(rawToken, invitation.tokenHash) } returns true
        coEvery { principalIdentityLookup.findByPrincipalId(principalId) } returns principalFacts
        coEvery { invitationRepository.updateIfVersionMatches(any()) } returns true
        coEvery {
            membershipProvisioner.reconcile("ws-existing", principalId)
        } returns
            WorkspaceMembership(
                "wm-2",
                "ws-existing",
                principalId,
                PrincipalType.USER,
                WorkspaceMembershipStatus.ACTIVE,
            )

        val result = coordinator.activateForRegistration(rawToken, email, principalId)

        assertEquals(InvitationStatus.ACCEPTED, result.invitation.status)
        assertEquals("ws-existing", result.invitation.workspaceId)
        assertEquals(WorkspaceMembershipStatus.ACTIVE, result.membershipStatus)

        coVerify(exactly = 0) { workspaceProvisioningService.provisionDefaultWorkspace(any(), any()) }
    }

    @Test
    fun `converts a waitlist invitation before accepting it`() = runTest {
        val rawToken = "secret-token"
        val candidateKey = "cand-123"
        val principalId = "principal-123"
        val invitation = createInvitation(
            target = InvitationTarget.NEW_WORKSPACE,
            workspaceId = null,
            status = InvitationStatus.ACTIVE,
        ).copy(source = InvitationSource.WAITLIST, sourceReferenceId = "entry-123")
        val principalFacts = PrincipalIdentityFacts(
            principalId = principalId,
            principalType = PrincipalType.USER,
            subject = "sub-1",
            provider = "local",
            displayIdentity = "User",
            email = "user@example.com",
            username = "user",
            emailStatus = EmailStatus.VERIFIED,
        )
        val entry = WaitlistEntry(
            id = WaitlistEntryId("entry-123"),
            waitlistId = WaitlistId("waitlist-1"),
            email = EmailAddress("user@example.com"),
            normalizedEmail = NormalizedEmail.fromPersisted("user@example.com"),
            source = CaptureSource("test"),
            formId = null,
            locale = CaptureLocale("en"),
            metadata = LeadMetadata(),
            consent = WaitlistConsent(earlyAccess = true, marketing = false, version = "1.0"),
            joinedAt = now.minusSeconds(3600),
            status = WaitlistEntryStatus.PENDING,
        )
        entry.invite(now.minusSeconds(1800))

        coEvery { tokenHasher.candidateKey(rawToken) } returns candidateKey
        coEvery { invitationRepository.findByCandidateKeyForUpdate(candidateKey) } returns invitation
        coEvery { tokenHasher.matches(rawToken, invitation.tokenHash) } returns true
        coEvery { principalIdentityLookup.findByPrincipalId(principalId) } returns principalFacts
        coEvery { waitlistEntryAdmin.findById("entry-123") } returns entry
        coEvery { waitlistEntryAdmin.save(entry) } returns entry
        coEvery { invitationRepository.updateIfVersionMatches(any()) } returns true
        coEvery { workspaceProvisioningService.provisionDefaultWorkspace(principalId, "user@example.com") } returns
            WorkspaceProvisioningService.ProvisionedWorkspace("ws-new", "Default", WorkspaceMembershipStatus.ACTIVE)
        coEvery { membershipProvisioner.reconcile("ws-new", principalId) } returns
            WorkspaceMembership("wm-1", "ws-new", principalId, PrincipalType.USER, WorkspaceMembershipStatus.ACTIVE)

        val result = coordinator.activateForRegistration(rawToken, "user@example.com", principalId)

        assertEquals(InvitationStatus.ACCEPTED, result.invitation.status)
        assertEquals(WaitlistEntryStatus.CONVERTED, entry.status)
        coVerify { waitlistEntryAdmin.save(entry) }
    }

    @Test
    fun `should fail when waitlist entry is missing during conversion`() = runTest {
        val rawToken = "secret-token"
        val candidateKey = "cand-123"
        val principalId = "principal-123"
        val invitation = createInvitation(
            target = InvitationTarget.NEW_WORKSPACE,
            workspaceId = null,
            status = InvitationStatus.ACTIVE,
        ).copy(source = InvitationSource.WAITLIST, sourceReferenceId = "entry-123")
        val principalFacts = PrincipalIdentityFacts(
            principalId = principalId,
            principalType = PrincipalType.USER,
            subject = "sub-1",
            provider = "local",
            displayIdentity = "User",
            email = "user@example.com",
            username = "user",
            emailStatus = EmailStatus.VERIFIED,
        )

        coEvery { tokenHasher.candidateKey(rawToken) } returns candidateKey
        coEvery { invitationRepository.findByCandidateKeyForUpdate(candidateKey) } returns invitation
        coEvery { tokenHasher.matches(rawToken, invitation.tokenHash) } returns true
        coEvery { principalIdentityLookup.findByPrincipalId(principalId) } returns principalFacts
        coEvery { workspaceProvisioningService.provisionDefaultWorkspace(principalId, "user@example.com") } returns
            WorkspaceProvisioningService.ProvisionedWorkspace("ws-new", "Default", WorkspaceMembershipStatus.ACTIVE)
        coEvery { waitlistEntryAdmin.findById("entry-123") } returns null

        val exception = shouldThrow<IllegalStateException> {
            coordinator.activateForRegistration(rawToken, "user@example.com", principalId)
        }

        exception.message shouldBe "Waitlist entry not found"
    }

    @Test
    fun `should record expired telemetry when invitation is expired`() = runTest {
        val rawToken = "secret-token"
        val candidateKey = "cand-123"
        val expiredInvitation = createInvitation(status = InvitationStatus.EXPIRED)

        coEvery { tokenHasher.candidateKey(rawToken) } returns candidateKey
        coEvery { invitationRepository.findByCandidateKeyForUpdate(candidateKey) } returns expiredInvitation
        coEvery { tokenHasher.matches(rawToken, expiredInvitation.tokenHash) } returns true

        val exception = shouldThrow<InvitationNotAcceptableException> {
            coordinator.activateForRegistration(rawToken, "user@example.com", "principal-1")
        }

        exception.failureCode shouldBe InvitationAcceptanceFailureCode.EXPIRED
        verify(exactly = 1) { telemetry.recordInvitationExpired() }
    }

    @Test
    fun `should record replay telemetry when invitation is already accepted`() = runTest {
        val rawToken = "secret-token"
        val candidateKey = "cand-123"
        val acceptedInvitation = createInvitation(
            target = InvitationTarget.EXISTING_WORKSPACE,
            workspaceId = "ws-existing",
        ).accept(now, "principal-1")

        coEvery { tokenHasher.candidateKey(rawToken) } returns candidateKey
        coEvery { invitationRepository.findByCandidateKeyForUpdate(candidateKey) } returns acceptedInvitation
        coEvery { tokenHasher.matches(rawToken, acceptedInvitation.tokenHash) } returns true

        val exception = shouldThrow<InvitationNotAcceptableException> {
            coordinator.activateForRegistration(rawToken, "user@example.com", "principal-1")
        }

        exception.failureCode shouldBe InvitationAcceptanceFailureCode.ALREADY_CONSUMED
        verify(exactly = 1) { telemetry.recordInvitationReplayRejected() }
    }

    @Test
    fun `should reject preparation when candidate key is unknown`() = runTest {
        val rawToken = "token"
        val candidateKey = "cand"

        coEvery { tokenHasher.candidateKey(rawToken) } returns candidateKey
        coEvery { invitationRepository.findByCandidateKey(candidateKey) } returns null

        val exception = shouldThrow<InvitationNotAcceptableException> {
            coordinator.prepare(rawToken, "user@example.com")
        }

        exception.failureCode shouldBe InvitationAcceptanceFailureCode.INVALID
    }

    @Test
    fun `should reject preparation and record telemetry when invitation is expired`() = runTest {
        val rawToken = "token"
        val candidateKey = "cand"
        val expiredInvitation = createInvitation(status = InvitationStatus.EXPIRED)

        coEvery { tokenHasher.candidateKey(rawToken) } returns candidateKey
        coEvery { invitationRepository.findByCandidateKey(candidateKey) } returns expiredInvitation
        coEvery { tokenHasher.matches(rawToken, expiredInvitation.tokenHash) } returns true

        val exception = shouldThrow<InvitationNotAcceptableException> {
            coordinator.prepare(rawToken, "user@example.com")
        }

        exception.failureCode shouldBe InvitationAcceptanceFailureCode.EXPIRED
        verify(exactly = 1) { telemetry.recordInvitationExpired() }
    }

    @Test
    fun `should reject preparation when invitation is revoked`() = runTest {
        val rawToken = "token"
        val candidateKey = "cand"
        val revokedInvitation = createInvitation(status = InvitationStatus.REVOKED)

        coEvery { tokenHasher.candidateKey(rawToken) } returns candidateKey
        coEvery { invitationRepository.findByCandidateKey(candidateKey) } returns revokedInvitation
        coEvery { tokenHasher.matches(rawToken, revokedInvitation.tokenHash) } returns true

        val exception = shouldThrow<InvitationNotAcceptableException> {
            coordinator.prepare(rawToken, "user@example.com")
        }

        exception.failureCode shouldBe InvitationAcceptanceFailureCode.REVOKED
    }

    @Test
    fun `should reject preparation and record telemetry when invitation is already accepted`() = runTest {
        val rawToken = "token"
        val candidateKey = "cand"
        val acceptedInvitation = createInvitation().accept(now, "principal-1", "ws-new")

        coEvery { tokenHasher.candidateKey(rawToken) } returns candidateKey
        coEvery { invitationRepository.findByCandidateKey(candidateKey) } returns acceptedInvitation
        coEvery { tokenHasher.matches(rawToken, acceptedInvitation.tokenHash) } returns true

        val exception = shouldThrow<InvitationNotAcceptableException> {
            coordinator.prepare(rawToken, "user@example.com")
        }

        exception.failureCode shouldBe InvitationAcceptanceFailureCode.ALREADY_CONSUMED
        verify(exactly = 1) { telemetry.recordInvitationReplayRejected() }
    }

    @Test
    fun `should reject preparation when invitation email does not match`() = runTest {
        val rawToken = "token"
        val candidateKey = "cand"
        val validInvitation = createInvitation(status = InvitationStatus.ACTIVE)

        coEvery { tokenHasher.candidateKey(rawToken) } returns candidateKey
        coEvery { invitationRepository.findByCandidateKey(candidateKey) } returns validInvitation
        coEvery { tokenHasher.matches(rawToken, validInvitation.tokenHash) } returns true

        val exception = shouldThrow<InvitationNotAcceptableException> {
            coordinator.prepare(rawToken, "mismatch@example.com")
        }

        exception.failureCode shouldBe InvitationAcceptanceFailureCode.EMAIL_MISMATCH
    }

    @Test
    fun `should reject completion when context invitation id does not match`() = runTest {
        val rawToken = "raw-token"
        val candidateKey = "cand-123"
        val invitation = createInvitation(
            target = InvitationTarget.EXISTING_WORKSPACE,
            workspaceId = "ws-1",
            status = InvitationStatus.ACTIVE,
        )

        coEvery { tokenHasher.candidateKey(rawToken) } returns candidateKey
        coEvery { invitationRepository.findByCandidateKeyForUpdate(candidateKey) } returns invitation
        coEvery { tokenHasher.matches(rawToken, invitation.tokenHash) } returns true

        val context = InvitationRegistrationContext(
            invitationId = UUID.randomUUID().toString(),
            target = InvitationRegistrationTarget.EXISTING_WORKSPACE,
            workspaceId = "ws-1",
            source = InvitationRegistrationSource.DIRECT,
        )

        val exception = shouldThrow<InvitationNotAcceptableException> {
            coordinator.complete(context, rawToken, "principal-123", "User")
        }

        exception.failureCode shouldBe InvitationAcceptanceFailureCode.INVALID
    }

    @Test
    fun `should reject completion when context target does not match`() = runTest {
        val rawToken = "raw-token"
        val candidateKey = "cand-123"
        val invitation = createInvitation(
            target = InvitationTarget.EXISTING_WORKSPACE,
            workspaceId = "ws-1",
        )
        val context = InvitationRegistrationContext(
            invitationId = invitation.id.value.toString(),
            target = InvitationRegistrationTarget.NEW_WORKSPACE,
            workspaceId = null,
            source = InvitationRegistrationSource.DIRECT,
        )

        coEvery { tokenHasher.candidateKey(rawToken) } returns candidateKey
        coEvery { invitationRepository.findByCandidateKeyForUpdate(candidateKey) } returns invitation
        coEvery { tokenHasher.matches(rawToken, invitation.tokenHash) } returns true

        val exception = shouldThrow<InvitationNotAcceptableException> {
            coordinator.complete(context, rawToken, "principal-123", "User")
        }

        exception.failureCode shouldBe InvitationAcceptanceFailureCode.INVALID
    }

    @Test
    fun `should reject completion when context source does not match`() = runTest {
        val rawToken = "raw-token"
        val candidateKey = "cand-123"
        val invitation = createInvitation(
            target = InvitationTarget.EXISTING_WORKSPACE,
            workspaceId = "ws-1",
        )
        val context = InvitationRegistrationContext(
            invitationId = invitation.id.value.toString(),
            target = InvitationRegistrationTarget.EXISTING_WORKSPACE,
            workspaceId = "ws-1",
            source = InvitationRegistrationSource.WAITLIST,
        )

        coEvery { tokenHasher.candidateKey(rawToken) } returns candidateKey
        coEvery { invitationRepository.findByCandidateKeyForUpdate(candidateKey) } returns invitation
        coEvery { tokenHasher.matches(rawToken, invitation.tokenHash) } returns true

        val exception = shouldThrow<InvitationNotAcceptableException> {
            coordinator.complete(context, rawToken, "principal-123", "User")
        }

        exception.failureCode shouldBe InvitationAcceptanceFailureCode.INVALID
    }

    @Test
    fun `should reject completion when context workspace id does not match`() = runTest {
        val rawToken = "raw-token"
        val candidateKey = "cand-123"
        val invitation = createInvitation(
            target = InvitationTarget.EXISTING_WORKSPACE,
            workspaceId = "ws-1",
        )
        val context = InvitationRegistrationContext(
            invitationId = invitation.id.value.toString(),
            target = InvitationRegistrationTarget.EXISTING_WORKSPACE,
            workspaceId = "ws-other",
            source = InvitationRegistrationSource.DIRECT,
        )

        coEvery { tokenHasher.candidateKey(rawToken) } returns candidateKey
        coEvery { invitationRepository.findByCandidateKeyForUpdate(candidateKey) } returns invitation
        coEvery { tokenHasher.matches(rawToken, invitation.tokenHash) } returns true

        val exception = shouldThrow<InvitationNotAcceptableException> {
            coordinator.complete(context, rawToken, "principal-123", "User")
        }

        exception.failureCode shouldBe InvitationAcceptanceFailureCode.INVALID
    }

    @Test
    fun `fails when tokenHasher is not InvitationTokenCandidateKey`() = runTest {
        val plainHasher = mockk<TokenHasher>()
        val coord = InvitationActivationCoordinator(
            invitationRepository = invitationRepository,
            tokenHasher = plainHasher,
            principalIdentityLookup = principalIdentityLookup,
            workspaceProvisioningService = workspaceProvisioningService,
            waitlistEntryAdmin = waitlistEntryAdmin,
            membershipProvisioner = membershipProvisioner,
            clock = clock,
        )

        assertThrows<InvitationNotAcceptableException> {
            coord.activateForRegistration("token", "user@example.com", "p-1")
        }
    }

    @Test
    fun `prepares a valid invitation without taking a row lock`() = runTest {
        val invitation = createInvitation(
            target = InvitationTarget.EXISTING_WORKSPACE,
            workspaceId = "ws-existing",
        )
        coEvery { tokenHasher.candidateKey("token") } returns "key"
        coEvery { invitationRepository.findByCandidateKey("key") } returns invitation
        coEvery { tokenHasher.matches("token", invitation.tokenHash) } returns true

        val context = coordinator.prepare("token", " User@Example.com ")

        assertEquals(invitation.id.value.toString(), context.invitationId)
        assertEquals(InvitationRegistrationTarget.EXISTING_WORKSPACE, context.target)
        assertEquals("ws-existing", context.workspaceId)
        coVerify(exactly = 0) { invitationRepository.findByCandidateKeyForUpdate(any()) }
    }

    @Test
    fun `fails when candidateKey candidate lookup returns null`() = runTest {
        val coord = coordinator
        coEvery { tokenHasher.candidateKey("invalid") } returns ""
        coEvery { invitationRepository.findByCandidateKeyForUpdate("") } returns null

        assertThrows<InvitationNotAcceptableException> {
            coord.activateForRegistration("invalid", "user@example.com", "p-1")
        }
    }

    @Test
    fun `fails when invitation is not found`() = runTest {
        coEvery { tokenHasher.candidateKey("token") } returns "key"
        coEvery { invitationRepository.findByCandidateKeyForUpdate("key") } returns null

        assertThrows<InvitationNotAcceptableException> {
            coordinator.activateForRegistration("token", "user@example.com", "p-1")
        }
    }

    @Test
    fun `fails when rawToken does not match tokenHash`() = runTest {
        val invitation = createInvitation()
        coEvery { tokenHasher.candidateKey("token") } returns "key"
        coEvery { invitationRepository.findByCandidateKeyForUpdate("key") } returns invitation
        coEvery { tokenHasher.matches("token", invitation.tokenHash) } returns false

        assertThrows<InvitationNotAcceptableException> {
            coordinator.activateForRegistration("token", "user@example.com", "p-1")
        }
    }

    @Test
    fun `fails when invitation status is not ACTIVE`() = runTest {
        val invitation = createInvitation(status = InvitationStatus.EXPIRED)
        coEvery { tokenHasher.candidateKey("token") } returns "key"
        coEvery { invitationRepository.findByCandidateKeyForUpdate("key") } returns invitation
        coEvery { tokenHasher.matches("token", invitation.tokenHash) } returns true

        assertThrows<InvitationNotAcceptableException> {
            coordinator.activateForRegistration("token", "user@example.com", "p-1")
        }
    }

    @Test
    fun `fails when principal identity is not found`() = runTest {
        val invitation = createInvitation()
        coEvery { tokenHasher.candidateKey("token") } returns "key"
        coEvery { invitationRepository.findByCandidateKeyForUpdate("key") } returns invitation
        coEvery { tokenHasher.matches("token", invitation.tokenHash) } returns true
        coEvery { principalIdentityLookup.findByPrincipalId("p-1") } returns null

        assertThrows<InvitationNotAcceptableException> {
            coordinator.activateForRegistration("token", "user@example.com", "p-1")
        }
    }

    @Test
    fun `fails when email does not match principal email`() = runTest {
        val invitation = createInvitation()
        val principalFacts = PrincipalIdentityFacts(
            principalId = "p-1",
            principalType = PrincipalType.USER,
            subject = "sub-1",
            provider = "local",
            displayIdentity = "User",
            email = "other@example.com",
            username = "user",
            emailStatus = EmailStatus.VERIFIED,
        )
        coEvery { tokenHasher.candidateKey("token") } returns "key"
        coEvery { invitationRepository.findByCandidateKeyForUpdate("key") } returns invitation
        coEvery { tokenHasher.matches("token", invitation.tokenHash) } returns true
        coEvery { principalIdentityLookup.findByPrincipalId("p-1") } returns principalFacts

        assertThrows<InvitationNotAcceptableException> {
            coordinator.activateForRegistration("token", "user@example.com", "p-1")
        }
    }

    @Test
    fun `fails when principalType is not USER`() = runTest {
        val invitation = createInvitation()
        val principalFacts = PrincipalIdentityFacts(
            principalId = "p-1",
            principalType = PrincipalType.SERVICE_ACCOUNT,
            subject = "sub-1",
            provider = "local",
            displayIdentity = "User",
            email = "user@example.com",
            username = "user",
            emailStatus = EmailStatus.VERIFIED,
        )
        coEvery { tokenHasher.candidateKey("token") } returns "key"
        coEvery { invitationRepository.findByCandidateKeyForUpdate("key") } returns invitation
        coEvery { tokenHasher.matches("token", invitation.tokenHash) } returns true
        coEvery { principalIdentityLookup.findByPrincipalId("p-1") } returns principalFacts

        assertThrows<InvitationNotAcceptableException> {
            coordinator.activateForRegistration("token", "user@example.com", "p-1")
        }
    }

    @Test
    fun `fails when invitation is expired`() = runTest {
        val expiredInvitation = createInvitation(
            status = InvitationStatus.ACTIVE,
            expiresAt = now.minusSeconds(10),
        )
        val principalFacts = PrincipalIdentityFacts(
            principalId = "p-1",
            principalType = PrincipalType.USER,
            subject = "sub-1",
            provider = "local",
            displayIdentity = "User",
            email = "user@example.com",
            username = "user",
            emailStatus = EmailStatus.VERIFIED,
        )
        coEvery { tokenHasher.candidateKey("token") } returns "key"
        coEvery { invitationRepository.findByCandidateKeyForUpdate("key") } returns expiredInvitation
        coEvery { tokenHasher.matches("token", expiredInvitation.tokenHash) } returns true
        coEvery { principalIdentityLookup.findByPrincipalId("p-1") } returns principalFacts

        assertThrows<InvitationNotAcceptableException> {
            coordinator.activateForRegistration("token", "user@example.com", "p-1")
        }
    }

    @Test
    fun `throws OptimisticLockException when updateIfVersionMatches returns false`() = runTest {
        val rawToken = "secret-token"
        val candidateKey = "cand-123"
        val email = "user@example.com"
        val principalId = "principal-123"

        val invitation = createInvitation(
            target = InvitationTarget.EXISTING_WORKSPACE,
            workspaceId = "ws-existing",
            status = InvitationStatus.ACTIVE,
        )

        val principalFacts = PrincipalIdentityFacts(
            principalId = principalId,
            principalType = PrincipalType.USER,
            subject = "sub-1",
            provider = "local",
            displayIdentity = "User",
            email = "user@example.com",
            username = "user",
            emailStatus = EmailStatus.VERIFIED,
        )

        coEvery { tokenHasher.candidateKey(rawToken) } returns candidateKey
        coEvery { invitationRepository.findByCandidateKeyForUpdate(candidateKey) } returns invitation
        coEvery { tokenHasher.matches(rawToken, invitation.tokenHash) } returns true
        coEvery { principalIdentityLookup.findByPrincipalId(principalId) } returns principalFacts
        coEvery { invitationRepository.updateIfVersionMatches(any()) } returns false

        assertThrows<OptimisticLockException> {
            coordinator.activateForRegistration(rawToken, email, principalId)
        }
    }

    private fun createInvitation(
        target: InvitationTarget = InvitationTarget.NEW_WORKSPACE,
        workspaceId: String? = null,
        status: InvitationStatus = InvitationStatus.ACTIVE,
        createdAt: Instant = now.minusSeconds(3600),
        expiresAt: Instant = now.plusSeconds(3600),
    ): Invitation = Invitation(
        id = InvitationId(UUID.randomUUID()),
        source = InvitationSource.DIRECT,
        sourceReferenceId = null,
        target = target,
        workspaceId = workspaceId,
        invitedEmailNormalized = "user@example.com",
        tokenHash = "hash-123",
        status = status,
        issuedBy = "issuer-1",
        createdAt = createdAt,
        expiresAt = expiresAt,
        version = 0,
    )

    interface CandidateKeyTokenHasher :
        TokenHasher,
        InvitationTokenCandidateKey
}
