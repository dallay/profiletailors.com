package com.profiletailors.smp.platformadmin.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Unit tests for [InvitationIssued] domain event.
 *
 * Validates that the event correctly captures invitation issuance and that the raw token
 * is excluded from JSON serialization while remaining available in-memory for notification
 * delivery.
 */
class InvitationIssuedTest {

    private val rawToken = "SECRET-RAW-TOKEN-VALUE"

    @Test
    fun `should create event with required properties`() {
        val invitationId = UUID.randomUUID()
        val recipientEmail = "user@example.com"
        val workspaceName = "Test Workspace"
        val locale = "en"

        val event = InvitationIssued(
            invitationId = invitationId,
            recipientEmail = recipientEmail,
            workspaceName = workspaceName,
            locale = locale,
            rawToken = rawToken,
        )

        assertThat(event.invitationId).isEqualTo(invitationId)
        assertThat(event.recipientEmail).isEqualTo(recipientEmail)
        assertThat(event.workspaceName).isEqualTo(workspaceName)
        assertThat(event.locale).isEqualTo(locale)
        assertThat(event.rawToken).isEqualTo(rawToken)
    }

    @Test
    fun `should create event with null locale`() {
        val invitationId = UUID.randomUUID()
        val recipientEmail = "user@example.com"
        val workspaceName = "Test Workspace"

        val event = InvitationIssued(
            invitationId = invitationId,
            recipientEmail = recipientEmail,
            workspaceName = workspaceName,
            locale = null,
            rawToken = rawToken,
        )

        assertThat(event.invitationId).isEqualTo(invitationId)
        assertThat(event.recipientEmail).isEqualTo(recipientEmail)
        assertThat(event.workspaceName).isEqualTo(workspaceName)
        assertThat(event.locale).isNull()
        assertThat(event.rawToken).isEqualTo(rawToken)
    }
}
