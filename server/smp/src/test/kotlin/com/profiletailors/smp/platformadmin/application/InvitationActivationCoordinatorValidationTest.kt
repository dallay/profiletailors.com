package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.context.PrincipalType
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
import io.mockk.coEvery
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

class InvitationActivationCoordinatorValidationTest {

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
    fun `throws IllegalStateException when waitlist entry is missing during waitlist conversion`() = runTest {
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
        coEvery {
            workspaceProvisioningService.provisionDefaultWorkspace(principalId, "user@example.com")
        } returns
            WorkspaceProvisioningService.ProvisionedWorkspace(
                "ws-new",
                "ws-new",
                com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus.ACTIVE,
            )
        coEvery { waitlistEntryAdmin.findById("entry-123") } returns null

        val ex = assertThrows<IllegalStateException> {
            coordinator.activateForRegistration(rawToken, "user@example.com", principalId)
        }
        assertEquals("Waitlist entry not found", ex.message)
    }

    @Test
    fun `records telemetry when invitation is expired or replayed`() = runTest {
        val rawToken = "secret-token"
        val candidateKey = "cand-123"
        val expiredInvitation = createInvitation(status = InvitationStatus.EXPIRED)
        val acceptedInvitation = createInvitation(
            target = InvitationTarget.EXISTING_WORKSPACE,
            workspaceId = "ws-existing",
            status = InvitationStatus.ACTIVE,
        ).copy(status = InvitationStatus.ACCEPTED, acceptedAt = now, acceptedPrincipalId = "user-principal-1")

        coEvery { tokenHasher.candidateKey(rawToken) } returns candidateKey
        coEvery { invitationRepository.findByCandidateKeyForUpdate(candidateKey) } returns expiredInvitation
        coEvery { tokenHasher.matches(rawToken, expiredInvitation.tokenHash) } returns true

        val ex1 = assertThrows<InvitationNotAcceptableException> {
            coordinator.activateForRegistration(rawToken, "user@example.com", "principal-1")
        }
        assertEquals(InvitationAcceptanceFailureCode.EXPIRED, ex1.failureCode)
        verify { telemetry.recordInvitationExpired() }

        coEvery { invitationRepository.findByCandidateKeyForUpdate(candidateKey) } returns acceptedInvitation
        coEvery { tokenHasher.matches(rawToken, acceptedInvitation.tokenHash) } returns true

        val ex2 = assertThrows<InvitationNotAcceptableException> {
            coordinator.activateForRegistration(rawToken, "user@example.com", "principal-1")
        }
        assertEquals(InvitationAcceptanceFailureCode.ALREADY_CONSUMED, ex2.failureCode)
        verify { telemetry.recordInvitationReplayRejected() }
    }

    @Test
    fun `prepare fails and records telemetry or throws appropriate failure codes`() = runTest {
        val rawToken = "token"
        val candidateKey = "cand"

        coEvery { tokenHasher.candidateKey(rawToken) } returns candidateKey
        coEvery { invitationRepository.findByCandidateKey(candidateKey) } returns null

        val ex1 = assertThrows<InvitationNotAcceptableException> {
            coordinator.prepare(rawToken, "user@example.com")
        }
        assertEquals(InvitationAcceptanceFailureCode.INVALID, ex1.failureCode)

        val expiredInvitation = createInvitation(status = InvitationStatus.EXPIRED)
        coEvery { invitationRepository.findByCandidateKey(candidateKey) } returns expiredInvitation
        coEvery { tokenHasher.matches(rawToken, expiredInvitation.tokenHash) } returns true

        val ex2 = assertThrows<InvitationNotAcceptableException> {
            coordinator.prepare(rawToken, "user@example.com")
        }
        assertEquals(InvitationAcceptanceFailureCode.EXPIRED, ex2.failureCode)

        val revokedInvitation = createInvitation(status = InvitationStatus.REVOKED)
        coEvery { invitationRepository.findByCandidateKey(candidateKey) } returns revokedInvitation
        coEvery { tokenHasher.matches(rawToken, revokedInvitation.tokenHash) } returns true

        val ex3 = assertThrows<InvitationNotAcceptableException> {
            coordinator.prepare(rawToken, "user@example.com")
        }
        assertEquals(InvitationAcceptanceFailureCode.REVOKED, ex3.failureCode)

        val validInvitation = createInvitation(status = InvitationStatus.ACTIVE)
        coEvery { invitationRepository.findByCandidateKey(candidateKey) } returns validInvitation
        coEvery { tokenHasher.matches(rawToken, validInvitation.tokenHash) } returns true

        val ex4 = assertThrows<InvitationNotAcceptableException> {
            coordinator.prepare(rawToken, "mismatch@example.com")
        }
        assertEquals(InvitationAcceptanceFailureCode.EMAIL_MISMATCH, ex4.failureCode)
    }

    @Test
    fun `complete validates context and fails when context properties mismatch`() = runTest {
        val rawToken = "raw-token"
        val candidateKey = "cand-123"
        val principalId = "principal-123"
        val invitation = createInvitation(
            target = InvitationTarget.EXISTING_WORKSPACE,
            workspaceId = "ws-1",
            status = InvitationStatus.ACTIVE,
        )

        coEvery { tokenHasher.candidateKey(rawToken) } returns candidateKey
        coEvery { invitationRepository.findByCandidateKeyForUpdate(candidateKey) } returns invitation
        coEvery { tokenHasher.matches(rawToken, invitation.tokenHash) } returns true

        val mismatchIdContext = InvitationRegistrationContext(
            invitationId = UUID.randomUUID().toString(),
            target = InvitationRegistrationTarget.EXISTING_WORKSPACE,
            workspaceId = "ws-1",
            source = InvitationRegistrationSource.DIRECT,
        )

        val ex1 = assertThrows<InvitationNotAcceptableException> {
            coordinator.complete(mismatchIdContext, rawToken, principalId, "User")
        }
        assertEquals(InvitationAcceptanceFailureCode.INVALID, ex1.failureCode)

        val mismatchTargetContext = InvitationRegistrationContext(
            invitationId = invitation.id.value.toString(),
            target = InvitationRegistrationTarget.NEW_WORKSPACE,
            workspaceId = null,
            source = InvitationRegistrationSource.DIRECT,
        )

        val ex2 = assertThrows<InvitationNotAcceptableException> {
            coordinator.complete(mismatchTargetContext, rawToken, principalId, "User")
        }
        assertEquals(InvitationAcceptanceFailureCode.INVALID, ex2.failureCode)
    }

    @Test
    fun `fails activation when candidate key lookup returns null, invitation not found, or token mismatches`() =
        runTest {
            coEvery { tokenHasher.candidateKey("invalid") } returns ""
            coEvery { invitationRepository.findByCandidateKeyForUpdate("") } returns null

            assertThrows<InvitationNotAcceptableException> {
                coordinator.activateForRegistration("invalid", "user@example.com", "p-1")
            }

            val invitation = createInvitation()
            coEvery { tokenHasher.candidateKey("token") } returns "key"
            coEvery { invitationRepository.findByCandidateKeyForUpdate("key") } returns invitation
            coEvery { tokenHasher.matches("token", invitation.tokenHash) } returns false

            assertThrows<InvitationNotAcceptableException> {
                coordinator.activateForRegistration("token", "user@example.com", "p-1")
            }
        }

    @Test
    fun `fails activation when principal identity not found, email mismatches, or principalType is not USER`() =
        runTest {
            val invitation = createInvitation()
            coEvery { tokenHasher.candidateKey("token") } returns "key"
            coEvery { invitationRepository.findByCandidateKeyForUpdate("key") } returns invitation
            coEvery { tokenHasher.matches("token", invitation.tokenHash) } returns true

            coEvery { principalIdentityLookup.findByPrincipalId("p-1") } returns null
            assertThrows<InvitationNotAcceptableException> {
                coordinator.activateForRegistration("token", "user@example.com", "p-1")
            }

            val mismatchedEmailFacts = PrincipalIdentityFacts(
                principalId = "p-1",
                principalType = PrincipalType.USER,
                subject = "sub-1",
                provider = "local",
                displayIdentity = "User",
                email = "other@example.com",
                username = "user",
                emailStatus = EmailStatus.VERIFIED,
            )
            coEvery { principalIdentityLookup.findByPrincipalId("p-1") } returns mismatchedEmailFacts
            assertThrows<InvitationNotAcceptableException> {
                coordinator.activateForRegistration("token", "user@example.com", "p-1")
            }

            val serviceAccountFacts = PrincipalIdentityFacts(
                principalId = "p-1",
                principalType = PrincipalType.SERVICE_ACCOUNT,
                subject = "sub-1",
                provider = "local",
                displayIdentity = "User",
                email = "user@example.com",
                username = "user",
                emailStatus = EmailStatus.VERIFIED,
            )
            coEvery { principalIdentityLookup.findByPrincipalId("p-1") } returns serviceAccountFacts
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
