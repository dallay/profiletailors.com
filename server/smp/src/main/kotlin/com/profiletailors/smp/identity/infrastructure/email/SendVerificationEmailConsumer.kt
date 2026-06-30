package com.profiletailors.smp.identity.infrastructure.email

import com.profiletailors.common.domain.bus.event.EventConsumer
import com.profiletailors.common.domain.bus.event.Subscribe
import com.profiletailors.smp.identity.application.EmailSender
import com.profiletailors.smp.identity.domain.UserRegistered
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Consumes [UserRegistered] domain events and dispatches verification emails.
 *
 * Registered automatically via [Subscribe] annotation. The event bus's [EventConfiguration]
 * scans and wires all `@Subscribe`-annotated [EventConsumer] beans.
 */
@Component
@Subscribe(filterBy = UserRegistered::class)
class SendVerificationEmailConsumer(
    private val emailSender: EmailSender,
    private val emailProperties: EmailProperties,
) : EventConsumer<UserRegistered> {

    private val log = LoggerFactory.getLogger(SendVerificationEmailConsumer::class.java)

    override suspend fun consume(event: UserRegistered) {
        val body = EmailTemplates.verificationEmail(
            username = event.username,
            token = event.rawVerificationToken,
            publicAppUrl = emailProperties.publicAppUrl,
        )
        val subject = "Verify your email address"
        val result = emailSender.send(
            to = event.email,
            subject = subject,
            body = body,
        )
        if (!result.success) {
            log.error(
                "Failed to send verification email to '${event.email}' " +
                    "for principal '${event.principalId}': ${result.error}",
            )
        } else {
            log.info("Verification email sent to '${event.email}' for principal '${event.principalId}'")
        }
    }
}
