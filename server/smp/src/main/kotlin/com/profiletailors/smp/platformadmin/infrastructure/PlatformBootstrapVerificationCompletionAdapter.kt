package com.profiletailors.smp.platformadmin.infrastructure

import com.profiletailors.smp.identity.application.BootstrapVerificationCompletionPort
import com.profiletailors.smp.platformadmin.application.CompleteBootstrapOwnerGrantHandler
import org.springframework.stereotype.Component
import java.time.Instant

@Component
internal class PlatformBootstrapVerificationCompletionAdapter(
    private val completeBootstrapOwnerGrantHandler: CompleteBootstrapOwnerGrantHandler,
) : BootstrapVerificationCompletionPort {
    override suspend fun completeVerifiedPrincipal(principalId: String, email: String, verifiedAt: Instant) {
        completeBootstrapOwnerGrantHandler.handle(principalId, email, verifiedAt)
    }
}
