package com.profiletailors.smp.identity.infrastructure.email

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for email sending.
 *
 * @property sender the "From" email address
 * @property verificationSubjectPrefix prefix for verification email subjects
 */
@ConfigurationProperties(prefix = "app.email")
data class EmailProperties(
    val sender: String = "noreply@profiletailors.com",
    val verificationSubjectPrefix: String = "[Profile Tailors]",
    val publicAppUrl: String = "https://app.profiletailors.com",
)
