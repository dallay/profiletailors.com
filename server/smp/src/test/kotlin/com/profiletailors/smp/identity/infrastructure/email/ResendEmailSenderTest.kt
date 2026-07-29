package com.profiletailors.smp.identity.infrastructure.email

import com.profiletailors.smp.identity.application.EmailFailureCategory
import com.profiletailors.smp.identity.application.EmailMessage
import com.resend.core.exception.ResendException
import com.resend.services.emails.model.CreateEmailOptions
import com.resend.services.emails.model.CreateEmailResponse
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResendEmailSenderTest {

    private val emailProperties = EmailProperties(
        sender = "noreply@profiletailors.com",
        verificationSubjectPrefix = "[Profile Tailors]",
    )

    // ── Tests ───────────────────────────────────────────────────────────────

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

        assertTrue(result.success)
        assertNull(result.error)
        assertEquals("noreply@profiletailors.com", capture.lastOptions?.from)
        assertEquals("user@example.com", capture.lastOptions?.to?.firstOrNull())
        assertEquals("[Profile Tailors] Verify your email", capture.lastOptions?.subject)
        assertEquals("Click the link to verify.", capture.lastOptions?.text)
        assertEquals("<p>Click the link to verify.</p>", capture.lastOptions?.html)
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

        assertFalse(result.success)
        assertNull(result.error)
        assertEquals(EmailFailureCategory.PROVIDER_REJECTED, result.failureCategory)
        assertFalse(result.retryable)
    }

    @Test
    fun `send builds subject with verification prefix`() = runTest {
        val capture = SentCapture()
        val sender = ResendEmailSender(emailProperties, fakeGateway(capture = capture))

        sender.send(to = "a@b.com", subject = "Password Reset", message = EmailMessage(text = "Reset link here."))

        assertEquals("[Profile Tailors] Password Reset", capture.lastOptions?.subject)
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

        assertEquals("custom@profiletailors.com", capture.lastOptions?.from)
        assertEquals("[PT] Hello", capture.lastOptions?.subject)
    }

    // ── Fakes ───────────────────────────────────────────────────────────────

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
