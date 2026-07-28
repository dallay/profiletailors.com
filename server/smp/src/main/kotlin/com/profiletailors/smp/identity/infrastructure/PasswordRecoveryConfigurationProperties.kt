package com.profiletailors.smp.identity.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.identity.password-recovery")
data class PasswordRecoveryConfigurationProperties(val enabled: Boolean = true)
