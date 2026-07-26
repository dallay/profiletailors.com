package com.profiletailors.smp.publishing.infrastructure

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.smp.identity.application.AuthFeature
import com.profiletailors.smp.identity.application.EmailVerificationPolicy
import com.profiletailors.smp.identity.application.NoOpPrincipalIdentityLookup
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.application.permissiveEmailVerificationPolicy
import com.profiletailors.smp.identity.application.requireEmailVerification
import com.profiletailors.smp.publishing.application.PublishingEmailVerificationGate
import com.profiletailors.smp.publishing.application.PublishingFeature
import org.springframework.stereotype.Component

/**
 * Identity-backed adapter for publishing email-verification checks.
 */
@Component
internal class IdentityPublishingEmailVerificationGate(
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy = permissiveEmailVerificationPolicy,
) : PublishingEmailVerificationGate {

    override suspend fun requireVerified(principal: PrincipalContext, feature: PublishingFeature) {
        requireEmailVerification(
            principal = principal,
            principalIdentityLookup = principalIdentityLookup,
            policy = emailVerificationPolicy,
            feature = feature.toIdentityFeature(),
        )
    }

    private fun PublishingFeature.toIdentityFeature(): AuthFeature = when (this) {
        PublishingFeature.PUBLISH_CONTENT -> AuthFeature.PUBLISH_CONTENT
        PublishingFeature.SCHEDULE_POST -> AuthFeature.SCHEDULE_POST
        PublishingFeature.CONNECT_SOCIAL -> AuthFeature.CONNECT_SOCIAL
    }
}
