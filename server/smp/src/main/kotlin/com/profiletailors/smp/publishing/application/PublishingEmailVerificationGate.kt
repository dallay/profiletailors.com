package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.context.PrincipalContext

/**
 * Publishing-local port used by application handlers to enforce email verification
 * without importing identity internals directly.
 */
interface PublishingEmailVerificationGate {
    suspend fun requireVerified(principal: PrincipalContext, feature: PublishingFeature)
}

enum class PublishingFeature {
    PUBLISH_CONTENT,
    SCHEDULE_POST,
    CONNECT_SOCIAL,
}

object NoOpPublishingEmailVerificationGate : PublishingEmailVerificationGate {
    override suspend fun requireVerified(principal: PrincipalContext, feature: PublishingFeature) {
        // Intentionally permissive default for tests and explicit wiring phases.
    }
}
