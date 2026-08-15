package com.profiletailors.smp.platformadmin.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class InvitationTest {
    private val now = Instant.parse("2026-08-09T10:00:00Z")
    private val issuer = "user-issuer"
    private val workspaceId = "workspace-a"

    @Test
    fun `creates an invitation ID from a UUID string`() {
        val value = UUID.randomUUID()

        assertEquals(InvitationId(value), InvitationId.fromString(value.toString()))
    }

    @Test
    fun `direct invitation does not require a waitlist source reference`() {
        val invitation = Invitation(
            id = InvitationId.generate(),
            source = InvitationSource.DIRECT,
            sourceReferenceId = null,
            workspaceId = workspaceId,
            invitedEmailNormalized = "invitee@example.com",
            tokenHash = "hashed-token",
            status = InvitationStatus.ACTIVE,
            issuedBy = issuer,
            createdAt = now,
            expiresAt = now.plusSeconds(3600),
        )

        assertNull(invitation.sourceReferenceId)
        assertEquals(workspaceId, invitation.workspaceId)
        assertTrue(invitation.isActive(now))
    }

    @Test
    fun `invitation exposes its immutable identity and lifecycle fields`() {
        val invitation = activeInvitation()

        assertEquals(InvitationSource.DIRECT, invitation.source)
        assertEquals(workspaceId, invitation.workspaceId)
        assertEquals("invitee@example.com", invitation.invitedEmailNormalized)
        assertEquals("hashed-token", invitation.tokenHash)
        assertEquals(InvitationStatus.ACTIVE, invitation.status)
        assertEquals(issuer, invitation.issuedBy)
        assertEquals(now, invitation.createdAt)
        assertEquals(now.plusSeconds(3600), invitation.expiresAt)
        assertNull(invitation.acceptedAt)
        assertNull(invitation.acceptedPrincipalId)
    }

    @Test
    fun `waitlist invitation rejects a blank source reference`() {
        assertThrows<IllegalArgumentException> {
            Invitation(
                id = InvitationId.generate(),
                source = InvitationSource.WAITLIST,
                sourceReferenceId = " ",
                workspaceId = workspaceId,
                invitedEmailNormalized = "invitee@example.com",
                tokenHash = "hashed-token",
                status = InvitationStatus.ACTIVE,
                issuedBy = issuer,
                createdAt = now,
                expiresAt = now.plusSeconds(3600),
            )
        }
    }

    @Test
    fun `waitlist invitation may carry its waitlist entry source reference`() {
        val invitation = Invitation(
            id = InvitationId.generate(),
            source = InvitationSource.WAITLIST,
            sourceReferenceId = "waitlist-entry-1",
            workspaceId = workspaceId,
            invitedEmailNormalized = "invitee@example.com",
            tokenHash = "hashed-token",
            status = InvitationStatus.ACTIVE,
            issuedBy = issuer,
            createdAt = now,
            expiresAt = now.plusSeconds(3600),
        )

        assertEquals("waitlist-entry-1", invitation.sourceReferenceId)
    }

    @Test
    fun `direct invitation rejects a waitlist source reference`() {
        assertThrows<IllegalArgumentException> {
            Invitation(
                id = InvitationId.generate(),
                source = InvitationSource.DIRECT,
                sourceReferenceId = "waitlist-entry-1",
                workspaceId = workspaceId,
                invitedEmailNormalized = "invitee@example.com",
                tokenHash = "hashed-token",
                status = InvitationStatus.ACTIVE,
                issuedBy = issuer,
                createdAt = now,
                expiresAt = now.plusSeconds(3600),
            )
        }
    }

    @Test
    fun `accepted invitation requires acceptance metadata`() {
        assertThrows<IllegalArgumentException> {
            activeInvitation().copy(status = InvitationStatus.ACCEPTED)
        }
        assertThrows<IllegalArgumentException> {
            activeInvitation().copy(
                status = InvitationStatus.ACCEPTED,
                acceptedAt = now,
            )
        }
        assertThrows<IllegalArgumentException> {
            activeInvitation().copy(
                status = InvitationStatus.ACCEPTED,
                acceptedPrincipalId = "principal-1",
            )
        }
    }

    @Test
    fun `acceptance records principal and changes only semantic invitation state`() {
        val invitation = activeInvitation()

        val accepted = invitation.accept(now.plusSeconds(30), "principal-1")

        assertEquals(InvitationStatus.ACCEPTED, accepted.status)
        assertEquals(now.plusSeconds(30), accepted.acceptedAt)
        assertEquals("principal-1", accepted.acceptedPrincipalId)
        assertEquals(invitation.tokenHash, accepted.tokenHash)
        assertEquals(invitation.expiresAt, accepted.expiresAt)
    }

    @Test
    fun `expired invitation is not active and cannot be accepted`() {
        val invitation = activeInvitation()

        assertFalse(invitation.isActive(invitation.expiresAt))
        assertThrows<InvitationNotAcceptableException> {
            invitation.accept(invitation.expiresAt, "principal-1")
        }
    }

    @Test
    fun `accepted invitation cannot be accepted again`() {
        val accepted = activeInvitation().accept(now.plusSeconds(30), "principal-1")

        assertThrows<InvitationNotAcceptableException> {
            accepted.accept(now.plusSeconds(60), "principal-2")
        }
    }

    @Test
    fun `blank workspace and non-normalized email are rejected`() {
        assertThrows<IllegalArgumentException> {
            activeInvitation().copy(workspaceId = "")
        }
        assertThrows<IllegalArgumentException> {
            activeInvitation().copy(invitedEmailNormalized = "Invitee@example.com")
        }
    }

    private fun activeInvitation() = Invitation(
        id = InvitationId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
        source = InvitationSource.DIRECT,
        sourceReferenceId = null,
        workspaceId = workspaceId,
        invitedEmailNormalized = "invitee@example.com",
        tokenHash = "hashed-token",
        status = InvitationStatus.ACTIVE,
        issuedBy = issuer,
        createdAt = now,
        expiresAt = now.plusSeconds(3600),
    )
}
