package com.profiletailors.smp.identity.application

/**
 * Port for sending email messages.
 *
 * Implementations live in the infrastructure layer, selected by priority:
 * 1. [ResendEmailSender] — Resend API (primary, active when `app.email.resend.api-key` is set)
 * 2. [SmtpEmailSender]  — raw SMTP via Spring Mail (active when `spring.mail.host` is set)
 * 3. [MockEmailSender]  — logs to console (dev/test fallback)
 */
data class EmailMessage(val text: String, val html: String? = null)

fun interface EmailSender {
    /** Send an email. Returns [EmailSendResult] indicating success or failure. */
    /**
     * Sends an email message to the specified recipient.
     *
     * @param to The recipient's email address.
     * @param subject The email subject.
     * @param message The email content.
     * @return The result of the email-sending operation.
     */
    suspend fun send(to: String, subject: String, message: EmailMessage): EmailSendResult
}
