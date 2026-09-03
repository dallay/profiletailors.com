package com.profiletailors.notifications.domain

import com.profiletailors.notifications.application.ports.InvitationDeliverySummary
import com.profiletailors.notifications.domain.event.InvitationDeliveryKind
import com.profiletailors.notifications.domain.event.InvitationNotificationRequested
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class InvitationNotificationContractsTest {

    private val invitationId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

    @Test
    fun `requested event exposes the approved token-free shape`() {
        val event = InvitationNotificationRequested(
            invitationId = invitationId,
            commandId = "command-1",
            kind = InvitationDeliveryKind.INITIAL,
            recipient = "invitee@example.com",
            workspaceName = "Profile Tailors",
            locale = "en",
        )

        assertEquals(invitationId, event.invitationId)
        assertEquals("command-1", event.commandId)
        assertEquals(InvitationDeliveryKind.INITIAL, event.kind)
        assertEquals("invitee@example.com", event.recipient)
        assertEquals("Profile Tailors", event.workspaceName)
        assertEquals("en", event.locale)
    }

    @Test
    fun `invitation delivery kind contains only initial and resend`() {
        val kinds = InvitationDeliveryKind.entries.toSet()

        assertTrue(
            kinds.containsAll(
                setOf(InvitationDeliveryKind.INITIAL, InvitationDeliveryKind.RESEND),
            ),
        )
        assertEquals(2, kinds.size)
    }

    @Test
    fun `requested event carries correlation and safe delivery context without token material`() {
        val fieldNames = InvitationNotificationRequested::class.java.declaredFields
            .map { it.name }
            .toSet()

        assertTrue(
            fieldNames.containsAll(
                setOf("invitationId", "commandId", "kind", "recipient", "workspaceName", "locale"),
            ),
        )
        assertFalse(fieldNames.contains("rawToken"))
        assertFalse(fieldNames.contains("tokenHash"))
        assertFalse(fieldNames.contains("acceptUrl"))
    }

    @Test
    fun `requested event rejects a blank command id`() {
        val thrown = assertFailsWith<IllegalArgumentException> {
            InvitationNotificationRequested(
                invitationId = invitationId,
                commandId = " ",
                kind = InvitationDeliveryKind.INITIAL,
                recipient = "invitee@example.com",
                workspaceName = "Profile Tailors",
                locale = "en",
            )
        }

        assertTrue(thrown.message!!.contains("commandId"))
    }

    @Test
    fun `delivery summary exposes operational latest values without payload`() {
        val fieldNames = InvitationDeliverySummary::class.java.declaredFields
            .map { it.name }
            .toSet()

        assertTrue(
            fieldNames.containsAll(
                setOf(
                    "count",
                    "latestStatus",
                    "latestCreatedAt",
                    "latestSentAt",
                    "latestFailedAt",
                ),
            ),
        )
        assertFalse(fieldNames.contains("payload"))
        assertFalse(fieldNames.contains("recipient"))
        assertFalse(fieldNames.contains("rawToken"))
    }

    @Test
    fun `empty delivery summary represents a lost or not yet recorded handoff`() {
        val summary = InvitationDeliverySummary.EMPTY

        assertEquals(0, summary.count)
        assertEquals(null, summary.latestStatus)
        assertEquals(null, summary.latestCreatedAt)
        assertEquals(null, summary.latestSentAt)
        assertEquals(null, summary.latestFailedAt)
    }
}
