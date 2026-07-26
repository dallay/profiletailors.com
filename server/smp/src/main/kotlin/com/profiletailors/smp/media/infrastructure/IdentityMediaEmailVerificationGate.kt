package com.profiletailors.smp.media.infrastructure

import com.profiletailors.common.domain.context.PrincipalContext
import com.profiletailors.smp.identity.application.AuthFeature
import com.profiletailors.smp.identity.application.EmailVerificationPolicy
import com.profiletailors.smp.identity.application.NoOpPrincipalIdentityLookup
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.application.permissiveEmailVerificationPolicy
import com.profiletailors.smp.identity.application.requireEmailVerification
import com.profiletailors.smp.media.application.MediaEmailVerificationGate
import com.profiletailors.smp.media.application.MediaFeature
import org.springframework.stereotype.Component

/**
 * Identity-backed adapter for media email-verification checks.
 */
@Component
internal class IdentityMediaEmailVerificationGate(
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy = permissiveEmailVerificationPolicy,
) : MediaEmailVerificationGate {

    override suspend fun requireVerified(principal: PrincipalContext, feature: MediaFeature) {
        requireEmailVerification(
            principal = principal,
            principalIdentityLookup = principalIdentityLookup,
            policy = emailVerificationPolicy,
            feature = feature.toIdentityFeature(),
        )
    }

    private fun MediaFeature.toIdentityFeature(): AuthFeature = when (this) {
        MediaFeature.UPLOAD_MEDIA -> AuthFeature.UPLOAD_MEDIA
    }
}
