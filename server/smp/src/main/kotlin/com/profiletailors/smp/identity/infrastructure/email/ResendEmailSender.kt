package com.profiletailors.smp.identity.infrastructure.email

import com.profiletailors.smp.identity.application.EmailSendResult
import com.profiletailors.smp.identity.application.EmailSender
import com.resend.core.exception.ResendException
import com.resend.services.emails.model.CreateEmailOptions
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

/**
 * Resend-backed implementation of [EmailSender].
 *
 * Takes priority over [SmtpEmailSender] and [MockEmailSender] via [@Primary].
 * Only activated when `app.email.resend.api-key` is set in the environment.
 *
 * Plain text is used for now; React Email / HTML support is future work tracked separately.
 */
@Component
@Primary
@ConditionalOnProperty(name = ["app.email.resend.api-key"])
class ResendEmailSender(
    private val emailProperties: EmailProperties,
    private val emailGateway: ResendEmailGateway,
) : EmailSender {

    private val log = LoggerFactory.getLogger(ResendEmailSender::class.java)

    override suspend fun send(to: String, subject: String, body: String): EmailSendResult {
        val params = CreateEmailOptions.builder()
            .from(emailProperties.sender)
            .to(to)
            .subject("${emailProperties.verificationSubjectPrefix} $subject")
            .text(body)
            .build()

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
