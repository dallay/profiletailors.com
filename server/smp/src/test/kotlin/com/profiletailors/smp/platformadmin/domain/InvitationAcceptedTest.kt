package com.profiletailors.smp.platformadmin.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class InvitationAcceptedTest {

    @Test
    fun `contains only redacted acceptance identity and bounded outcome data`() {
        val occurredAt = Instant.parse("2026-08-15T12:00:00Z")
        val invitationId = UUID.fromString("00000000-0000-0000-0000-000000000567")
        val event = InvitationAccepted(
            invitationId = invitationId,
            principalId = "principal-567",
            workspaceId = "workspace-567",
            target = InvitationTarget.NEW_WORKSPACE,
            occurredAt = occurredAt,
        )

        assertEquals(invitationId, event.invitationId)
        assertEquals("principal-567", event.principalId)
        assertEquals("workspace-567", event.workspaceId)
        assertEquals(InvitationTarget.NEW_WORKSPACE, event.target)
        assertEquals(InvitationAcceptanceOutcome.ACCEPTED, event.outcome)
        assertEquals(occurredAt, event.occurredAt)
        assertEquals(
            LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC),
            event.occurredOn(),
        )
        assertEquals(1, event.eventVersion())
        assertFalse(event.toString().contains("raw-token"))
        assertFalse(event.toString().contains("invitee@example.com"))
        assertTrue(event.toString().contains("principal-567"))
    }

    @Test
    fun `should reject event when principal id is blank`() {
        val invitationId = UUID.randomUUID()

        val exception = shouldThrow<IllegalArgumentException> {
            InvitationAccepted(
                invitationId = invitationId,
                principalId = "   ",
                workspaceId = "ws-1",
                target = InvitationTarget.NEW_WORKSPACE,
            )
        }

        exception.message shouldBe "Accepted principal id must not be blank"
    }

    @Test
    fun `should reject event when workspace id is blank`() {
        val invitationId = UUID.randomUUID()

        val exception = shouldThrow<IllegalArgumentException> {
            InvitationAccepted(
                invitationId = invitationId,
                principalId = "p-1",
                workspaceId = "  ",
                target = InvitationTarget.NEW_WORKSPACE,
            )
        }

        exception.message shouldBe "Accepted workspace id must not be blank"
    }
}
