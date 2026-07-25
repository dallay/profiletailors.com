package com.profiletailors.smp.media.application

import com.profiletailors.common.domain.context.PrincipalContext

/**
 * Media-local port used by application handlers to enforce email verification
 * without importing identity internals directly.
 */
interface MediaEmailVerificationGate {
    suspend fun requireVerified(principal: PrincipalContext, feature: MediaFeature)
}

enum class MediaFeature {
    UPLOAD_MEDIA,
}

object NoOpMediaEmailVerificationGate : MediaEmailVerificationGate {
    override suspend fun requireVerified(principal: PrincipalContext, feature: MediaFeature) {
        // Intentionally permissive default for tests and explicit wiring phases.
    }
}