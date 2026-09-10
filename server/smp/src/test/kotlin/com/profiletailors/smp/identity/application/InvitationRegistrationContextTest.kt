package com.profiletailors.smp.identity.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InvitationRegistrationContextTest {

    @Test
    fun `creates valid InvitationRegistrationContext for NEW_WORKSPACE`() {
        val ctx = InvitationRegistrationContext(
            invitationId = "inv-1",
            target = InvitationRegistrationTarget.NEW_WORKSPACE,
            workspaceId = null,
            source = InvitationRegistrationSource.DIRECT,
        )
        assertEquals("inv-1", ctx.invitationId)
        assertEquals(InvitationRegistrationTarget.NEW_WORKSPACE, ctx.target)
    }

    @Test
    fun `creates valid InvitationRegistrationContext for EXISTING_WORKSPACE`() {
        val ctx = InvitationRegistrationContext(
            invitationId = "inv-1",
            target = InvitationRegistrationTarget.EXISTING_WORKSPACE,
            workspaceId = "ws-1",
            source = InvitationRegistrationSource.WAITLIST,
        )
        assertEquals("ws-1", ctx.workspaceId)
    }

    @Test
    fun `throws IllegalArgumentException when invitationId is blank`() {
        val ex = assertThrows<IllegalArgumentException> {
            InvitationRegistrationContext(
                invitationId = "   ",
                target = InvitationRegistrationTarget.NEW_WORKSPACE,
                workspaceId = null,
                source = InvitationRegistrationSource.DIRECT,
            )
        }
        assertEquals("Invitation id must not be blank", ex.message)
    }

    @Test
    fun `throws IllegalArgumentException when EXISTING_WORKSPACE has null or blank workspaceId`() {
        val ex1 = assertThrows<IllegalArgumentException> {
            InvitationRegistrationContext(
                invitationId = "inv-1",
                target = InvitationRegistrationTarget.EXISTING_WORKSPACE,
                workspaceId = null,
                source = InvitationRegistrationSource.DIRECT,
            )
        }
        assertEquals("Existing workspace invitations require workspaceId", ex1.message)

        val ex2 = assertThrows<IllegalArgumentException> {
            InvitationRegistrationContext(
                invitationId = "inv-1",
                target = InvitationRegistrationTarget.EXISTING_WORKSPACE,
                workspaceId = "   ",
                source = InvitationRegistrationSource.DIRECT,
            )
        }
        assertEquals("Existing workspace invitations require workspaceId", ex2.message)
    }

    @Test
    fun `throws IllegalArgumentException when NEW_WORKSPACE carries a non-null workspaceId`() {
        val ex = assertThrows<IllegalArgumentException> {
            InvitationRegistrationContext(
                invitationId = "inv-1",
                target = InvitationRegistrationTarget.NEW_WORKSPACE,
                workspaceId = "ws-1",
                source = InvitationRegistrationSource.DIRECT,
            )
        }
        assertEquals("New workspace invitations must not carry a workspaceId before completion", ex.message)
    }

    @Test
    fun `creates valid InvitationRegistrationResult`() {
        val res = InvitationRegistrationResult(
            workspaceId = "ws-1",
            membershipStatus = "ACTIVE",
        )
        assertEquals("ws-1", res.workspaceId)
        assertEquals("ACTIVE", res.membershipStatus)
    }

    @Test
    fun `throws IllegalArgumentException when InvitationRegistrationResult workspaceId or membershipStatus is blank`() {
        val ex1 = assertThrows<IllegalArgumentException> {
            InvitationRegistrationResult(
                workspaceId = "  ",
                membershipStatus = "ACTIVE",
            )
        }
        assertEquals("Invitation registration requires a resolved workspaceId", ex1.message)

        val ex2 = assertThrows<IllegalArgumentException> {
            InvitationRegistrationResult(
                workspaceId = "ws-1",
                membershipStatus = "",
            )
        }
        assertEquals("Invitation registration requires a membership status", ex2.message)
    }
}
