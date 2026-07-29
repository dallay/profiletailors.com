package com.profiletailors.smp.identity.infrastructure.email

import com.profiletailors.smp.identity.application.EmailFailureCategory
import com.profiletailors.smp.identity.application.EmailMessage
import com.resend.core.exception.ResendException
import com.resend.services.emails.model.CreateEmailOptions
import com.resend.services.emails.model.CreateEmailResponse
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ResendEmailSenderTest {

    private val emailProperties = EmailProperties(
        sender = "noreply@profiletailors.com",
        verificationSubjectPrefix = "[Profile Tailors]",
    )

    @Test
    fun `send returns success when gateway responds`() = runTest {
        val capture = SentCapture()
        val sender = ResendEmailSender(emailProperties, fakeGateway(capture = capture))

        val result = sender.send(
            to = "user@example.com",
            subject = "Verify your email",
            message = EmailMessage(
                text = "Click the link to verify.",
                html = "<p>Click the link to verify.</p>",
            ),
        )

        result.success shouldBe true
        result.error.shouldBeNull()
        capture.lastOptions.shouldNotBeNull().from shouldBe "noreply@profiletailors.com"
        capture.lastOptions.shouldNotBeNull().to?.firstOrNull() shouldBe "user@example.com"
        capture.lastOptions.shouldNotBeNull().subject shouldBe "[Profile Tailors] Verify your email"
        capture.lastOptions.shouldNotBeNull().text shouldBe "Click the link to verify."
        capture.lastOptions.shouldNotBeNull().html shouldBe "<p>Click the link to verify.</p>"
    }

    @Test
    fun `send returns failure when gateway throws ResendException`() = runTest {
        val sender = ResendEmailSender(
            emailProperties,
            fakeGateway(throws = ResendException("Invalid API key")),
        )

        val result = sender.send(
            to = "user@example.com",
            subject = "Test",
            message = EmailMessage(text = "Body"),
        )

        result.success shouldBe false
        result.error.shouldBeNull()
        result.failureCategory shouldBe EmailFailureCategory.PROVIDER_REJECTED
        result.retryable shouldBe false
    }

    @Test
    fun `send builds subject with verification prefix`() = runTest {
        val capture = SentCapture()
        val sender = ResendEmailSender(emailProperties, fakeGateway(capture = capture))

        sender.send(to = "a@b.com", subject = "Password Reset", message = EmailMessage(text = "Reset link here."))

        capture.lastOptions.shouldNotBeNull().subject shouldBe "[Profile Tailors] Password Reset"
    }

    @Test
    fun `send uses configured sender address as from`() = runTest {
        val customProperties = EmailProperties(
            sender = "custom@profiletailors.com",
            verificationSubjectPrefix = "[PT]",
        )
        val capture = SentCapture()
        val sender = ResendEmailSender(customProperties, fakeGateway(capture = capture))

        sender.send(to = "user@example.com", subject = "Hello", message = EmailMessage(text = "Body"))

        capture.lastOptions.shouldNotBeNull().from shouldBe "custom@profiletailors.com"
        capture.lastOptions.shouldNotBeNull().subject shouldBe "[PT] Hello"
    }

    class SentCapture {
        var lastOptions: CreateEmailOptions? = null
    }

    private fun fakeGateway(
        capture: SentCapture = SentCapture(),
        responseId: String = "email-id-fake",
        throws: ResendException? = null,
    ): ResendEmailGateway = ResendEmailGateway { options ->
        if (throws != null) throw throws
        capture.lastOptions = options
        CreateEmailResponse().apply { id = responseId }
    }
}
