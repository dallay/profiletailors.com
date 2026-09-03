package com.profiletailors.notifications.domain

import com.profiletailors.leadcapture.common.NormalizedEmail
import java.util.UUID

/**
 * Identifier for the invitation email template. Centralised so callers don't pass raw strings.
 */
object InvitationEmailTemplateId {
    const val VALUE: String = "platform.invitation"
    val INSTANCE: TemplateId = TemplateId(VALUE)
}

/**
 * Value object capturing the inputs to render an invitation email when an operator
 * invites a waitlist lead into a workspace, or resends a previously minted invitation.
 *
 * The raw invitation token is captured here because the template URL is the only place
 * the token is delivered. The token MUST never be persisted in
 * [toPayload] and MUST never appear in audit events, log lines, or response bodies.
 *
 * @property invitationId canonical invitation identifier (UUID)
 * @property recipient normalised email address of the invitee
 * @property workspaceName human-readable workspace name used in copy
 * @property acceptUrl fully-built URL the invitee clicks to accept; the raw token is
 *                  already encoded inside this URL and the value object is the only
 *                  place that holds it
 * @property rawToken raw token value, kept in memory only to render [acceptUrl] and
 *                  [render]; not propagated to persistence
 * @property locale optional BCP-47 locale code (e.g. "en", "es")
 */
data class InvitationEmail(
    val invitationId: UUID,
    val recipient: NormalizedEmail,
    val workspaceName: String,
    val acceptUrl: String,
    val rawToken: String,
    val locale: String?,
) {
    init {
        require(workspaceName.isNotBlank()) { "Workspace name cannot be blank" }
        require(acceptUrl.isNotBlank()) { "Accept URL cannot be blank" }
        require(rawToken.isNotBlank()) { "Raw token cannot be blank" }
        require(recipient.value.isNotBlank()) { "Recipient cannot be blank" }
        require(acceptUrl.contains(rawToken)) {
            "Accept URL must embed the raw token; refusing to render an email that would leak " +
                "a mismatched invitation token"
        }
    }

    /**
     * Build the [NotificationPayload] used to render the email template. The raw token is
     * intentionally excluded; the consumer passes the [acceptUrl] separately so the
     * rendered body still includes the accept link without persisting the token.
     */
    fun toPayload(): NotificationPayload = NotificationPayload(
        mapOf(
            "email" to recipient.value,
            "workspaceName" to workspaceName,
            "acceptUrl" to acceptUrl,
            "locale" to (locale ?: "en"),
        ),
    )

    /**
     * Compute the [IdempotencyKey] that identifies this invitation across retries.
     *
     * One invitation email per invitation. Re-dispatching the same invitation (e.g. after
     * a crash mid-send) MUST NOT produce a second email to the same address.
     */
    fun idempotencyKey(): IdempotencyKey = IdempotencyKey("invitation:$invitationId:initial")

    /**
     * Render the plain-text and HTML bodies for this invitation email.
     */
    fun render(): RenderedEmail = RenderedEmail(
        subject = renderSubject(locale),
        text = renderText(),
        html = renderHtml(),
    )

    private fun renderSubject(locale: String?): String = when (locale?.lowercase()) {
        "es" -> "Has sido invitada a $workspaceName"
        else -> "You're invited to $workspaceName"
    }

    private fun renderText(): String = """
        |Hi ${recipient.value},
        |
        |You've been invited to join the "$workspaceName" workspace on Profile Tailors.
        |
        |To accept the invitation and set up your account, click the link below:
        |
        |$acceptUrl
        |
        |The link is single-use and will expire in line with the workspace's invitation
        |policy. If you did not request this invitation, you can safely ignore this email.
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
        |            <p style="margin:0 0 16px;color:#a3a3a3;font-family:'Space Mono',monospace;font-size:11px;letter-spacing:.08em;">PROFILE TAILORS / INVITATION</p>
        |            <h1 style="margin:0 0 24px;color:#ffffff;font-size:24px;font-weight:500;line-height:1.2;">${escapeHtml(
        "You're invited",
    )}</h1>
        |            <p style="margin:0 0 16px;font-size:16px;line-height:1.5;">Hi ${escapeHtml(recipient.value)},</p>
        |            <p style="margin:0 0 16px;font-size:16px;line-height:1.5;">${escapeHtml(
        "You've been invited to join the",
    )} <strong>${escapeHtml(
        workspaceName,
    )}</strong> ${escapeHtml("workspace on Profile Tailors.")}</p>
        |            <p style="margin:0 0 16px;font-size:16px;line-height:1.5;">${escapeHtml(
        "To accept the invitation and set up your account, click the link below:",
    )}</p>
        |            <p style="margin:0 0 24px;font-size:14px;line-height:1.5;word-break:break-all;"><a href="${escapeHtml(
        acceptUrl,
    )}" style="color:#7dd3fc;text-decoration:underline;">${escapeHtml(acceptUrl)}</a></p>
        |            <p style="margin:0 0 16px;color:#a3a3a3;font-size:14px;line-height:1.5;">${escapeHtml(
        "The link is single-use and will expire in line with the workspace's invitation policy.",
    )}</p>
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
}
