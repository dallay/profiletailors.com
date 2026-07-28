package com.profiletailors.smp.identity.infrastructure.email

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EmailTemplatesPasswordResetTest {

    @Test
    fun `renders the reset URL with the configured publicAppUrl and raw token`() {
        val message = EmailTemplates.passwordResetEmail(
            username = "user",
            token = "raw-token",
            publicAppUrl = "https://app.example.com",
        )

        val expectedUrl = "https://app.example.com/reset-password?token=raw-token"
        assertTrue(message.text.contains(expectedUrl), message.text)
        assertNotNull(message.html)
        assertTrue(message.html!!.contains(expectedUrl), message.html)
    }

    @Test
    fun `escapes user-controlled username in the plain-text body`() {
        val message = EmailTemplates.passwordResetEmail(
            username = "<b>user</b>",
            token = "raw-token",
            publicAppUrl = "https://app.example.com",
        )

        // Plain-text body intentionally renders the raw username (it is not parsed as HTML).
        assertTrue(message.text.contains("<b>user</b>"), message.text)
        // The HTML body MUST escape the username to block HTML injection.
        assertTrue(message.html!!.contains("&lt;b&gt;user&lt;/b&gt;"), message.html)
        assertFalse(message.html!!.contains("<b>user</b>"))
    }

    @Test
    fun `states the 30 minute expiry in the body`() {
        val message = EmailTemplates.passwordResetEmail(
            username = "user",
            token = "raw-token",
            publicAppUrl = "https://app.example.com",
        )

        assertTrue(message.text.contains("30 minutes"))
        assertTrue(message.html!!.contains("30 MINUTES"))
    }

    @Test
    fun `states that the request can be ignored if not initiated by the recipient`() {
        val message = EmailTemplates.passwordResetEmail(
            username = "user",
            token = "raw-token",
            publicAppUrl = "https://app.example.com",
        )

        assertTrue(message.text.contains("safely ignore this email"))
        assertTrue(message.html!!.contains("safely ignore this email"))
    }

    @Test
    fun `never contains the user's current password or a temporary password`() {
        val message = EmailTemplates.passwordResetEmail(
            username = "user",
            token = "raw-token",
            publicAppUrl = "https://app.example.com",
        )

        assertFalse(message.text.contains("current password", ignoreCase = true))
        assertFalse(message.text.contains("temporary password", ignoreCase = true))
        assertFalse(message.html!!.contains("current password", ignoreCase = true))
        assertFalse(message.html!!.contains("temporary password", ignoreCase = true))
    }

    @Test
    fun `trims trailing slash from publicAppUrl before composing the reset URL`() {
        val message = EmailTemplates.passwordResetEmail(
            username = "user",
            token = "raw-token",
            publicAppUrl = "https://app.example.com/",
        )

        assertTrue(message.text.contains("https://app.example.com/reset-password?token=raw-token"))
    }

    @Test
    fun `missing variables raise an explicit error`() {
        val error = runCatching {
            EmailTemplates.passwordResetEmail(
                username = "user",
                token = "",
                publicAppUrl = "https://app.example.com",
            )
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error!!.message!!.contains("token"))
    }

    @Test
    fun `text body greets the username when provided`() {
        val message = EmailTemplates.passwordResetEmail(
            username = "alice",
            token = "raw-token",
            publicAppUrl = "https://app.example.com",
        )

        assertTrue(message.text.contains("Hi alice,"))
        assertEquals("alice,", message.text.lineSequence().first().removePrefix("Hi "))
    }
}
