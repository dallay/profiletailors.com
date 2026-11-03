package com.profiletailors.smp.identity.application

import java.time.Instant

fun interface BootstrapVerificationCompletionPort {
    suspend fun completeVerifiedPrincipal(principalId: String, email: String, verifiedAt: Instant)
}

val NoOpBootstrapVerificationCompletionPort: BootstrapVerificationCompletionPort =
    BootstrapVerificationCompletionPort { _, _, _ -> }
