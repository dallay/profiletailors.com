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
        assertThat(email).contains("https://app.profiletailors.com/api/auth/verify-email")
        assertThat(email).contains("24 hours")
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
    fun `should include verification link with token`() {
        val email = EmailTemplates.verificationEmail(
            username = "User",
            token = "secret-token",
        )

        assertThat(email).contains("?token=secret-token")
    }

    @Test
    fun `should use custom verification base URL`() {
        val email = EmailTemplates.verificationEmail(
            username = "User",
            token = "tok",
            verificationBaseUrl = "https://custom.example.com/verify",
        )

        assertThat(email).contains("https://custom.example.com/verify")
        assertThat(email).doesNotContain("app.profiletailors.com")
    }
}
