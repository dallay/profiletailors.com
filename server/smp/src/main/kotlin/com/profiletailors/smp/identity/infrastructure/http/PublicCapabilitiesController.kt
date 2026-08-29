package com.profiletailors.smp.identity.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.identity.application.GetPublicCapabilitiesQuery
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/capabilities")
class PublicCapabilitiesController(private val mediator: Mediator) {

    /**
     * Retrieves the capabilities available to unauthenticated users.
     *
     * @return The current public capability settings.
     */
    @GetMapping("/public", version = "1")
    suspend fun publicCapabilities(): PublicCapabilitiesResponse {
        val capabilities = mediator.send(GetPublicCapabilitiesQuery())
        return PublicCapabilitiesResponse(
            registrationEnabled = capabilities.registrationEnabled,
            passwordRecoveryEnabled = capabilities.passwordRecoveryEnabled,
            invitationAcceptanceEnabled = capabilities.invitationAcceptanceEnabled,
        )
    }
}

data class PublicCapabilitiesResponse(
    val registrationEnabled: Boolean,
    val passwordRecoveryEnabled: Boolean,
    val invitationAcceptanceEnabled: Boolean,
)
