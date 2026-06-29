package com.profiletailors.smp.identity.infrastructure.email

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class EmailTemplatesTest {

    @Test
    fun `should render verification email with username`() {
        val email = EmailTemplates.verificationEmail(
            username = "John",
            token = "abc123",
        )

        assertThat(email).contains("Hi John")
        assertThat(email).contains("abc123")
        assertThat(email).contains("/verify-email?token=abc123")
        assertThat(email).contains("This verification link expires in 24 hours.")
    }

    @Test
    fun `should render verification email without username`() {
        val email = EmailTemplates.verificationEmail(
            username = null,
            token = "token-xyz",
        )

        assertThat(email).contains("Hi there")
        assertThat(email).contains("token-xyz")
    }

    @Test
    fun `should include frontend verification link with token`() {
        val email = EmailTemplates.verificationEmail(
            username = "User",
            token = "secret-token",
            publicAppUrl = "https://app-staging.profiletailors.com",
        )

        assertThat(email).contains("https://app-staging.profiletailors.com/verify-email?token=secret-token")
    }

    @Test
    fun `should use custom public app URL without hardcoded api host`() {
        val email = EmailTemplates.verificationEmail(
            username = "User",
            token = "tok",
            publicAppUrl = "https://custom.example.com",
        )

        assertThat(email).contains("https://custom.example.com/verify-email?token=tok")
        assertThat(email).doesNotContain("api/auth/verify-email")
        assertThat(email).doesNotContain("app.profiletailors.com/api")
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

        assertThat(withSlash).contains("https://app.profiletailors.com/verify-email?token=tok")
        assertThat(withoutSlash).contains("https://app.profiletailors.com/verify-email?token=tok")
        // They must be byte-identical so there is no double-slash in the URL
        assertThat(withSlash).isEqualTo(withoutSlash)
    }
}
