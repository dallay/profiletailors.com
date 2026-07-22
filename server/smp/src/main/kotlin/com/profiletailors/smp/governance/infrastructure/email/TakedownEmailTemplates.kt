package com.profiletailors.smp.governance.infrastructure.email

import com.profiletailors.notifications.domain.IdempotencyKey
import com.profiletailors.notifications.domain.NotificationPayload
import com.profiletailors.notifications.domain.RenderedEmail
import com.profiletailors.notifications.domain.TemplateId

/**
 * Identifier for the "takedown reported" email template.
 * Sent to workspace admins when a new takedown report is created.
 */
private const val ERR_RECIPIENT_BLANK = "Recipient cannot be blank"
private const val ERR_ASSET_ID_BLANK = "Asset id cannot be blank"

object TakedownReportedEmailTemplateId {
    const val VALUE: String = "governance.takedown.reported"
    val INSTANCE: TemplateId = TemplateId(VALUE)
}

/**
 * Identifier for the "takedown approved" email template.
 * Sent to the original reporter when a takedown report is approved.
 */
object TakedownApprovedEmailTemplateId {
    const val VALUE: String = "governance.takedown.approved"
    val INSTANCE: TemplateId = TemplateId(VALUE)
}

/**
 * Identifier for the "takedown rejected" email template.
 * Sent to the original reporter when a takedown report is rejected.
 */
object TakedownRejectedEmailTemplateId {
    const val VALUE: String = "governance.takedown.rejected"
    val INSTANCE: TemplateId = TemplateId(VALUE)
}

/**
 * Email rendered when a new takedown report is submitted.
 * Notifies workspace admins so they can review and act.
 */
data class TakedownReportedEmail(
    val reportId: String,
    val recipient: String,
    val assetId: String,
    val reason: String,
    val reporterEmail: String,
    val mediaReferenceUrl: String?,
) {
    init {
        require(recipient.isNotBlank()) { ERR_RECIPIENT_BLANK }
        require(assetId.isNotBlank()) { ERR_ASSET_ID_BLANK }
    }

    fun toPayload(): NotificationPayload = NotificationPayload(
        mapOf(
            "reportId" to reportId,
            "assetId" to assetId,
            "reason" to reason,
            "reporterEmail" to reporterEmail,
            "mediaReferenceUrl" to (mediaReferenceUrl ?: ""),
        ),
    )

    /**
     * One admin-notification email per (reportId, recipient).
     */
    fun idempotencyKey(): IdempotencyKey = IdempotencyKey("governance.takedown.reported:$reportId:$recipient")

    fun render(): RenderedEmail = RenderedEmail(
        subject = "New takedown report requires review",
        text = """
            |Hi,
            |
            |A new copyright/DMCA takedown report has been submitted for one of your
            |workspace's media assets.
            |
            |Report ID: $reportId
            |Asset ID: $assetId
            |Reporter: $reporterEmail
            |Reason: $reason
            |${if (mediaReferenceUrl != null) "Reference: $mediaReferenceUrl" else ""}
            |
            |Please review the report and approve or reject it at your earliest convenience.
            |
            |— The Profile Tailors team
        """.trimMargin(),
        html = reportedHtmlBody(),
    )

    private fun reportedHtmlBody(): String = """
        |<!doctype html>
        |<html lang="en">
        |  <body style="margin:0;padding:0;background:#0a0a0a;color:#e5e5e5;font-family:'Space Grotesk',Arial,sans-serif;">
        |    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:#0a0a0a;border-collapse:collapse;">
        |      <tr><td align="center" style="padding:32px 16px;">
|        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:600px;background:#1a1a1a;border:1px solid #333333;border-collapse:collapse;">
|          <tr><td style="padding:32px;">
|            <p style="margin:0 0 16px;color:#a3a3a3;font-family:'Space Mono',monospace;font-size:11px;letter-spacing:.08em;">PROFILE TAILORS / GOVERNANCE</p>
        |            <h1 style="margin:0 0 24px;color:#ffffff;font-size:24px;font-weight:500;line-height:1.2;">${escapeHtml(
        "New takedown report requires review",
    )}</h1>
        |            <p style="margin:0 0 16px;font-size:16px;line-height:1.5;">${escapeHtml(
        "A new copyright/DMCA takedown report has been submitted for one of your workspace's media assets.",
    )}</p>
        |            <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="margin:0 0 16px;border-collapse:collapse;">
        |              <tr><td style="padding:8px 0;color:#a3a3a3;font-size:14px;">Report ID</td><td style="padding:8px 0;font-family:'Space Mono',monospace;font-size:14px;">${escapeHtml(
        reportId,
    )}</td></tr>
        |              <tr><td style="padding:8px 0;color:#a3a3a3;font-size:14px;">Asset ID</td><td style="padding:8px 0;font-family:'Space Mono',monospace;font-size:14px;">${escapeHtml(
        assetId,
    )}</td></tr>
        |              <tr><td style="padding:8px 0;color:#a3a3a3;font-size:14px;">Reporter</td><td style="padding:8px 0;font-size:14px;">${escapeHtml(
        reporterEmail,
    )}</td></tr>
        |              <tr><td style="padding:8px 0;color:#a3a3a3;font-size:14px;">Reason</td><td style="padding:8px 0;font-size:14px;">${escapeHtml(
        reason,
    )}</td></tr>
        |              ${if (mediaReferenceUrl != null) {
        """<tr><td style="padding:8px 0;color:#a3a3a3;font-size:14px;">Reference</td><td style="padding:8px 0;font-size:14px;word-break:break-all;">${escapeHtml(
            mediaReferenceUrl,
        )}</td></tr>"""
    } else {
        ""
    }}
        |            </table>
        |            <p style="margin:0;color:#a3a3a3;font-size:14px;line-height:1.5;">${escapeHtml(
        "Please review the report and approve or reject it at your earliest convenience.",
    )}</p>
        |          </td></tr>
        |        </table>
        |      </td></tr>
        |    </table>
        |  </body>
        |</html>
    """.trimMargin()
}

/**
 * Email rendered when a takedown report is approved.
 * Notifies the original reporter that their report was accepted.
 */
data class TakedownApprovedEmail(val reportId: String, val recipient: String, val assetId: String) {
    init {
        require(recipient.isNotBlank()) { ERR_RECIPIENT_BLANK }
        require(assetId.isNotBlank()) { ERR_ASSET_ID_BLANK }
    }

    fun toPayload(): NotificationPayload = NotificationPayload(
        mapOf(
            "reportId" to reportId,
            "assetId" to assetId,
        ),
    )

    fun idempotencyKey(): IdempotencyKey = IdempotencyKey("governance.takedown.approved:$reportId:$recipient")

    fun render(): RenderedEmail = RenderedEmail(
        subject = "Your takedown report has been approved",
        text = """
            |Hi,
            |
            |Your takedown report (ID: $reportId) for asset $assetId has been reviewed
            |and approved. The asset has been suspended and is no longer visible.
            |
            |Thank you for helping us maintain the integrity of our platform.
            |
            |— The Profile Tailors team
        """.trimMargin(),
        html = """
            |<!doctype html>
            |<html lang="en">
            |  <body style="margin:0;padding:0;background:#0a0a0a;color:#e5e5e5;font-family:'Space Grotesk',Arial,sans-serif;">
            |    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:#0a0a0a;border-collapse:collapse;">
            |      <tr><td align="center" style="padding:32px 16px;">
            |        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:600px;background:#1a1a1a;border:1px solid #333333;border-collapse:collapse;">
            |          <td style="padding:32px;">
            |            <p style="margin:0 0 16px;color:#a3a3a3;font-family:'Space Mono',monospace;font-size:11px;letter-spacing:.08em;">PROFILE TAILORS / GOVERNANCE</p>
            |            <h1 style="margin:0 0 24px;color:#ffffff;font-size:24px;font-weight:500;line-height:1.2;">${escapeHtml(
            "Your takedown report has been approved",
        )}</h1>
            |            <p style="margin:0 0 16px;font-size:16px;line-height:1.5;">${escapeHtml(
            "Your takedown report has been reviewed and approved. The asset has been suspended and is no longer visible.",
        )}</p>
            |            <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="margin:0 0 16px;border-collapse:collapse;">
            |              <tr><td style="padding:8px 0;color:#a3a3a3;font-size:14px;">Report ID</td><td style="padding:8px 0;font-family:'Space Mono',monospace;font-size:14px;">${escapeHtml(
            reportId,
        )}</td></tr>
            |              <tr><td style="padding:8px 0;color:#a3a3a3;font-size:14px;">Asset ID</td><td style="padding:8px 0;font-family:'Space Mono',monospace;font-size:14px;">${escapeHtml(
            assetId,
        )}</td></tr>
            |            </table>
            |            <p style="margin:0;color:#a3a3a3;font-size:14px;line-height:1.5;">${escapeHtml(
            "Thank you for helping us maintain the integrity of our platform.",
        )}</p>
            |          </td>
            |        </table>
            |      </td></tr>
            |    </table>
            |  </body>
            |</html>
        """.trimMargin(),
    )
}

/**
 * Email rendered when a takedown report is rejected.
 * Notifies the original reporter that their report was reviewed and rejected.
 */
data class TakedownRejectedEmail(
    val reportId: String,
    val recipient: String,
    val assetId: String,
    val rejectionReason: String?,
) {
    init {
        require(recipient.isNotBlank()) { ERR_RECIPIENT_BLANK }
        require(assetId.isNotBlank()) { ERR_ASSET_ID_BLANK }
    }

    fun toPayload(): NotificationPayload = NotificationPayload(
        mapOf(
            "reportId" to reportId,
            "assetId" to assetId,
            "rejectionReason" to (rejectionReason ?: ""),
        ),
    )

    fun idempotencyKey(): IdempotencyKey = IdempotencyKey("governance.takedown.rejected:$reportId:$recipient")

    fun render(): RenderedEmail {
        val reviewerNoteRow = if (rejectionReason != null) {
            """<tr><td style="padding:8px 0;color:#a3a3a3;font-size:14px;">Reviewer note</td><td style="padding:8px 0;font-size:14px;">${escapeHtml(
                rejectionReason,
            )}</td></tr>"""
        } else {
            ""
        }
        return RenderedEmail(
            subject = "Your takedown report has been reviewed and rejected",
            text = """
                |Hi,
                |
                |Your takedown report (ID: $reportId) for asset $assetId has been reviewed
                |and was not approved.
                |${if (rejectionReason != null) "\n|Reviewer note: $rejectionReason" else ""}
                |
                |If you believe this decision was made in error, please contact support.
                |
                |— The Profile Tailors team
            """.trimMargin(),
            html = """
                |<!doctype html>
                |<html lang="en">
                |  <body style="margin:0;padding:0;background:#0a0a0a;color:#e5e5e5;font-family:'Space Grotesk',Arial,sans-serif;">
                |    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:#0a0a0a;border-collapse:collapse;">
                |      <tr><td align="center" style="padding:32px 16px;">
                |        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:600px;background:#1a1a1a;border:1px solid #333333;border-collapse:collapse;">
                |          <td style="padding:32px;">
                |            <p style="margin:0 0 16px;color:#a3a3a3;font-family:'Space Mono',monospace;font-size:11px;letter-spacing:.08em;">PROFILE TAILORS / GOVERNANCE</p>
                |            <h1 style="margin:0 0 24px;color:#ffffff;font-size:24px;font-weight:500;line-height:1.2;">${escapeHtml(
                "Your takedown report has been reviewed and rejected",
            )}</h1>
                |            <p style="margin:0 0 16px;font-size:16px;line-height:1.5;">${escapeHtml(
                "Your takedown report has been reviewed and was not approved.",
            )}</p>
                |            <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="margin:0 0 16px;border-collapse:collapse;">
                |              <tr><td style="padding:8px 0;color:#a3a3a3;font-size:14px;">Report ID</td><td style="padding:8px 0;font-family:'Space Mono',monospace;font-size:14px;">${escapeHtml(
                reportId,
            )}</td></tr>
                |              <tr><td style="padding:8px 0;color:#a3a3a3;font-size:14px;">Asset ID</td><td style="padding:8px 0;font-family:'Space Mono',monospace;font-size:14px;">${escapeHtml(
                assetId,
            )}</td></tr>
                |              $reviewerNoteRow
                |            </table>
                |            <p style="margin:0;color:#a3a3a3;font-size:14px;line-height:1.5;">${escapeHtml(
                "If you believe this decision was made in error, please contact support.",
            )}</p>
                |          </td>
                |        </table>
                |      </td></tr>
                |    </table>
                |  </body>
                |</html>
            """.trimMargin(),
        )
    }
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
