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
    override fun requiresVerification(feature: AuthFeature): Boolean = true
}

/**
 * Permissive policy that never requires email verification.
 * Use as default constructor parameter in tests or when gating is handled externally.
 */
class PermissiveEmailVerificationPolicy : EmailVerificationPolicy {
    override fun requiresVerification(feature: AuthFeature): Boolean = false
}
