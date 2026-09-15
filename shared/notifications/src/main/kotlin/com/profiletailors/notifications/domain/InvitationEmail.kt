package com.profiletailors.notifications.domain

import com.profiletailors.leadcapture.common.NormalizedEmail
import java.util.UUID

object InvitationEmailTemplateId {
    const val VALUE: String = "platform.invitation"
    val INSTANCE: TemplateId = TemplateId(VALUE)
}

enum class InvitationEmailTarget { EXISTING_WORKSPACE, NEW_WORKSPACE }

data class InvitationEmail(
    val invitationId: UUID,
    val recipient: NormalizedEmail,
    val workspaceName: String,
    val target: InvitationEmailTarget,
    val acceptUrl: String,
    val rawToken: String,
    val locale: String?,
    val deliveryId: UUID? = null,
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

    fun toPayload(): NotificationPayload = NotificationPayload(
        mapOf(
            "email" to recipient.value,
            "workspaceName" to workspaceName,
            "target" to target.name,
            "acceptUrl" to acceptUrl,
            "locale" to (locale ?: "en"),
        ),
    )

    fun idempotencyKey(): IdempotencyKey = when {
        deliveryId != null -> IdempotencyKey("invitation:$invitationId:resend:$deliveryId")
        else -> IdempotencyKey("invitation:$invitationId:initial")
    }

    fun render(): RenderedEmail = RenderedEmail(
        subject = renderSubject(locale),
        text = renderText(),
        html = renderHtml(),
    )

    private fun renderSubject(locale: String?): String = when {
        target == InvitationEmailTarget.NEW_WORKSPACE -> when (locale?.lowercase()) {
            "es" -> "Has sido invitada a crear un nuevo espacio de trabajo en Profile Tailors"
            else -> "You're invited to create a new Profile Tailors workspace"
        }
        locale?.lowercase() == "es" -> "Has sido invitada a $workspaceName"
        else -> "You're invited to $workspaceName"
    }

    private fun renderText(): String = when (target) {
        InvitationEmailTarget.NEW_WORKSPACE -> """
            |Hi ${recipient.value},
            |
            |$workspaceName
            |
            |To accept the invitation and set up your new workspace, click the link below:
            |
            |$acceptUrl
            |
            |The link is single-use and will expire in line with the workspace's invitation
            |policy. If you did not request this invitation, you can safely ignore this email.
            |
            |— The Profile Tailors team
        """.trimMargin()
        InvitationEmailTarget.EXISTING_WORKSPACE -> """
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
    }

    private fun renderHtml(): String {
        val workspaceParagraph = when (target) {
            InvitationEmailTarget.NEW_WORKSPACE -> {
                escapeHtml(workspaceName)
            }
            InvitationEmailTarget.EXISTING_WORKSPACE -> {
                "${escapeHtml("You've been invited to join the")} " +
                    "<strong>${escapeHtml(workspaceName)}</strong> " +
                    escapeHtml("workspace on Profile Tailors.")
            }
        }
        val actionParagraph = when (target) {
            InvitationEmailTarget.NEW_WORKSPACE -> {
                "To accept the invitation and set up your new workspace, click the link below:"
            }
            InvitationEmailTarget.EXISTING_WORKSPACE -> {
                "To accept the invitation and set up your account, click the link below:"
            }
        }
        return """
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
            |            <p style="margin:0 0 16px;font-size:16px;line-height:1.5;">Hi ${escapeHtml(
            recipient.value,
        )},</p>
            |            <p style="margin:0 0 16px;font-size:16px;line-height:1.5;">$workspaceParagraph</p>
            |            <p style="margin:0 0 16px;font-size:16px;line-height:1.5;">${escapeHtml(actionParagraph)}</p>
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
    }

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
        const val NEW_WORKSPACE_COPY_EN: String =
            "You've been invited to create a new Profile Tailors workspace."
        const val NEW_WORKSPACE_COPY_ES: String =
            "Has sido invitada a crear un nuevo espacio de trabajo en Profile Tailors."
    }
}
