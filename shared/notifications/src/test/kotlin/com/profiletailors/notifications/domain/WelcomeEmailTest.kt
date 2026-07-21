package com.profiletailors.notifications.domain

import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class WelcomeEmailTest {

    @Test
    fun `idempotencyKey is stable per waitlist entry`() {
        val email = welcome()
        assertEquals("waitlist.welcome:${email.waitlistEntryId.value}", email.idempotencyKey().value)
    }

    @Test
    fun `payload exposes the variables the template needs`() {
        val email = welcome()
        val payload = email.toPayload()
        assertEquals("user@example.com", payload["email"])
        assertEquals("Profile Tailors Launch", payload["waitlistName"])
        assertEquals("es", payload["locale"])
    }

    @Test
    fun `default locale is en when not provided`() {
        val email = welcome(locale = null)
        assertEquals("en", email.toPayload()["locale"])
    }

    @Test
    fun `subject is localised by locale`() {
        assertTrue(welcome(locale = "en").render().subject.startsWith("Welcome"))
        assertTrue(welcome(locale = "ES").render().subject.startsWith("¡Bienvenido"))
    }

    @Test
    fun `render produces text and HTML bodies`() {
        val rendered = welcome().render()
        assertTrue(rendered.text.contains("user@example.com"))
        assertTrue(rendered.text.contains("Profile Tailors Launch"))
        assertNotNull(rendered.html)
        assertTrue(rendered.html!!.contains("Profile Tailors Launch"))
    }

    @Test
    fun `escapeHtml neutralises dangerous characters in HTML body`() {
        val email = welcome(
            recipient = NormalizedEmail.from(
                com.profiletailors.leadcapture.common.EmailAddress("naughty<script>@example.com"),
            ),
            waitlistName = "Pro<Tailors> & \"Friends\"",
        )
        val html = email.render().html!!
        assertTrue("&lt;script&gt;" in html, "expected escaped <script> in html body")
        assertTrue("&amp;" in html, "expected escaped & in waitlist name")
        assertTrue("&quot;Friends&quot;" in html, "expected escaped quotes in waitlist name")
    }

    @Test
    fun `blank waitlistName is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            welcome(waitlistName = "   ")
        }
    }

    private fun welcome(
        waitlistEntryId: WaitlistEntryId = WaitlistEntryId("entry-1"),
        recipient: NormalizedEmail = NormalizedEmail.from(
            com.profiletailors.leadcapture.common.EmailAddress("user@example.com"),
        ),
        waitlistName: String = "Profile Tailors Launch",
        locale: String? = "es",
    ): WelcomeEmail = WelcomeEmail(
        waitlistEntryId = waitlistEntryId,
        recipient = recipient,
        waitlistName = waitlistName,
        locale = locale,
    )
}
