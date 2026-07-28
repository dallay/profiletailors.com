package com.profiletailors.smp.identity.infrastructure.email

import com.profiletailors.common.domain.bus.event.EventConsumer
import com.profiletailors.common.domain.bus.event.Subscribe
import com.profiletailors.smp.identity.application.EmailSender
import com.profiletailors.smp.identity.domain.PasswordResetRequested
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
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
    @Qualifier("passwordResetEmailTaskExecutor") private val taskExecutor: TaskExecutor,
) : EventConsumer<PasswordResetRequested> {

    private val log = LoggerFactory.getLogger(SendPasswordResetEmailConsumer::class.java)

    override suspend fun consume(event: PasswordResetRequested) {
        taskExecutor.execute {
            runBlocking { deliver(event) }
        }
    }

    private suspend fun deliver(event: PasswordResetRequested) {
        val message = EmailTemplates.passwordResetEmail(
            username = event.email.substringBefore('@'),
            token = event.rawResetToken,
            publicAppUrl = emailProperties.publicAppUrl,
            locale = event.locale,
        )
        val subject = if (event.locale.lowercase().startsWith("es")) {
            "Restablece tu contraseña"
        } else {
            "Reset your password"
        }
        val result = emailSender.send(
            to = event.email,
            subject = subject,
            message = message,
        )
        if (!result.success) {
            log.error(
                "Password reset email delivery failed for principal '{}' with category 'provider-rejected'",
                event.principalId,
            )
        } else {
            log.info("Password reset email sent to recipient for principal '{}'", event.principalId)
        }
    }
}
