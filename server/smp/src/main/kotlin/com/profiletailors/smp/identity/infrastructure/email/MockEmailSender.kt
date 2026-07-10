package com.profiletailors.smp.identity.infrastructure.email

import com.profiletailors.smp.identity.application.EmailMessage
import com.profiletailors.smp.identity.application.EmailSendResult
import com.profiletailors.smp.identity.application.EmailSender
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Mock [EmailSender] implementation that logs emails to the console.
 *
 * Registered as the default EmailSender when no SMTP configuration is present.
 * In production, [SmtpEmailSender] is registered instead (it takes priority
 * because it has explicit activation conditions).
 */
@Component
class MockEmailSender : EmailSender {

    private val log = LoggerFactory.getLogger(MockEmailSender::class.java)

    override suspend fun send(to: String, subject: String, message: EmailMessage): EmailSendResult {
        log.info(
            """
            |=== MOCK EMAIL ===
            |To: $to
            |Subject: $subject
            |Text:
            |${message.text}
            |HTML:
            |${message.html ?: "(none)"}
            |==================
            """.trimMargin(),
        )
        return EmailSendResult(success = true)
    }
}
