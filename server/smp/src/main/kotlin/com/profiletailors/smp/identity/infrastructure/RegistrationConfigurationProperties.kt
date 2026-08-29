package com.profiletailors.smp.identity.infrastructure

import com.profiletailors.smp.identity.domain.RegistrationMode
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.identity.registration")
data class RegistrationConfigurationProperties(val mode: RegistrationMode = RegistrationMode.CLOSED)
