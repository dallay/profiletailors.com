package com.profiletailors.notifications.domain

import com.profiletailors.leadcapture.common.EmailAddress
import com.profiletailors.leadcapture.common.NormalizedEmail
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class InvitationEmailTest {

    @Test
    fun `resend idempotency key includes delivery identity`() {
        val email = invitation(deliveryId = UUID.randomUUID())

        assertEquals(
            "invitation:${email.invitationId}:resend:${email.deliveryId}",
            email.idempotencyKey().value,
        )
    }

    @Test
    fun `resend with delivery identity is distinct from initial and from another resend`() {
        val initial = invitation(deliveryId = null)
        val resendA = invitation(deliveryId = UUID.randomUUID())
        val resendB = invitation(deliveryId = UUID.randomUUID())

        assertEquals("invitation:${initial.invitationId}:initial", initial.idempotencyKey().value)
        assertEquals("invitation:${resendA.invitationId}:resend:${resendA.deliveryId}", resendA.idempotencyKey().value)
        assertNotEquals(resendA.idempotencyKey(), resendB.idempotencyKey())
    }

    @Test
    fun `idempotencyKey is stable per invitation and distinct across invitations`() {
        val a = invitation(invitationId = invitationId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
        val b = invitation(invitationId = invitationId("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"))
        assertEquals("invitation:${a.invitationId}:initial", a.idempotencyKey().value)
        assertNotEquals(a.idempotencyKey().value, b.idempotencyKey().value)
    }

    @Test
    fun `payload exposes the variables the template needs`() {
        val email = invitation()
        val payload = email.toPayload()
        assertEquals("user@example.com", payload["email"])
        assertEquals(email.invitationId.toString(), payload["invitationId"])
        assertEquals("Profile Tailors Launch", payload["workspaceName"])
        assertEquals("es", payload["locale"])
        assertEquals("EXISTING_WORKSPACE", payload["target"])
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
    fun `persisted payload key set carries correlation without bearer material`() {
        val payload = invitation().toPayload()

        assertEquals(setOf("invitationId", "email", "workspaceName", "target", "locale"), payload.variables.keys)
    }

    @Test
    fun `persisted payload carries no raw token value in any variable`() {
        val raw = "super-secret-token-do-not-leak"
        val email = invitation(
            rawToken = raw,
            acceptUrl = "https://app.example.com/invitations/accept?token=$raw",
        )
        val payload = email.toPayload()

        payload.variables.values.forEach { value ->
            assertFalse(value.contains(raw))
        }
    }

    @Test
    fun `persisted payload never exposes a token key and render keeps the accept URL`() {
        val email = invitation(
            rawToken = "super-secret-token-do-not-leak",
            acceptUrl = "https://app.example.com/invitations/accept?token=super-secret-token-do-not-leak",
        )
        val payload = email.toPayload()
        assertTrue(payload["token"] == null, "payload MUST NOT expose a raw token key")
        assertTrue(payload["rawToken"] == null, "payload MUST NOT expose a rawToken key")
        assertTrue(payload["acceptUrl"] == null, "payload MUST NOT persist the bearer accept URL")
        assertTrue(email.render().text.contains("super-secret-token-do-not-leak"))
    }

    @Test
    fun `persisted payload carries no bearer material while render keeps the accept URL`() {
        val raw = "super-secret-bearer-do-not-persist"
        val email = invitation(
            rawToken = raw,
            acceptUrl = "https://app.example.com/invitations/accept?token=$raw",
        )
        val payload = email.toPayload()

        assertFalse(payload.variables.containsKey("acceptUrl"))
        assertFalse(payload.variables.containsKey("token"))
        assertFalse(payload.variables.containsKey("rawToken"))
        payload.variables.values.forEach { value ->
            assertFalse(value.contains(raw))
        }
        assertEquals(email.invitationId.toString(), payload["invitationId"])
        assertTrue(email.render().text.contains(raw))
    }

    @Test
    fun `NEW_WORKSPACE copy is rendered in text body and subject for English locale`() {
        val email = invitation(
            target = InvitationEmailTarget.NEW_WORKSPACE,
            workspaceName = InvitationEmail.NEW_WORKSPACE_COPY_EN,
            locale = "en",
        )
        val rendered = email.render()
        assertTrue(rendered.text.contains(InvitationEmail.NEW_WORKSPACE_COPY_EN))
        assertTrue(rendered.subject.startsWith("You're invited to create a new Profile Tailors workspace"))
    }

    @Test
    fun `NEW_WORKSPACE copy is rendered for Spanish locale`() {
        val email = invitation(
            target = InvitationEmailTarget.NEW_WORKSPACE,
            workspaceName = InvitationEmail.NEW_WORKSPACE_COPY_ES,
            locale = "es",
        )
        val rendered = email.render()
        assertTrue(rendered.text.contains(InvitationEmail.NEW_WORKSPACE_COPY_ES))
        assertTrue(rendered.subject.startsWith("Has sido invitada a crear un nuevo espacio de trabajo"))
    }

    @Test
    fun `NEW_WORKSPACE target marks the payload for the template engine`() {
        val email = invitation(target = InvitationEmailTarget.NEW_WORKSPACE)
        assertEquals("NEW_WORKSPACE", email.toPayload()["target"])
    }

    private fun invitation(
        invitationId: UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
        recipient: NormalizedEmail = NormalizedEmail.from(EmailAddress("user@example.com")),
        workspaceName: String = "Profile Tailors Launch",
        acceptUrl: String = "https://app.profiletailors.com/invitations/accept?token=raw-token",
        locale: String? = "es",
        rawToken: String = "raw-token",
        target: InvitationEmailTarget = InvitationEmailTarget.EXISTING_WORKSPACE,
        deliveryId: UUID? = null,
    ): InvitationEmail = InvitationEmail(
        invitationId = invitationId,
        recipient = recipient,
        workspaceName = workspaceName,
        target = target,
        acceptUrl = acceptUrl,
        rawToken = rawToken,
        locale = locale,
        deliveryId = deliveryId,
    )

    private fun invitationId(raw: String): UUID = UUID.fromString(raw)
}
