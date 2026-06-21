package com.profiletailors.smp.identity.application

enum class AuthFeature {
    PUBLISH_CONTENT,
    SCHEDULE_POST,
    INVITE_TEAM,
    CONNECT_SOCIAL,
    ACCESS_BILLING,
    ENABLE_AUTOMATIONS,
}

/**
 * Policy for determining whether a feature requires email verification.
 *
 * A functional interface allows concise lambda syntax: `EmailVerificationPolicy { true }`
 */
fun interface EmailVerificationPolicy {
    operator fun invoke(feature: AuthFeature): Boolean
}

/**
 * Default policy that requires email verification for all features.
 */
fun emailVerificationPolicyOf(): EmailVerificationPolicy = EmailVerificationPolicy { true }

/**
 * Permissive policy that never requires email verification.
 * Use as default constructor parameter in tests or when gating is handled externally.
 */
val permissiveEmailVerificationPolicy: EmailVerificationPolicy = EmailVerificationPolicy { false }
