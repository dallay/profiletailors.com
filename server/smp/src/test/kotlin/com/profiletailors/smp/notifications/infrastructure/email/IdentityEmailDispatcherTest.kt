package com.profiletailors.smp.notifications.infrastructure.email

import com.profiletailors.notifications.application.ports.EmailDispatchResult
import com.profiletailors.notifications.domain.RenderedEmail
import com.profiletailors.smp.identity.application.EmailMessage
import com.profiletailors.smp.identity.application.EmailSendResult
import com.profiletailors.smp.identity.application.EmailSender
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class IdentityEmailDispatcherTest {

    private val rendered = RenderedEmail(
        subject = "Welcome",
        text = "plain text body",
        html = "<p>html body</p>",
    )

    @Test
    fun `dispatch translates EmailSendResult success into EmailDispatchResult Success`() = runTest {
        val sender = mockk<EmailSender>()
        coEvery { sender.send(any(), any(), any()) } returns EmailSendResult(success = true)

        val dispatcher = IdentityEmailDispatcher(emailSender = sender, subjectPrefix = "[PT] ")

        val result = dispatcher.dispatch("user@example.com", rendered)

        assertIs<EmailDispatchResult.Success>(result)
    }

    @Test
    fun `dispatch prefixes the rendered subject with the configured subject prefix`() = runTest {
        val sender = mockk<EmailSender>()
        val captured = slot<String>()
        coEvery { sender.send(any(), capture(captured), any()) } returns EmailSendResult(success = true)

        val dispatcher = IdentityEmailDispatcher(emailSender = sender, subjectPrefix = "[PT] ")

        dispatcher.dispatch("user@example.com", rendered.copy(subject = "Welcome"))

        assertEquals("[PT] Welcome", captured.captured)
    }

    @Test
    fun `dispatch leaves the subject unchanged when no prefix is configured`() = runTest {
        val sender = mockk<EmailSender>()
        val captured = slot<String>()
        coEvery { sender.send(any(), capture(captured), any()) } returns EmailSendResult(success = true)

        val dispatcher = IdentityEmailDispatcher(emailSender = sender, subjectPrefix = "")

        dispatcher.dispatch("user@example.com", rendered.copy(subject = "NoPrefix"))

        assertEquals("NoPrefix", captured.captured)
    }

    @Test
    fun `dispatch forwards the recipient address unchanged`() = runTest {
        val sender = mockk<EmailSender>()
        val captured = slot<String>()
        coEvery { sender.send(capture(captured), any(), any()) } returns EmailSendResult(success = true)

        val dispatcher = IdentityEmailDispatcher(emailSender = sender, subjectPrefix = "")

        dispatcher.dispatch("captured@example.com", rendered)

        assertEquals("captured@example.com", captured.captured)
    }

    @Test
    fun `dispatch converts the rendered text and html into an EmailMessage`() = runTest {
        val sender = mockk<EmailSender>()
        val captured = slot<EmailMessage>()
        coEvery { sender.send(any(), any(), capture(captured)) } returns EmailSendResult(success = true)

        val dispatcher = IdentityEmailDispatcher(emailSender = sender, subjectPrefix = "")

        dispatcher.dispatch("user@example.com", rendered)

        val message = captured.captured
        assertEquals("plain text body", message.text)
        assertEquals("<p>html body</p>", message.html)
    }

    @Test
    fun `dispatch forwards null html through unchanged`() = runTest {
        val sender = mockk<EmailSender>()
        val captured = slot<EmailMessage>()
        coEvery { sender.send(any(), any(), capture(captured)) } returns EmailSendResult(success = true)

        val dispatcher = IdentityEmailDispatcher(emailSender = sender, subjectPrefix = "")

        dispatcher.dispatch(
            "user@example.com",
            rendered.copy(html = null),
        )

        assertEquals(null, captured.captured.html)
    }

    @Test
    fun `dispatch translates EmailSendResult failure into Failure carrying the upstream error`() = runTest {
        val sender = mockk<EmailSender>()
        coEvery { sender.send(any(), any(), any()) } returns EmailSendResult(success = false, error = "smtp 5xx")

        val dispatcher = IdentityEmailDispatcher(emailSender = sender, subjectPrefix = "")

        val result = dispatcher.dispatch("user@example.com", rendered)

        assertIs<EmailDispatchResult.Failure>(result)
        assertEquals("smtp 5xx", result.error)
    }

    @Test
    fun `dispatch failure without an upstream error message uses a default placeholder`() = runTest {
        val sender = mockk<EmailSender>()
        coEvery { sender.send(any(), any(), any()) } returns EmailSendResult(success = false, error = null)

        val dispatcher = IdentityEmailDispatcher(emailSender = sender, subjectPrefix = "")

        val result = dispatcher.dispatch("user@example.com", rendered)

        assertIs<EmailDispatchResult.Failure>(result)
        assertEquals("Email send failed without error message", result.error)
    }

    @Test
    fun `dispatch only calls the underlying sender once per call`() = runTest {
        val sender = mockk<EmailSender>()
        coEvery { sender.send(any(), any(), any()) } returns EmailSendResult(success = true)

        val dispatcher = IdentityEmailDispatcher(emailSender = sender, subjectPrefix = "")

        dispatcher.dispatch("user@example.com", rendered)

        coVerify(exactly = 1) { sender.send("user@example.com", "Welcome", any()) }
    }
}
