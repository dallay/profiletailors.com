package com.profiletailors.smp.identity.infrastructure.email

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the Resend transactional email adapter.
 *
 * @property apiKey the Resend API key used to authenticate requests.
 *   When empty, [ResendEmailSender] is not loaded (conditional on this property).
 */
@ConfigurationProperties(prefix = "app.email.resend")
data class ResendProperties(val apiKey: String = "")
