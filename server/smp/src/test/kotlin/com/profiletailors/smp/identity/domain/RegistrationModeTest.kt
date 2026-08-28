package com.profiletailors.smp.identity.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RegistrationModeTest {

    @Test
    fun `open mode allows registration with or without an invitation`() {
        assertEquals(RegistrationDecision.ALLOWED, RegistrationMode.OPEN.evaluate(hasValidInvitation = false))
        assertEquals(RegistrationDecision.ALLOWED, RegistrationMode.OPEN.evaluate(hasValidInvitation = true))
    }

    @Test
    fun `invite only mode requires a valid invitation`() {
        assertEquals(
            RegistrationDecision.INVITATION_REQUIRED,
            RegistrationMode.INVITE_ONLY.evaluate(hasValidInvitation = false),
        )
        assertEquals(RegistrationDecision.ALLOWED, RegistrationMode.INVITE_ONLY.evaluate(hasValidInvitation = true))
    }

    @Test
    fun `closed mode rejects registration regardless of invitation`() {
        assertEquals(RegistrationDecision.CLOSED, RegistrationMode.CLOSED.evaluate(hasValidInvitation = false))
        assertEquals(RegistrationDecision.CLOSED, RegistrationMode.CLOSED.evaluate(hasValidInvitation = true))
    }
}
