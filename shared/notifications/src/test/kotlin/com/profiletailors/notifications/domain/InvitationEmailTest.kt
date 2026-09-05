package com.profiletailors.notifications.domain

import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.NormalizedEmail
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class InvitationEmailTest {

    @Test
    fun `idempotencyKey is stable per invitation and distinct across invitations`() {
        val a = invitation(invitationId = invitationId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
        val b = invitation(invitationId = invitationId("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"))
        assertEquals("platform.invitation:${a.invitationId}", a.idempotencyKey().value)
        assertNotEquals(a.idempotencyKey().value, b.idempotencyKey().value)
    }

    @Test
    fun `payload exposes the variables the template needs`() {
        val email = invitation()
        val payload = email.toPayload()
        assertEquals("user@example.com", payload["email"])
        assertEquals("https://app.profiletailors.com/invitations/accept?token=raw-token", payload["acceptUrl"])
        assertEquals("Profile Tailors Launch", payload["workspaceName"])
        assertEquals("es", payload["locale"])
    }

    @Test
    fun `default locale is en when not provided`() {
        val email = invitation(locale = null)
        assertEquals("en", email.toPayload()["locale"])
    }

    @Test
    fun `subject is localised by locale`() {
        assertTrue(invitation(locale = "en").render().subject.startsWith("You're invited"))
        assertTrue(invitation(locale = "ES").render().subject.startsWith("Has sido invitada"))
    }

    @Test
    fun `render produces text and HTML bodies that include the accept URL and recipient`() {
        val rendered = invitation().render()
        assertTrue(rendered.text.contains("user@example.com"))
        assertTrue(rendered.text.contains("accept?token=raw-token"))
        assertNotNull(rendered.html)
        assertTrue(rendered.html!!.contains("accept?token=raw-token"))
        assertTrue(rendered.html!!.contains("Profile Tailors Launch"))
    }

    @Test
    fun `escapeHtml neutralises dangerous characters in HTML body and accept URL`() {
        val email = invitation(
            recipient = NormalizedEmail.from(EmailAddress("naughty<script>@example.com")),
            workspaceName = "Pro<Tailors> & \"Friends\"",
            acceptUrl = "https://app.example.com/invitations/accept?token=raw-token&x=\"y\"",
        )
        val html = email.render().html!!
        assertTrue("&lt;script&gt;" in html, "expected escaped <script> in recipient")
        assertTrue("&amp;" in html, "expected escaped & in workspace name")
        assertTrue("&amp;x=" in html, "expected escaped ampersand in accept URL")
        assertTrue("&quot;y&quot;" in html, "expected escaped quotes in accept URL")
    }

    @Test
    fun `blank workspaceName is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            invitation(workspaceName = "   ")
        }
    }

    @Test
    fun `blank accept URL is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            invitation(acceptUrl = "")
        }
    }

    @Test
    fun `blank recipient is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            invitation(recipient = NormalizedEmail.from(EmailAddress("   ")))
        }
    }

    @Test
    fun `raw invitation token is exposed only through acceptUrl, never as a separate payload key`() {
        val email = invitation(
            rawToken = "super-secret-token-do-not-leak",
            acceptUrl = "https://app.example.com/invitations/accept?token=super-secret-token-do-not-leak",
        )
        val payload = email.toPayload()
        assertTrue(payload["token"] == null, "payload MUST NOT expose a raw token key")
        assertTrue(payload["rawToken"] == null, "payload MUST NOT expose a rawToken key")
        assertTrue(payload["acceptUrl"]!!.contains("super-secret-token-do-not-leak"))
    }

    private fun invitation(
        invitationId: UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
        recipient: NormalizedEmail = NormalizedEmail.from(EmailAddress("user@example.com")),
        workspaceName: String = "Profile Tailors Launch",
        acceptUrl: String = "https://app.profiletailors.com/invitations/accept?token=raw-token",
        locale: String? = "es",
        rawToken: String = "raw-token",
    ): InvitationEmail = InvitationEmail(
        invitationId = invitationId,
        recipient = recipient,
        workspaceName = workspaceName,
        acceptUrl = acceptUrl,
        rawToken = rawToken,
        locale = locale,
    )

    private fun invitationId(raw: String): UUID = UUID.fromString(raw)
}
