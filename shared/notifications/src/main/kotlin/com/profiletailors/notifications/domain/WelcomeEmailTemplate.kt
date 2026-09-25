package com.profiletailors.notifications.domain

import com.profiletailors.leadcapture.common.NormalizedEmail
import com.profiletailors.leadcapture.waitlist.domain.WaitlistEntryId

/**
 * Identifier for the welcome email template. Centralised so callers don't pass raw strings.
 */
object WelcomeEmailTemplateId {
    const val VALUE: String = "waitlist.welcome"
    val INSTANCE: TemplateId = TemplateId(VALUE)
}

/**
 * Value object capturing the inputs to render a welcome email after a waitlist submission.
 *
 * Encapsulates the small piece of business logic that turns a waitlist submission event
 * into a rendered [Notification] record. The render output (subject, text, html) is
 * derived from a stable template — see [render].
 */
data class WelcomeEmail(
    val waitlistEntryId: WaitlistEntryId,
    val recipient: NormalizedEmail,
    val waitlistName: String,
    val locale: String?,
    val withdrawalUrl: String,
) {
    init {
        require(waitlistName.isNotBlank()) { "Waitlist name cannot be blank" }
    }

    /**
     * Render the plain-text and HTML bodies for this welcome email.
     */
    fun render(): RenderedEmail = RenderedEmail(
        subject = renderSubject(locale),
        text = renderText(),
        html = renderHtml(),
    )

    private fun renderSubject(locale: String?): String = when (locale?.lowercase()) {
        "es" -> "¡Bienvenido a la lista de espera de $waitlistName!"
        else -> "Welcome to the $waitlistName waitlist!"
    }

    private fun renderText(): String = """
        |Hi ${recipient.value},
        |
        |Thanks for joining the $waitlistName waitlist. We'll let you know as soon as
        |a spot opens up.
        |
        |In the meantime, keep an eye on your inbox — that's where invitations go.
        |
        |You can withdraw your waitlist entry here: $withdrawalUrl
        |
        |— The Profile Tailors team
    """.trimMargin()

    private fun renderHtml(): String = """
        |<!doctype html>
        |<html lang="en">
        |  <body style="margin:0;padding:0;background:#0a0a0a;color:#e5e5e5;font-family:'Space Grotesk',Arial,sans-serif;">
        |    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:#0a0a0a;border-collapse:collapse;">
        |      <tr><td align="center" style="padding:32px 16px;">
        |        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:600px;background:#1a1a1a;border:1px solid #333333;border-collapse:collapse;">
        |          <tr><td style="padding:32px;">
        |            <p style="margin:0 0 16px;color:#a3a3a3;font-family:'Space Mono',monospace;font-size:11px;letter-spacing:.08em;">PROFILE TAILORS / WAITLIST</p>
        |            <h1 style="margin:0 0 24px;color:#ffffff;font-size:24px;font-weight:500;line-height:1.2;">${escapeHtml(
        "You're on the list",
    )}</h1>
        |            <p style="margin:0 0 16px;font-size:16px;line-height:1.5;">Hi ${escapeHtml(recipient.value)},</p>
        |            <p style="margin:0 0 16px;font-size:16px;line-height:1.5;">${escapeHtml(
        "Thanks for joining the",
    )} <strong>${escapeHtml(
        waitlistName,
    )}</strong> ${escapeHtml("waitlist. We'll let you know as soon as a spot opens up.")}</p>
        |            <p style="margin:0 0 16px;color:#a3a3a3;font-size:14px;line-height:1.5;">${escapeHtml(
        "In the meantime, keep an eye on your inbox — that's where invitations go.",
    )}</p>
        |            <p style="margin:0;color:#a3a3a3;font-size:14px;line-height:1.5;"><a href="${escapeHtml(
        withdrawalUrl,
    )}" style="color:#ffffff;">${escapeHtml("Withdraw from the waitlist")}</a></p>
        |          </td></tr>
        |        </table>
        |      </td></tr>
        |    </table>
        |  </body>
        |</html>
    """.trimMargin()

    private fun escapeHtml(value: String): String = buildString(value.length) {
        value.forEach { character ->
            append(
                when (character) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&#39;"
                    else -> character
                },
            )
        }
    }

    companion object {
        /** Identifies the welcome notification for one waitlist entry across retries. */
        fun idempotencyKeyFor(entryId: WaitlistEntryId): IdempotencyKey =
            IdempotencyKey("waitlist.welcome:${entryId.value}")

        /** Stores only the fields needed to reconstruct the email during reconciliation. */
        fun payloadFor(entryId: WaitlistEntryId, waitlistName: String, locale: String?): NotificationPayload =
            NotificationPayload(
                mapOf(
                    "waitlistEntryId" to entryId.value,
                    "waitlistName" to waitlistName,
                    "locale" to (locale ?: "en"),
                ),
            )

        fun entryIdFrom(payload: NotificationPayload): WaitlistEntryId? =
            payload["waitlistEntryId"]?.let(::WaitlistEntryId)

        fun fromPayload(
            payload: NotificationPayload,
            recipient: NormalizedEmail,
            withdrawalUrl: String,
        ): WelcomeEmail? {
            val entryId = entryIdFrom(payload) ?: return null
            val waitlistName = payload["waitlistName"] ?: return null
            return WelcomeEmail(entryId, recipient, waitlistName, payload["locale"], withdrawalUrl)
        }
    }
}

/**
 * Rendered email content. The dispatcher sends this to the configured email provider.
 */
data class RenderedEmail(val subject: String, val text: String, val html: String?)
