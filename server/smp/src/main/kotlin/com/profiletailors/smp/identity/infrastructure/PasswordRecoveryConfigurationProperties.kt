package com.profiletailors.smp.identity.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

private const val DEFAULT_MINIMUM_RESPONSE_DURATION_MILLIS = 250L

@ConfigurationProperties(prefix = "app.identity.password-recovery")
data class PasswordRecoveryConfigurationProperties(
    val enabled: Boolean = true,
    val minimumResponseDuration: Duration = Duration.ofMillis(DEFAULT_MINIMUM_RESPONSE_DURATION_MILLIS),
)
