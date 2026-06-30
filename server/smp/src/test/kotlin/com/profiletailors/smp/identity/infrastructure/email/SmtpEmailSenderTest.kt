package com.profiletailors.smp.identity.infrastructure.email

import com.profiletailors.smp.identity.application.EmailMessage
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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

    private class RecordingJavaMailSender : JavaMailSender {
        var simpleMessage: SimpleMailMessage? = null
        var mimeMessage: MimeMessage? = null

        override fun createMimeMessage(): MimeMessage = MimeMessage(Session.getInstance(Properties()))
        override fun createMimeMessage(contentStream: InputStream): MimeMessage =
            MimeMessage(Session.getInstance(Properties()), contentStream)

        override fun send(mimeMessage: MimeMessage) {
            this.mimeMessage = mimeMessage
        }

        override fun send(vararg mimeMessages: MimeMessage) {
            this.mimeMessage = mimeMessages.lastOrNull()
        }

        override fun send(simpleMessage: SimpleMailMessage) {
            this.simpleMessage = simpleMessage
        }

        override fun send(vararg simpleMessages: SimpleMailMessage) {
            this.simpleMessage = simpleMessages.lastOrNull()
        }
    }
}
