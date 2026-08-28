package com.profiletailors.smp.platformadmin.infrastructure

import com.profiletailors.common.domain.workspace.WorkspaceMembershipSnapshot
import com.profiletailors.smp.platformadmin.application.InvitationAcceptanceRepository
import com.profiletailors.smp.platformadmin.application.contracts.InvitationTokenCandidateKey
import com.profiletailors.smp.platformadmin.application.contracts.TokenHasher
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

class InvitationRegistrationGatewayAdapterTest {
    private val now = Instant.parse("2026-08-09T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val invitationRepository = mockk<InvitationAcceptanceRepository>()
    private val tokenHasher = mockk<TokenHasher>(moreInterfaces = arrayOf(InvitationTokenCandidateKey::class))
    private val membershipProvisioner = mockk<WorkspaceMembershipProvisioner>()
    private val invitation = Invitation(
        id = InvitationId.generate(),
        source = InvitationSource.WAITLIST,
        sourceReferenceId = "waitlist-1",
        workspaceId = "workspace-a",
        invitedEmailNormalized = "invitee@example.com",
        tokenHash = "hashed-token",
        status = InvitationStatus.ACTIVE,
        issuedBy = "issuer-1",
        createdAt = now.minusSeconds(60),
        expiresAt = now.plusSeconds(3600),
    )

    @Test
    fun `accepts matching invitation and returns its workspace`() = runTest {
        every { (tokenHasher as InvitationTokenCandidateKey).candidateKey("raw-token") } returns "candidate-key"
        every { tokenHasher.matches("raw-token", "hashed-token") } returns true
        coEvery { invitationRepository.findByCandidateKeyForUpdate("candidate-key") } returns invitation
        coEvery {
            membershipProvisioner.reconcile("workspace-a", "principal-1")
        } returns mockk<WorkspaceMembershipSnapshot>()
        coEvery { invitationRepository.markAccepted(invitation.id, now, "principal-1") } returns true

        val workspaceId = adapter().acceptForRegistration(
            rawToken = "raw-token",
            email = " Invitee@Example.com ",
            principalId = "principal-1",
        )

        assertEquals("workspace-a", workspaceId)
        coVerifyOrder {
            membershipProvisioner.reconcile("workspace-a", "principal-1")
            invitationRepository.markAccepted(invitation.id, now, "principal-1")
        }
    }

    @Test
    fun `rejects invitation when registration email does not match`() = runTest {
        every { (tokenHasher as InvitationTokenCandidateKey).candidateKey("raw-token") } returns "candidate-key"
        every { tokenHasher.matches("raw-token", "hashed-token") } returns true
        coEvery { invitationRepository.findByCandidateKeyForUpdate("candidate-key") } returns invitation

        assertThrows<InvitationNotAcceptableException> {
            adapter().acceptForRegistration(
                rawToken = "raw-token",
                email = "other@example.com",
                principalId = "principal-1",
            )
        }

        coVerify(exactly = 0) { membershipProvisioner.reconcile(any(), any()) }
        coVerify(exactly = 0) { invitationRepository.markAccepted(any(), any(), any()) }
    }

    private fun adapter() = InvitationRegistrationGatewayAdapter(
        invitationRepository = invitationRepository,
        tokenHasher = tokenHasher,
        membershipProvisioner = membershipProvisioner,
        clock = clock,
    )
}
