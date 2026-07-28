package com.profiletailors.smp.identity.infrastructure.email

import com.profiletailors.smp.identity.application.EmailMessage
import com.profiletailors.smp.identity.application.EmailSendResult
import com.profiletailors.smp.identity.application.EmailSender
import com.profiletailors.smp.identity.domain.PasswordResetRequested
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SendPasswordResetEmailConsumerTest {

    @Test
    fun `dispatches a password reset email with the expected recipient and subject`() = runTest {
        val sender = RecordingEmailSender()
        val properties = EmailProperties(publicAppUrl = "https://app.example.com")
        val consumer = SendPasswordResetEmailConsumer(sender, properties)

        consumer.consume(
            PasswordResetRequested(
                principalId = "user-1",
                email = "user@example.com",
                rawResetToken = "raw-token",
            ),
        )

        assertThat(sender.messages).hasSize(1)
        val message = sender.messages.single()
        assertThat(message.to).isEqualTo("user@example.com")
        assertThat(message.subject).isEqualTo("Reset your password")
        assertThat(message.content.text).contains("https://app.example.com/reset-password?token=raw-token")
    }

    @Test
    fun `records a failure when the email sender returns an error`() = runTest {
        val sender = FailingEmailSender()
        val properties = EmailProperties(publicAppUrl = "https://app.example.com")
        val consumer = SendPasswordResetEmailConsumer(sender, properties)

        // The consumer must NOT rethrow — failed deliveries are absorbed.
        consumer.consume(
            PasswordResetRequested(
                principalId = "user-1",
                email = "user@example.com",
                rawResetToken = "raw-token",
            ),
        )

        assertThat(sender.attempts).isEqualTo(1)
    }

    private class RecordingEmailSender : EmailSender {
        data class Message(val to: String, val subject: String, val content: EmailMessage)
        val messages = mutableListOf<Message>()

        override suspend fun send(to: String, subject: String, message: EmailMessage): EmailSendResult {
            messages.add(Message(to, subject, message))
            return EmailSendResult(success = true)
        }
    }

    private class FailingEmailSender : EmailSender {
        var attempts: Int = 0

        override suspend fun send(to: String, subject: String, message: EmailMessage): EmailSendResult {
            attempts += 1
            return EmailSendResult(success = false, error = "smtp down")
        }
    }
}
