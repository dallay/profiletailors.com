package com.profiletailors.smp.identity.infrastructure.email

import com.profiletailors.smp.identity.application.EmailMessage
import org.slf4j.LoggerFactory

fun interface VerificationEmailHtmlRenderer {
    fun render(username: String, verificationUrl: String): String
}

/** Verification email templates with complete plain-text and conservative inline HTML bodies. */
object EmailTemplates {

    private const val DEFAULT_PUBLIC_APP_URL = "https://app.profiletailors.com"
    private val log = LoggerFactory.getLogger(EmailTemplates::class.java)

    fun verificationEmail(
        username: String?,
        token: String,
        publicAppUrl: String = DEFAULT_PUBLIC_APP_URL,
        htmlRenderer: VerificationEmailHtmlRenderer = VerificationEmailHtmlRenderer(::verificationHtml),
    ): EmailMessage {
        val missingVariables = listOfNotNull(
            "token".takeIf { token.isBlank() },
            "publicAppUrl".takeIf { publicAppUrl.isBlank() },
        )
        require(missingVariables.isEmpty()) {
            val vars = missingVariables.joinToString(", ")
            val message = "Missing required verification email template variables: $vars"
            log.error(message)
            message
        }

        val normalizedPublicAppUrl = publicAppUrl.trimEnd('/')
        val verificationUrl = "$normalizedPublicAppUrl/verify-email?token=$token"
        val greetingName = username ?: "there"
        val text = """
            |Hi $greetingName,
            |
            |Welcome to Profile Tailors! Please verify your email address by clicking the link below:
            |
            |$verificationUrl
            |
            |This verification link expires in 24 hours.
            |
            |If you did not create an account, please ignore this email.
            |
            |Best,
            |The Profile Tailors Team
        """.trimMargin()

        val html = runCatching {
            htmlRenderer.render(escapeHtml(greetingName), escapeHtml(verificationUrl))
        }.onFailure { error ->
            log.error("Verification email HTML template rendering failed; falling back to plain text", error)
        }.getOrNull()

        return EmailMessage(
            text = text,
            html = html,
        )
    }

    /**
     * Builds the HTML body for a verification email.
     *
     * @param username The escaped recipient name shown in the greeting.
     * @param verificationUrl The escaped URL used for the verification link and fallback text.
     * @return A complete HTML document for the verification email.
     */
    private fun verificationHtml(username: String, verificationUrl: String): String = """
        <!doctype html>
        <html lang="en">
          <body style="margin:0;padding:0;background:#0a0a0a;color:#e5e5e5;font-family:'Space Grotesk','DM Sans',Arial,sans-serif;">
            <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:#0a0a0a;border-collapse:collapse;">
              <tr><td align="center" style="padding:32px 16px;">
                <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:600px;background:#1a1a1a;border:1px solid #333333;border-collapse:collapse;">
                  <tr><td style="padding:32px;">
                    <p style="margin:0 0 24px;color:#a3a3a3;font-family:'Space Mono','JetBrains Mono',monospace;font-size:11px;font-weight:700;letter-spacing:.08em;">PROFILE TAILORS / EMAIL VERIFICATION</p>
                    <h1 style="margin:0 0 24px;color:#ffffff;font-size:24px;font-weight:500;line-height:1.2;">Verify your email address</h1>
                    <p style="margin:0 0 16px;font-size:16px;line-height:1.5;">Hi $username,</p>
                    <p style="margin:0 0 16px;font-size:16px;line-height:1.5;">Welcome to Profile Tailors. Verify your email address to activate your account.</p>
                    <p style="margin:0 0 24px;color:#a3a3a3;font-size:14px;line-height:1.5;">This verification link expires in 24 hours.</p>
                    <p style="margin:0 0 32px;"><a href="$verificationUrl" style="display:inline-block;background:#ffffff;color:#0a0a0a;border-radius:999px;padding:14px 24px;font-family:'Space Mono','JetBrains Mono',monospace;font-size:11px;font-weight:700;letter-spacing:.08em;text-decoration:none;">Verify Email</a></p>
                    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="border-top:1px solid #333333;border-bottom:1px solid #333333;border-collapse:collapse;">
                      <tr><td style="padding:16px 0;color:#a3a3a3;font-family:'Space Mono','JetBrains Mono',monospace;font-size:12px;letter-spacing:.04em;">STATUS</td><td align="right" style="padding:16px 0;color:#D4A843;font-family:'Space Mono','JetBrains Mono',monospace;font-size:12px;">PENDING</td></tr>
                      <tr><td style="padding:0 0 16px;color:#a3a3a3;font-family:'Space Mono','JetBrains Mono',monospace;font-size:12px;letter-spacing:.04em;">EXPIRES IN</td><td align="right" style="padding:0 0 16px;color:#e5e5e5;font-family:'Space Mono','JetBrains Mono',monospace;font-size:12px;">24 HOURS</td></tr>
                    </table>
                    <p style="margin:24px 0 8px;color:#a3a3a3;font-size:14px;line-height:1.5;">If the button does not work, copy this URL:</p>
                    <p style="margin:0 0 24px;color:#e5e5e5;font-family:'Space Mono','JetBrains Mono',monospace;font-size:12px;line-height:1.5;word-break:break-all;">$verificationUrl</p>
                    <p style="margin:0;color:#a3a3a3;font-size:14px;line-height:1.5;">If you did not create an account, ignore this email.</p>
                  </td></tr>
                </table>
              </td></tr>
            </table>
          </body>
        </html>
    """.trimIndent()

    /**
     * Escapes HTML-sensitive characters in a string.
     *
     * @param value The string to escape.
     * @return The escaped string.
     */
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
