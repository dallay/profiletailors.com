package com.profiletailors.smp.identity.application

import java.time.Instant

fun interface BootstrapVerificationCompletionPort {
    suspend fun completeVerifiedPrincipal(principalId: String, email: String, verifiedAt: Instant)
}

object NoOpBootstrapVerificationCompletionPort : BootstrapVerificationCompletionPort {
    override suspend fun completeVerifiedPrincipal(principalId: String, email: String, verifiedAt: Instant) = Unit
}
