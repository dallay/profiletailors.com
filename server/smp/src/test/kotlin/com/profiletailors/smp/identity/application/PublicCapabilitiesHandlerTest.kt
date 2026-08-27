package com.profiletailors.smp.identity.application

import com.profiletailors.smp.identity.domain.RegistrationMode
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PublicCapabilitiesHandlerTest {

    @Test
    fun `public registration is enabled only in open mode`() = runTest {
        RegistrationMode.entries.forEach { mode ->
            handler(mode).handle(GetPublicCapabilitiesQuery()).registrationEnabled shouldBe
                (mode == RegistrationMode.OPEN)
        }
    }

    private fun handler(mode: RegistrationMode) = GetPublicCapabilitiesHandler(
        registrationPolicy = RegistrationPolicy { hasValidInvitation -> mode.evaluate(hasValidInvitation) },
        passwordRecoveryEnabled = { true },
    )
}
