package com.profiletailors.smp.identity.infrastructure.email

/**
 * Plain-text email template for verification messages.
 */
object EmailTemplates {

    /**
     * Renders the verification email body.
     *
     * @param username the recipient's display name
     * @param token the raw verification token (must be URL-encoded by the caller if used in a URL)
     * @param verificationBaseUrl the base URL for the verification endpoint
     *   (e.g., "https://app.profiletailors.com/api/auth/verify-email")
     */
    fun verificationEmail(
        username: String?,
        token: String,
        verificationBaseUrl: String = VERIFICATION_BASE_URL,
    ): String = """
        |Hi ${username ?: "there"},
        |
        |Welcome to Profile Tailors! Please verify your email address by clicking the link below:
        |
        |$verificationBaseUrl?token=$token
        |
        |This link expires in 24 hours.
        |
        |If you did not create an account, please ignore this email.
        |
        |Best,
        |The Profile Tailors Team
        """.trimMargin()

    private const val VERIFICATION_BASE_URL =
        "https://app.profiletailors.com/api/auth/verify-email"
}
