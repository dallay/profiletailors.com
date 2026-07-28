package com.profiletailors.smp.identity.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class PasswordResetRequestedTest {

    @Test
    fun `carries principalId email and rawResetToken verbatim`() {
        val event = PasswordResetRequested(
            principalId = "user-1",
            email = "user@example.com",
            rawResetToken = "raw-token",
        )

        assertEquals("user-1", event.principalId)
        assertEquals("user@example.com", event.email)
        assertEquals("raw-token", event.rawResetToken)
    }

    @Test
    fun `is a DomainEvent with occurredOn timestamp`() {
        val event = PasswordResetRequested(
            principalId = "user-1",
            email = "user@example.com",
            rawResetToken = "raw-token",
        )

        assertNotNull(event.occurredOn())
        assertEquals(1, event.eventVersion())
    }

    @Test
    fun `two events with the same fields are equal`() {
        val first = PasswordResetRequested(
            principalId = "user-1",
            email = "user@example.com",
            rawResetToken = "raw-token",
        )
        val second = PasswordResetRequested(
            principalId = "user-1",
            email = "user@example.com",
            rawResetToken = "raw-token",
        )

        assertEquals(first, second)
    }

    @Test
    fun `two events with different field values are not equal`() {
        val first = PasswordResetRequested(
            principalId = "user-1",
            email = "user@example.com",
            rawResetToken = "raw-token",
        )
        val second = PasswordResetRequested(
            principalId = "user-2",
            email = "user@example.com",
            rawResetToken = "raw-token",
        )

        assertNotEquals(first, second)
    }
}
