package com.profiletailors.smp.identity.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.identity.registration")
data class RegistrationConfigurationProperties(val enabled: Boolean = false)
