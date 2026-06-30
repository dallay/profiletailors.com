package com.profiletailors.smp.identity.infrastructure.email

/**
 * Plain-text email template for verification messages.
 */
object EmailTemplates {

    private const val DEFAULT_PUBLIC_APP_URL = "https://app.profiletailors.com"

    /**
     * Renders the verification email body.
     *
     * @param username the recipient's display name
     * @param token the raw verification token (must be URL-encoded by the caller if used in a URL)
     * @param publicAppUrl the public application origin used to build the frontend verification route
     *   (e.g., "https://app.profiletailors.com")
     */
    fun verificationEmail(username: String?, token: String, publicAppUrl: String = DEFAULT_PUBLIC_APP_URL): String {
        val normalizedPublicAppUrl = publicAppUrl.trimEnd('/')
        val verificationUrl = "$normalizedPublicAppUrl/verify-email?token=$token"

        return """
            |Hi ${username ?: "there"},
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
    }
}
