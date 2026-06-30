package com.profiletailors.smp.identity.infrastructure.email

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension

@ExtendWith(OutputCaptureExtension::class)
internal class EmailTemplatesTest {

    @Test
    fun `should render verification email with username`() {
        val email = EmailTemplates.verificationEmail(
            username = "John",
            token = "abc123",
        )

        assertThat(email.text).contains("Hi John")
        assertThat(email.text).contains("abc123")
        assertThat(email.text).contains("/verify-email?token=abc123")
        assertThat(email.text).contains("This verification link expires in 24 hours.")
        assertThat(email.html).contains("Hi John")
        assertThat(email.html).contains("/verify-email?token=abc123")
        assertThat(email.html).contains("This verification link expires in 24 hours.")
    }

    @Test
    fun `should render verification email without username`() {
        val email = EmailTemplates.verificationEmail(
            username = null,
            token = "token-xyz",
        )

        assertThat(email.text).contains("Hi there")
        assertThat(email.html).contains("Hi there")
        assertThat(email.text).contains("token-xyz")
    }

    @Test
    fun `should include frontend verification link with token`() {
        val email = EmailTemplates.verificationEmail(
            username = "User",
            token = "secret-token",
            publicAppUrl = "https://app-staging.profiletailors.com",
        )

        val verificationUrl = "https://app-staging.profiletailors.com/verify-email?token=secret-token"
        assertThat(email.text).contains(verificationUrl)
        assertThat(email.html).contains(verificationUrl)
    }

    @Test
    fun `should use custom public app URL without hardcoded api host`() {
        val email = EmailTemplates.verificationEmail(
            username = "User",
            token = "tok",
            publicAppUrl = "https://custom.example.com",
        )

        assertThat(email.text).contains("https://custom.example.com/verify-email?token=tok")
        assertThat(email.html).contains("https://custom.example.com/verify-email?token=tok")
        assertThat(email.text).doesNotContain("api/auth/verify-email")
        assertThat(email.html).doesNotContain("app.profiletailors.com/api")
    }

    @Test
    fun `should strip trailing slash from publicAppUrl before building the verification URL`() {
        // Both "with-slash/" and "no-slash" must produce identical verification URLs
        val withSlash = EmailTemplates.verificationEmail(
            username = "User",
            token = "tok",
            publicAppUrl = "https://app.profiletailors.com/",
        )
        val withoutSlash = EmailTemplates.verificationEmail(
            username = "User",
            token = "tok",
            publicAppUrl = "https://app.profiletailors.com",
        )

        assertThat(withSlash.text).contains("https://app.profiletailors.com/verify-email?token=tok")
        assertThat(withoutSlash.html).contains("https://app.profiletailors.com/verify-email?token=tok")
        assertThat(withSlash).isEqualTo(withoutSlash)
    }

    @Test
    fun `should render conservative inline HTML and escape dynamic values`() {
        val email = EmailTemplates.verificationEmail(
            username = "<Admin & Owner>",
            token = "a&b\"c",
            publicAppUrl = "https://example.com/?from=\"email\"&channel=verify",
        )

        assertThat(email.html)
            .contains("style=\"")
            .contains("Space Grotesk")
            .contains("Space Mono")
            .contains("#0a0a0a")
            .contains("Verify Email")
            .contains("STATUS")
            .contains("24 HOURS")
            .contains("&lt;Admin &amp; Owner&gt;")
            .contains("&amp;")
            .contains("&quot;")
            .doesNotContain("<style", "<script", "gradient", "box-shadow", "filter: blur")
    }

    @Test
    fun `should reject missing required template variables and log details`(output: CapturedOutput) {
        assertThatThrownBy {
            EmailTemplates.verificationEmail(username = "User", token = " ")
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Missing required verification email template variables: token")

        assertThat(output.out + output.err)
            .contains("Missing required verification email template variables: token")
    }

    @Test
    fun `should fallback to plain text when html rendering fails and log failure`(output: CapturedOutput) {
        val failingRenderer = object : VerificationEmailHtmlRenderer {
            override fun render(username: String, verificationUrl: String): String = error("template boom")
        }

        val email = EmailTemplates.verificationEmail(
            username = "User",
            token = "tok",
            htmlRenderer = failingRenderer,
        )

        assertThat(email.text).contains("https://app.profiletailors.com/verify-email?token=tok")
        assertThat(email.html).isNull()
        assertThat(output.out + output.err)
            .contains("Verification email HTML template rendering failed")
            .contains("template boom")
    }
}
