package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.context.PrincipalType
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.common.domain.workspace.WorkspaceMembershipStatus
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.domain.EmailStatus
import com.profiletailors.smp.identity.domain.PrincipalIdentityFacts
import com.profiletailors.smp.platformadmin.application.ports.InvitationTokenCandidateKey
import com.profiletailors.smp.platformadmin.application.ports.TokenHasher
import com.profiletailors.smp.platformadmin.domain.Invitation
import com.profiletailors.smp.platformadmin.domain.InvitationId
import com.profiletailors.smp.platformadmin.domain.InvitationNotAcceptableException
import com.profiletailors.smp.platformadmin.domain.InvitationSource
import com.profiletailors.smp.platformadmin.domain.InvitationStatus
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipProvisioner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AcceptInvitationHandlerTest {
    private val now = Instant.parse("2026-08-09T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val invitationRepository = mockk<InvitationAcceptanceRepository>()
    private val tokenHasher = mockk<TokenHasher>(moreInterfaces = arrayOf(InvitationTokenCandidateKey::class))
    private val principalIdentityLookup = mockk<PrincipalIdentityLookup>()
    private val membershipProvisioner = mockk<WorkspaceMembershipProvisioner>()
    private val transactionRunner = object : AtomicTransactionRunner {
        override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
    }

    @Test
    fun `rejects an authenticated identity whose email does not match the invitation`() = runTest {
        val invitation = Invitation(
            id = InvitationId.generate(),
            source = InvitationSource.DIRECT,
            sourceReferenceId = null,
            workspaceId = "workspace-a",
            invitedEmailNormalized = "other@example.com",
            tokenHash = "hashed-token",
            status = InvitationStatus.ACTIVE,
            issuedBy = "issuer-1",
            createdAt = now.minusSeconds(60),
            expiresAt = now.plusSeconds(3600),
        )
        every { (tokenHasher as InvitationTokenCandidateKey).candidateKey("raw-token") } returns "candidate-key"
        coEvery { invitationRepository.findByCandidateKeyForUpdate("candidate-key") } returns invitation
        coEvery { tokenHasher.matches("raw-token", "hashed-token") } returns true
        coEvery { principalIdentityLookup.findByPrincipalId("principal-1") } returns PrincipalIdentityFacts(
            principalId = "principal-1",
            principalType = PrincipalType.USER,
            subject = "local:invitee@example.com",
            provider = null,
            displayIdentity = "invitee",
            email = "invitee@example.com",
            username = "invitee",
            emailStatus = EmailStatus.PENDING,
        )

        assertThrows<InvitationNotAcceptableException> {
            handler().handle(
                AcceptInvitationCommand(
                    rawToken = "raw-token",
                    authenticatedPrincipalId = "principal-1",
                    authenticatedEmail = "invitee@example.com",
                ),
            )
        }

        coVerify(exactly = 0) { membershipProvisioner.reconcile(any(), any()) }
        coVerify(exactly = 0) { invitationRepository.markAccepted(any(), any(), any()) }
    }

    @Test
    fun `rejects an already accepted invitation without changing membership`() = runTest {
        val invitation = Invitation(
            id = InvitationId.generate(),
            source = InvitationSource.DIRECT,
            sourceReferenceId = null,
            workspaceId = "workspace-a",
            invitedEmailNormalized = "invitee@example.com",
            tokenHash = "hashed-token",
            status = InvitationStatus.ACCEPTED,
            issuedBy = "issuer-1",
            createdAt = now.minusSeconds(60),
            expiresAt = now.plusSeconds(3600),
            acceptedAt = now.minusSeconds(30),
            acceptedPrincipalId = "principal-1",
        )
        every { (tokenHasher as InvitationTokenCandidateKey).candidateKey("raw-token") } returns "candidate-key"
        coEvery { invitationRepository.findByCandidateKeyForUpdate("candidate-key") } returns invitation
        coEvery { tokenHasher.matches("raw-token", "hashed-token") } returns true
        coEvery { principalIdentityLookup.findByPrincipalId("principal-1") } returns PrincipalIdentityFacts(
            principalId = "principal-1",
            principalType = PrincipalType.USER,
            subject = "local:invitee@example.com",
            provider = null,
            displayIdentity = "invitee",
            email = "invitee@example.com",
            username = "invitee",
            emailStatus = EmailStatus.PENDING,
        )

        assertThrows<InvitationNotAcceptableException> {
            handler().handle(
                AcceptInvitationCommand(
                    rawToken = "raw-token",
                    authenticatedPrincipalId = "principal-1",
                    authenticatedEmail = "invitee@example.com",
                ),
            )
        }

        coVerify(exactly = 0) { membershipProvisioner.reconcile(any(), any()) }
        coVerify(exactly = 0) { invitationRepository.markAccepted(any(), any(), any()) }
    }

    @Test
    fun `rejects an invitation when the token hasher cannot provide a candidate key`() = runTest {
        val tokenHasherWithoutCandidateKey = mockk<TokenHasher>()
        val handler = AcceptInvitationHandler(
            invitationRepository = invitationRepository,
            tokenHasher = tokenHasherWithoutCandidateKey,
            principalIdentityLookup = principalIdentityLookup,
            membershipProvisioner = membershipProvisioner,
            transactionRunner = transactionRunner,
            clock = clock,
        )

        assertThrows<InvitationNotAcceptableException> {
            handler.handle(
                AcceptInvitationCommand(
                    rawToken = "raw-token",
                    authenticatedPrincipalId = "principal-1",
                    authenticatedEmail = "invitee@example.com",
                ),
            )
        }

        coVerify(exactly = 0) { invitationRepository.findByCandidateKeyForUpdate(any()) }
    }

    @Test
    fun `rejects acceptance when the atomic invitation update reports no row`() = runTest {
        val invitation = Invitation(
            id = InvitationId.generate(),
            source = InvitationSource.DIRECT,
            sourceReferenceId = null,
            workspaceId = "workspace-a",
            invitedEmailNormalized = "invitee@example.com",
            tokenHash = "hashed-token",
            status = InvitationStatus.ACTIVE,
            issuedBy = "issuer-1",
            createdAt = now.minusSeconds(60),
            expiresAt = now.plusSeconds(3600),
        )
        val membership = com.profiletailors.smp.tenancy.domain.WorkspaceMembership(
            workspaceId = "workspace-a",
            principalId = "principal-1",
            principalType = PrincipalType.USER,
            status = WorkspaceMembershipStatus.ACTIVE,
        )
        every { (tokenHasher as InvitationTokenCandidateKey).candidateKey("raw-token") } returns "candidate-key"
        coEvery { invitationRepository.findByCandidateKeyForUpdate("candidate-key") } returns invitation
        coEvery { tokenHasher.matches("raw-token", "hashed-token") } returns true
        coEvery { principalIdentityLookup.findByPrincipalId("principal-1") } returns PrincipalIdentityFacts(
            principalId = "principal-1",
            principalType = PrincipalType.USER,
            subject = "local:invitee@example.com",
            provider = null,
            displayIdentity = "invitee",
            email = "invitee@example.com",
            username = "invitee",
            emailStatus = EmailStatus.PENDING,
        )
        coEvery { membershipProvisioner.reconcile("workspace-a", "principal-1") } returns membership
        coEvery {
            invitationRepository.markAccepted(invitation.id, now, "principal-1")
        } returns false

        assertThrows<InvitationNotAcceptableException> {
            handler().handle(
                AcceptInvitationCommand(
                    rawToken = "raw-token",
                    authenticatedPrincipalId = "principal-1",
                    authenticatedEmail = "invitee@example.com",
                ),
            )
        }
    }

    @Test
    fun `accepts an existing user using invitation workspace and reconciles one membership`() = runTest {
        val invitation = Invitation(
            id = InvitationId.generate(),
            source = InvitationSource.DIRECT,
            sourceReferenceId = null,
            workspaceId = "workspace-a",
            invitedEmailNormalized = "invitee@example.com",
            tokenHash = "hashed-token",
            status = InvitationStatus.ACTIVE,
            issuedBy = "issuer-1",
            createdAt = now.minusSeconds(60),
            expiresAt = now.plusSeconds(3600),
        )
        val membership = com.profiletailors.smp.tenancy.domain.WorkspaceMembership(
            workspaceId = "workspace-a",
            principalId = "principal-1",
            principalType = PrincipalType.USER,
            status = WorkspaceMembershipStatus.ACTIVE,
        )
        every { (tokenHasher as InvitationTokenCandidateKey).candidateKey("raw-token") } returns "candidate-key"
        coEvery { invitationRepository.findByCandidateKeyForUpdate("candidate-key") } returns invitation
        coEvery { tokenHasher.matches("raw-token", "hashed-token") } returns true
        coEvery { principalIdentityLookup.findByPrincipalId("principal-1") } returns PrincipalIdentityFacts(
            principalId = "principal-1",
            principalType = PrincipalType.USER,
            subject = "local:invitee@example.com",
            provider = null,
            displayIdentity = "invitee",
            email = "Invitee@Example.com",
            username = "invitee",
            emailStatus = EmailStatus.PENDING,
        )
        coEvery { membershipProvisioner.reconcile("workspace-a", "principal-1") } returns membership
        coEvery {
            invitationRepository.markAccepted(invitation.id, now, "principal-1")
        } returns true

        val result = handler().handle(
            AcceptInvitationCommand(
                rawToken = "raw-token",
                authenticatedPrincipalId = "principal-1",
                authenticatedEmail = " invitee@example.com ",
            ),
        )

        assertEquals("workspace-a", result.workspaceId)
        assertEquals(WorkspaceMembershipStatus.ACTIVE.name, result.membershipStatus)
        coVerifyOrder {
            membershipProvisioner.reconcile("workspace-a", "principal-1")
            invitationRepository.markAccepted(invitation.id, now, "principal-1")
        }
    }

    private fun handler() = AcceptInvitationHandler(
        invitationRepository = invitationRepository,
        tokenHasher = tokenHasher,
        principalIdentityLookup = principalIdentityLookup,
        membershipProvisioner = membershipProvisioner,
        transactionRunner = transactionRunner,
        clock = clock,
    )
}
