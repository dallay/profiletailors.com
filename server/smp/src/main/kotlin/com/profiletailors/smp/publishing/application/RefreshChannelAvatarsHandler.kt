package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.publishing.domain.ChannelEvent
import com.profiletailors.smp.publishing.domain.ChannelEventPublisher
import com.profiletailors.smp.publishing.domain.ChannelEventType
import com.profiletailors.smp.publishing.domain.LinkedInAvatarFetcher
import com.profiletailors.smp.publishing.domain.ReconnectRequiredException
import com.profiletailors.smp.publishing.domain.RefreshAwareCredentialResolver
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext
import java.time.Clock

@Service
internal class RefreshChannelAvatarsHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val socialAccountRepository: SocialAccountRepository,
    private val credentialResolver: RefreshAwareCredentialResolver,
    private val avatarFetcher: LinkedInAvatarFetcher,
    private val channelEventPublisher: ChannelEventPublisher,
    private val clock: Clock,
) : CommandWithResultHandler<RefreshChannelAvatarsCommand, RefreshChannelAvatarsResult> {
    override suspend fun handle(command: RefreshChannelAvatarsCommand): RefreshChannelAvatarsResult {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val refreshed = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val failed = mutableListOf<String>()
        socialAccountRepository.listActiveByWorkspace(workspaceId)
            .filter { it.provider == SocialProvider.LINKEDIN }
            .forEach { account ->
                when (refreshAvatar(account)) {
                    RefreshOutcome.REFRESHED -> refreshed += account.id
                    RefreshOutcome.SKIPPED -> skipped += account.id
                    RefreshOutcome.FAILED -> failed += account.id
                }
            }
        return RefreshChannelAvatarsResult(
            refreshedAccountIds = refreshed,
            skippedAccountIds = skipped,
            failedAccountIds = failed,
        )
    }

    private suspend fun refreshAvatar(account: SocialAccount): RefreshOutcome {
        val accessToken = try {
            credentialResolver.resolve(account)
        } catch (_: ReconnectRequiredException) {
            return RefreshOutcome.SKIPPED
        }
        val freshAvatarUrl = avatarFetcher.fetchAvatarUrl(accessToken)?.trim()
        if (freshAvatarUrl.isNullOrBlank() || freshAvatarUrl == account.avatarUrl?.trim()) {
            return RefreshOutcome.SKIPPED
        }
        return try {
            socialAccountRepository.upsert(account.copy(avatarUrl = freshAvatarUrl))
            channelEventPublisher.publish(
                ChannelEvent(
                    type = ChannelEventType.CONNECTED_CHANNEL_UPDATED,
                    workspaceId = account.workspaceId,
                    socialAccountId = account.id,
                    occurredAt = clock.instant(),
                ),
            )
            RefreshOutcome.REFRESHED
        } catch (_: IllegalStateException) {
            RefreshOutcome.FAILED
        }
    }

    private enum class RefreshOutcome {
        REFRESHED,
        SKIPPED,
        FAILED,
    }
}
