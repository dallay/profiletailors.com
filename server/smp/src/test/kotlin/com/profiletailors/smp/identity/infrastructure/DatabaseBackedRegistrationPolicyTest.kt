package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.application.RegistrationModeChange
import com.profiletailors.smp.identity.application.RegistrationModeGateway
import com.profiletailors.smp.identity.domain.RegistrationDecision
import com.profiletailors.smp.identity.domain.RegistrationMode
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DatabaseBackedRegistrationPolicyTest {

    @Test
    fun `evaluates against the mode currently reported by the gateway`() = runTest {
        val policy = DatabaseBackedRegistrationPolicy(FakeRegistrationModeGateway(RegistrationMode.INVITE_ONLY))

        policy.evaluate(hasInvitationToken = false) shouldBe RegistrationDecision.INVITATION_REQUIRED
        policy.evaluate(hasInvitationToken = true) shouldBe RegistrationDecision.ALLOWED
    }

    @Test
    fun `reads through on every call instead of caching the first observed mode`() = runTest {
        val gateway = FakeRegistrationModeGateway(RegistrationMode.CLOSED)
        val policy = DatabaseBackedRegistrationPolicy(gateway)

        policy.evaluate(hasInvitationToken = false) shouldBe RegistrationDecision.CLOSED

        gateway.mode = RegistrationMode.OPEN

        policy.evaluate(hasInvitationToken = false) shouldBe RegistrationDecision.ALLOWED
    }

    private class FakeRegistrationModeGateway(var mode: RegistrationMode) : RegistrationModeGateway {
        override suspend fun currentMode(): RegistrationMode = mode

        override suspend fun changeMode(newMode: RegistrationMode): RegistrationModeChange {
            val previous = mode
            mode = newMode
            return RegistrationModeChange(previousMode = previous, newMode = newMode)
        }
    }
}
