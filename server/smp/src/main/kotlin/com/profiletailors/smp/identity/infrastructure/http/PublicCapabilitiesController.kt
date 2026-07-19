package com.profiletailors.smp.identity.infrastructure.http

import com.profiletailors.smp.identity.infrastructure.RegistrationConfigurationProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/capabilities")
class PublicCapabilitiesController(private val registrationProperties: RegistrationConfigurationProperties) {

    @GetMapping("/public", version = "1")
    fun publicCapabilities(): PublicCapabilitiesResponse =
        PublicCapabilitiesResponse(registrationEnabled = registrationProperties.enabled)
}

data class PublicCapabilitiesResponse(val registrationEnabled: Boolean)
