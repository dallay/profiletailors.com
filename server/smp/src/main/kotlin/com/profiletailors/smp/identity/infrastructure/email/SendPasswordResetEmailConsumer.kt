package com.profiletailors.smp.identity.infrastructure.email

import com.profiletailors.common.domain.bus.event.EventConsumer
import com.profiletailors.common.domain.bus.event.Subscribe
import com.profiletailors.smp.identity.application.EmailSender
import com.profiletailors.smp.identity.domain.PasswordResetRequested
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Consumes [PasswordResetRequested] domain events and dispatches password
 * recovery emails.
 *
 * The raw reset token is rendered into the email URL and is never logged.
 * Diagnostic logs include only the principalId and the recipient email.
 */
@Component
@Subscribe(filterBy = PasswordResetRequested::class)
class SendPasswordResetEmailConsumer(
    private val emailSender: EmailSender,
    private val emailProperties: EmailProperties,
) : EventConsumer<PasswordResetRequested> {

    private val log = LoggerFactory.getLogger(SendPasswordResetEmailConsumer::class.java)

    override suspend fun consume(event: PasswordResetRequested) {
        val message = EmailTemplates.passwordResetEmail(
            username = event.email.substringBefore('@'),
            token = event.rawResetToken,
            publicAppUrl = emailProperties.publicAppUrl,
        )
        val subject = "Reset your password"
        val result = emailSender.send(
            to = event.email,
            subject = subject,
            message = message,
        )
        if (!result.success) {
            log.error(
                "Failed to send password reset email to recipient for principal '{}': {}",
                event.principalId,
                result.error,
            )
        } else {
            log.info("Password reset email sent to recipient for principal '{}'", event.principalId)
        }
    }
}
