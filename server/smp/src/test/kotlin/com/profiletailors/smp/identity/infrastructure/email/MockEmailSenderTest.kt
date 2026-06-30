package com.profiletailors.smp.identity.infrastructure.email

import com.profiletailors.smp.identity.application.EmailMessage
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension

@ExtendWith(OutputCaptureExtension::class)
class MockEmailSenderTest {

    @Test
    fun `send logs recipient subject text and html without delivery`(output: CapturedOutput) = runTest {
        val sender = MockEmailSender()

        val result = sender.send(
            to = "user@example.com",
            subject = "Verify your email",
            message = EmailMessage(
                text = "Plain fallback body",
                html = "<p>HTML body</p>",
            ),
        )

        assertThat(result.success).isTrue()
        assertThat(output.out + output.err)
            .contains("=== MOCK EMAIL ===")
            .contains("To: user@example.com")
            .contains("Subject: Verify your email")
            .contains("Plain fallback body")
            .contains("<p>HTML body</p>")
    }

    @Test
    fun `send logs (none) when html is null`(output: CapturedOutput) = runTest {
        val sender = MockEmailSender()

        val result = sender.send(
            to = "user@example.com",
            subject = "Verify your email",
            message = EmailMessage(
                text = "Plain fallback body",
                html = null,
            ),
        )

        assertThat(result.success).isTrue()
        assertThat(output.out + output.err)
            .contains("=== MOCK EMAIL ===")
            .contains("To: user@example.com")
            .contains("Plain fallback body")
            .contains("(none)")
    }
}
