package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.common.domain.Service
import com.profiletailors.smp.identity.application.RegistrationAvailability

@Service
internal class PropertyBackedRegistrationAvailability(private val properties: RegistrationConfigurationProperties) :
    RegistrationAvailability {
    override fun isRegistrationEnabled(): Boolean = properties.enabled
}
