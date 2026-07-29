package com.profiletailors.smp.identity.infrastructure.email

import com.profiletailors.smp.identity.application.EmailFailureCategory
import com.profiletailors.smp.identity.application.EmailMessage
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.test.runTest
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

        result.success shouldBe true
        mailSender.simpleMessage.shouldBeNull()
        val mime = mailSender.mimeMessage.shouldNotBeNull()
        mime.subject shouldBe "[Profile Tailors] Verify your email"
        mime.getRecipients(Message.RecipientType.TO).single().toString() shouldBe "user@example.com"
        val rawMessage = ByteArrayOutputStream().also(mime::writeTo).toString(Charsets.UTF_8)
        rawMessage shouldContain "Plain fallback"
        rawMessage shouldContain "<strong>Verify</strong>"
    }

    @Test
    fun `uses SimpleMailMessage for text-only content`() = runTest {
        val mailSender = RecordingJavaMailSender()
        val sender = SmtpEmailSender(mailSender, properties)

        val result = sender.send("user@example.com", "Hello", EmailMessage("Plain only"))

        result.success shouldBe true
        mailSender.mimeMessage.shouldBeNull()
        mailSender.simpleMessage.shouldNotBeNull().text shouldBe "Plain only"
        mailSender.simpleMessage.shouldNotBeNull().subject shouldBe "[Profile Tailors] Hello"
    }

    @Test
    fun `authentication failure is permanent and is not retryable`() = runTest {
        val sender = SmtpEmailSender(
            RecordingJavaMailSender(failure = MailAuthenticationException("invalid credentials")),
            properties,
        )

        val result = sender.send("user@example.com", "Hello", EmailMessage("Plain only"))

        result.failureCategory shouldBe EmailFailureCategory.PROVIDER_REJECTED
        result.retryable shouldBe false
    }

    @Test
    fun `transient connection failure is temporary and retryable`() = runTest {
        val sender = SmtpEmailSender(
            RecordingJavaMailSender(
                failure = MailSendException("connection refused: smtp.example.com"),
            ),
            properties,
        )

        val result = sender.send("user@example.com", "Hello", EmailMessage("Plain only"))

        result.failureCategory shouldBe EmailFailureCategory.PROVIDER_UNAVAILABLE
        result.retryable shouldBe true
    }

    @Test
    fun `recipient rejection is permanent and not retryable`() = runTest {
        val sender = SmtpEmailSender(
            RecordingJavaMailSender(
                failure = MailSendException("recipient rejected: mailbox unavailable"),
            ),
            properties,
        )

        val result = sender.send("user@example.com", "Hello", EmailMessage("Plain only"))

        result.failureCategory shouldBe EmailFailureCategory.PROVIDER_REJECTED
        result.retryable shouldBe false
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
