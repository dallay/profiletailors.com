package com.profiletailors.smp.identity.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class InvitationRegistrationContextTest {

    @Test
    fun `should create context when invitation targets a new workspace`() {
        val context = InvitationRegistrationContext(
            invitationId = "inv-1",
            target = InvitationRegistrationTarget.NEW_WORKSPACE,
            workspaceId = null,
            source = InvitationRegistrationSource.DIRECT,
        )

        context.invitationId shouldBe "inv-1"
        context.target shouldBe InvitationRegistrationTarget.NEW_WORKSPACE
        context.workspaceId shouldBe null
        context.source shouldBe InvitationRegistrationSource.DIRECT
    }

    @Test
    fun `should create context when invitation targets an existing workspace`() {
        val context = InvitationRegistrationContext(
            invitationId = "inv-1",
            target = InvitationRegistrationTarget.EXISTING_WORKSPACE,
            workspaceId = "ws-1",
            source = InvitationRegistrationSource.WAITLIST,
        )

        context.invitationId shouldBe "inv-1"
        context.target shouldBe InvitationRegistrationTarget.EXISTING_WORKSPACE
        context.workspaceId shouldBe "ws-1"
        context.source shouldBe InvitationRegistrationSource.WAITLIST
    }

    @Test
    fun `should reject context when invitation id is blank`() {
        val exception = shouldThrow<IllegalArgumentException> {
            InvitationRegistrationContext(
                invitationId = "   ",
                target = InvitationRegistrationTarget.NEW_WORKSPACE,
                workspaceId = null,
                source = InvitationRegistrationSource.DIRECT,
            )
        }

        exception.message shouldBe "Invitation id must not be blank"
    }

    @Test
    fun `should reject context when existing workspace id is null`() {
        val exception = shouldThrow<IllegalArgumentException> {
            InvitationRegistrationContext(
                invitationId = "inv-1",
                target = InvitationRegistrationTarget.EXISTING_WORKSPACE,
                workspaceId = null,
                source = InvitationRegistrationSource.DIRECT,
            )
        }

        exception.message shouldBe "Existing workspace invitations require workspaceId"
    }

    @Test
    fun `should reject context when existing workspace id is blank`() {
        val exception = shouldThrow<IllegalArgumentException> {
            InvitationRegistrationContext(
                invitationId = "inv-1",
                target = InvitationRegistrationTarget.EXISTING_WORKSPACE,
                workspaceId = "   ",
                source = InvitationRegistrationSource.DIRECT,
            )
        }

        exception.message shouldBe "Existing workspace invitations require workspaceId"
    }

    @Test
    fun `should reject context when new workspace already has an id`() {
        val exception = shouldThrow<IllegalArgumentException> {
            InvitationRegistrationContext(
                invitationId = "inv-1",
                target = InvitationRegistrationTarget.NEW_WORKSPACE,
                workspaceId = "ws-1",
                source = InvitationRegistrationSource.DIRECT,
            )
        }

        exception.message shouldBe "New workspace invitations must not carry a workspaceId before completion"
    }

    @Test
    fun `should create result when workspace and membership status are resolved`() {
        val result = InvitationRegistrationResult(
            workspaceId = "ws-1",
            membershipStatus = "ACTIVE",
        )

        result.workspaceId shouldBe "ws-1"
        result.membershipStatus shouldBe "ACTIVE"
        result.postCommitEvent shouldBe null
    }

    @Test
    fun `should reject result when workspace id is blank`() {
        val exception = shouldThrow<IllegalArgumentException> {
            InvitationRegistrationResult(
                workspaceId = "  ",
                membershipStatus = "ACTIVE",
            )
        }

        exception.message shouldBe "Invitation registration requires a resolved workspaceId"
    }

    @Test
    fun `should reject result when membership status is blank`() {
        val exception = shouldThrow<IllegalArgumentException> {
            InvitationRegistrationResult(
                workspaceId = "ws-1",
                membershipStatus = "",
            )
        }

        exception.message shouldBe "Invitation registration requires a membership status"
    }
}
