package com.profiletailors.smp.identity.application

enum class AuthFeature {
    PUBLISH_CONTENT,
    SCHEDULE_POST,
    INVITE_TEAM,
    CONNECT_SOCIAL,
    ACCESS_BILLING,
    ENABLE_AUTOMATIONS,
}

interface EmailVerificationPolicy {
    fun requiresVerification(feature: AuthFeature): Boolean
}

class DefaultEmailVerificationPolicy : EmailVerificationPolicy {
    private val restrictedFeatures = setOf(
        AuthFeature.PUBLISH_CONTENT,
        AuthFeature.SCHEDULE_POST,
        AuthFeature.INVITE_TEAM,
        AuthFeature.CONNECT_SOCIAL,
        AuthFeature.ACCESS_BILLING,
        AuthFeature.ENABLE_AUTOMATIONS,
    )

    override fun requiresVerification(feature: AuthFeature): Boolean =
        feature in restrictedFeatures
}

/**
 * Permissive policy that never requires email verification.
 * Use as default constructor parameter in tests or when gating is handled externally.
 */
class PermissiveEmailVerificationPolicy : EmailVerificationPolicy {
    override fun requiresVerification(feature: AuthFeature): Boolean = false
}
