package com.profiletailors.smp.identity.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RegistrationModeTest {

    @Test
    fun `open mode allows registration with or without an invitation`() {
        RegistrationMode.OPEN.evaluate(hasInvitationToken = false) shouldBe RegistrationDecision.ALLOWED
        RegistrationMode.OPEN.evaluate(hasInvitationToken = true) shouldBe RegistrationDecision.ALLOWED
    }

    @Test
    fun `invite only mode requires a valid invitation`() {
        RegistrationMode.INVITE_ONLY.evaluate(hasInvitationToken = false) shouldBe
            RegistrationDecision.INVITATION_REQUIRED
        RegistrationMode.INVITE_ONLY.evaluate(hasInvitationToken = true) shouldBe RegistrationDecision.ALLOWED
    }

    @Test
    fun `closed mode rejects registration regardless of invitation`() {
        RegistrationMode.CLOSED.evaluate(hasInvitationToken = false) shouldBe RegistrationDecision.CLOSED
        RegistrationMode.CLOSED.evaluate(hasInvitationToken = true) shouldBe RegistrationDecision.CLOSED
    }
}
