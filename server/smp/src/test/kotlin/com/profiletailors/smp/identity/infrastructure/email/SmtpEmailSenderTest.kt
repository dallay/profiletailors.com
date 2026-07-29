package com.profiletailors.smp.identity.infrastructure.email

import com.profiletailors.smp.identity.application.EmailFailureCategory
import com.profiletailors.smp.identity.application.EmailMessage
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mail.MailAuthenticationException
import org.springframework.mail.MailSendException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Properties

class SmtpEmailSenderTest {

    private val properties = EmailProperties(
        sender = "noreply@profiletailors.com",
        verificationSubjectPrefix = "[Profile Tailors]",
    )

    @Test
    fun `sends multipart text and HTML when HTML exists`() = runTest {
        val mailSender = RecordingJavaMailSender()
        val sender = SmtpEmailSender(mailSender, properties)

        val result = sender.send(
            "user@example.com",
            "Verify your email",
            EmailMessage("Plain fallback", "<strong>Verify</strong>"),
        )

        assertThat(result.success).isTrue()
        assertThat(mailSender.simpleMessage).isNull()
        val mime = mailSender.mimeMessage!!
        assertThat(mime.subject).isEqualTo("[Profile Tailors] Verify your email")
        assertThat(mime.getRecipients(Message.RecipientType.TO).single().toString()).isEqualTo("user@example.com")
        val rawMessage = ByteArrayOutputStream().also(mime::writeTo).toString(Charsets.UTF_8)
        assertThat(rawMessage).contains("Plain fallback", "<strong>Verify</strong>")
    }

    @Test
    fun `uses SimpleMailMessage for text-only content`() = runTest {
        val mailSender = RecordingJavaMailSender()
        val sender = SmtpEmailSender(mailSender, properties)

        val result = sender.send("user@example.com", "Hello", EmailMessage("Plain only"))

        assertThat(result.success).isTrue()
        assertThat(mailSender.mimeMessage).isNull()
        assertThat(mailSender.simpleMessage?.text).isEqualTo("Plain only")
        assertThat(mailSender.simpleMessage?.subject).isEqualTo("[Profile Tailors] Hello")
    }

    @Test
    fun `authentication failure is permanent and is not retryable`() = runTest {
        val sender = SmtpEmailSender(
            RecordingJavaMailSender(failure = MailAuthenticationException("invalid credentials")),
            properties,
        )

        val result = sender.send("user@example.com", "Hello", EmailMessage("Plain only"))

        assertThat(result.failureCategory).isEqualTo(EmailFailureCategory.PROVIDER_REJECTED)
        assertThat(result.retryable).isFalse()
    }

    @Test
    fun `send failure is temporary and retryable`() = runTest {
        val sender = SmtpEmailSender(
            RecordingJavaMailSender(failure = MailSendException("connection unavailable")),
            properties,
        )

        val result = sender.send("user@example.com", "Hello", EmailMessage("Plain only"))

        assertThat(result.failureCategory).isEqualTo(EmailFailureCategory.PROVIDER_UNAVAILABLE)
        assertThat(result.retryable).isTrue()
    }

    private class RecordingJavaMailSender(private val failure: RuntimeException? = null) : JavaMailSender {
        var simpleMessage: SimpleMailMessage? = null
        var mimeMessage: MimeMessage? = null

        override fun createMimeMessage(): MimeMessage = MimeMessage(Session.getInstance(Properties()))
        override fun createMimeMessage(contentStream: InputStream): MimeMessage =
            MimeMessage(Session.getInstance(Properties()), contentStream)

        override fun send(mimeMessage: MimeMessage) {
            failure?.let { throw it }
            this.mimeMessage = mimeMessage
        }

        override fun send(vararg mimeMessages: MimeMessage) {
            failure?.let { throw it }
            this.mimeMessage = mimeMessages.lastOrNull()
        }

        override fun send(simpleMessage: SimpleMailMessage) {
            failure?.let { throw it }
            this.simpleMessage = simpleMessage
        }

        override fun send(vararg simpleMessages: SimpleMailMessage) {
            failure?.let { throw it }
            this.simpleMessage = simpleMessages.lastOrNull()
        }
    }
}
