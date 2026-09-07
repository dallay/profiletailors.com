package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.identity.domain.PrincipalIdentityFacts
import com.profiletailors.smp.platformadmin.application.contracts.InvitationRepository
import com.profiletailors.smp.platformadmin.application.contracts.InvitationTokenCandidateKey
import com.profiletailors.smp.platformadmin.application.contracts.TokenHasher
import com.profiletailors.smp.platformadmin.domain.Invitation
import com.profiletailors.smp.platformadmin.domain.InvitationId
import com.profiletailors.smp.platformadmin.domain.InvitationNotAcceptableException
import com.profiletailors.smp.platformadmin.domain.InvitationSource
import com.profiletailors.smp.platformadmin.domain.InvitationStatus
import com.profiletailors.smp.platformadmin.domain.InvitationTarget
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipProvisioner
import com.profiletailors.smp.tenancy.application.WorkspaceProvisioningService
import com.profiletailors.smp.tenancy.domain.WorkspaceMembership
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
    private val transactionRunner = NoOpTransactionRunner()
    private val now = Instant.parse("2026-08-15T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val coordinator = InvitationActivationCoordinator(
        invitationRepository = invitationRepository,
        tokenHasher = tokenHasher,
        principalIdentityLookup = principalIdentityLookup,
        workspaceProvisioningService = workspaceProvisioningService,
        membershipProvisioner = membershipProvisioner,
        transactionRunner = transactionRunner,
        clock = clock,
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
    fun `fails when tokenHasher is not InvitationTokenCandidateKey`() = runTest {
        val plainHasher = mockk<TokenHasher>()
        val coord = InvitationActivationCoordinator(
            invitationRepository = invitationRepository,
            tokenHasher = plainHasher,
            principalIdentityLookup = principalIdentityLookup,
            workspaceProvisioningService = workspaceProvisioningService,
            membershipProvisioner = membershipProvisioner,
            transactionRunner = transactionRunner,
            clock = clock,
        )

        assertThrows<InvitationNotAcceptableException> {
            coord.activateForRegistration("token", "user@example.com", "p-1")
        }
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

    private class NoOpTransactionRunner : AtomicTransactionRunner {
        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
    }
}
