package com.profiletailors.smp.publishing.domain

import java.time.Instant

interface ConnectedSocialChannelReadRepository {
    suspend fun listByWorkspace(
        workspaceId: String,
        statuses: Set<SocialConnectionStatus> = setOf(SocialConnectionStatus.ACTIVE),
    ): List<ConnectedSocialChannel>
}

data class ConnectedSocialChannel(
    val socialAccountId: String,
    val connectionId: String,
    val provider: SocialProvider,
    val accountKind: SocialAccountKind,
    val displayName: String,
    val status: SocialConnectionStatus,
    val profileUrn: String?,
    val connectedAt: Instant?,
    val lastSyncedAt: Instant?,
)
