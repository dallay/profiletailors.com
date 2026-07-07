package com.profiletailors.smp.identity.infrastructure.email

import com.profiletailors.smp.identity.application.EmailMessage
import com.profiletailors.smp.identity.application.EmailSendResult
import com.profiletailors.smp.identity.application.EmailSender
import com.resend.core.exception.ResendException
import com.resend.services.emails.model.CreateEmailOptions
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

/**
 * Resend-backed implementation of [EmailSender].
 *
 * Takes priority over [SmtpEmailSender] and [MockEmailSender] via [@Primary].
 * Only activated when `app.email.resend.api-key` is set to a non-blank value.
 * Sends required plain text and optional HTML when the message provides it.
 */
@Component
@Primary
@ConditionalOnExpression("'\${app.email.resend.api-key:}'.trim().length() > 0")
class ResendEmailSender(private val emailProperties: EmailProperties, private val emailGateway: ResendEmailGateway) :
    EmailSender {

    private val log = LoggerFactory.getLogger(ResendEmailSender::class.java)

    override suspend fun send(to: String, subject: String, message: EmailMessage): EmailSendResult {
        val builder = CreateEmailOptions.builder()
            .from(emailProperties.sender)
            .to(to)
            .subject("${emailProperties.verificationSubjectPrefix} $subject")
            .text(message.text)
        message.html?.let(builder::html)
        val params = builder.build()

        return try {
            val response = emailGateway.send(params)
            log.debug("Email sent via Resend — id={}", response.id)
            EmailSendResult(success = true)
        } catch (e: ResendException) {
            log.error("Failed to send email via Resend — to={} error={}", to, e.message)
            EmailSendResult(success = false, error = e.message)
        }
    }
}
