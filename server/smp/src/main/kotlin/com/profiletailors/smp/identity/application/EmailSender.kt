package com.profiletailors.smp.identity.application

/**
 * Port for sending email messages.
 *
 * Implementations live in the infrastructure layer:
 * - [SmtpEmailSender] — sends via SMTP (production)
 * - [MockEmailSender] — logs to console (dev/test)
 */
interface EmailSender {
    /** Send an email. Returns [EmailSendResult] indicating success or failure. */
    suspend fun send(
        to: String,
        subject: String,
        body: String,
    ): EmailSendResult
}

data class EmailSendResult(
    val success: Boolean,
    val error: String? = null,
)
