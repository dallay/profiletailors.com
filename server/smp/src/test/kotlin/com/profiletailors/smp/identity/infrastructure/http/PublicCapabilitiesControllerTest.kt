package com.profiletailors.smp.identity.infrastructure.http

import com.profiletailors.smp.identity.infrastructure.RegistrationConfigurationProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PublicCapabilitiesControllerTest {

    @Test
    fun `returns only disabled registration capability`() {
        val response = controller(registrationEnabled = false).publicCapabilities()

        assertEquals(PublicCapabilitiesResponse(false), response)
    }

    @Test
    fun `returns only enabled registration capability`() {
        val response = controller(registrationEnabled = true).publicCapabilities()

        assertEquals(PublicCapabilitiesResponse(true), response)
    }

    private fun controller(registrationEnabled: Boolean) = PublicCapabilitiesController(
        RegistrationConfigurationProperties(enabled = registrationEnabled),
    )
}
