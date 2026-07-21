package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.common.domain.Service
import com.profiletailors.smp.identity.application.RegistrationAvailabilityPort

@Service
internal class RegistrationAvailabilityAdapter(private val properties: RegistrationConfigurationProperties) :
    RegistrationAvailabilityPort {
    override fun isRegistrationEnabled(): Boolean = properties.enabled
}
