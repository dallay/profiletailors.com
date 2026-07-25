package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.common.domain.context.PrincipalContextProvider
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.identity.application.AuthFeature
import com.profiletailors.smp.identity.application.EmailVerificationPolicy
import com.profiletailors.smp.identity.application.NoOpPrincipalIdentityLookup
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.application.permissiveEmailVerificationPolicy
import com.profiletailors.smp.identity.application.requireEmailVerification
import com.profiletailors.smp.publishing.domain.ChannelEvent
import com.profiletailors.smp.publishing.domain.ChannelEventPublisher
import com.profiletailors.smp.publishing.domain.ChannelEventType
import com.profiletailors.smp.publishing.domain.CompleteProviderConnectionCommand
import com.profiletailors.smp.publishing.domain.ConnectedSocialChannelReadRepository
import com.profiletailors.smp.publishing.domain.ExpiredOAuthStateException
import com.profiletailors.smp.publishing.domain.InvalidOAuthStateException
import com.profiletailors.smp.publishing.domain.LinkedInAuthorizationUrlBuilder
import com.profiletailors.smp.publishing.domain.LinkedInOAuthStatePayload
import com.profiletailors.smp.publishing.domain.OAuthStateSigner
import com.profiletailors.smp.publishing.domain.ProviderConnectionResult
import com.profiletailors.smp.publishing.domain.ProviderNotConfiguredException
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialConnection
import com.profiletailors.smp.publishing.domain.SocialConnectionProvider
import com.profiletailors.smp.publishing.domain.SocialConnectionRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext
import java.time.Clock
import java.util.UUID

data class PublishingMediaIntegrationSettings(val enabled: Boolean)

@Suppress("LongParameterList")
@Service
internal class InitiateLinkedInConnectionHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val oauthStateSigner: OAuthStateSigner,
    private val authorizationUrlBuilder: LinkedInAuthorizationUrlBuilder,
    private val clock: Clock,
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy = permissiveEmailVerificationPolicy,
) : CommandWithResultHandler<InitiateLinkedInConnectionCommand, LinkedInConnectionInitiationResult> {
    override suspend fun handle(command: InitiateLinkedInConnectionCommand): LinkedInConnectionInitiationResult {
        val principalCtx = principalContextProvider.require()
        requireEmailVerification(
            principalCtx,
            principalIdentityLookup,
            emailVerificationPolicy,
            AuthFeature.CONNECT_SOCIAL,
        )

        if (!authorizationUrlBuilder.isConfigured()) {
            throw ProviderNotConfiguredException(SocialProvider.LINKEDIN)
        }
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val issuedAt = clock.instant()
        val expiresAt = issuedAt.plus(STATE_TTL)
        val state = oauthStateSigner.sign(
            LinkedInOAuthStatePayload(
                provider = SocialProvider.LINKEDIN,
                workspaceId = workspaceId,
                principalId = principalCtx.principalId,
                redirectUri = command.redirectUri,
                nonce = UUID.randomUUID().toString(),
                issuedAt = issuedAt,
                expiresAt = expiresAt,
            ),
        )
        return LinkedInConnectionInitiationResult(
            authorizationUrl = authorizationUrlBuilder.buildAuthorizationUrl(
                state = state,
                redirectUri = command.redirectUri,
            ),
            state = state,
            expiresAt = expiresAt,
        )
    }

    private companion object {
        val STATE_TTL: java.time.Duration = java.time.Duration.ofMinutes(10)
    }
}

@Suppress("LongParameterList")
@Service
internal class CompleteLinkedInConnectionHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val socialConnectionProvider: SocialConnectionProvider,
    private val oauthStateSigner: OAuthStateSigner,
    private val socialConnectionRepository: SocialConnectionRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val channelEventPublisher: ChannelEventPublisher,
    private val clock: Clock,
    private val transactionRunner: AtomicTransactionRunner,
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy =
        permissiveEmailVerificationPolicy,
) : CommandWithResultHandler<CompleteLinkedInConnectionCommand, SocialConnectionResult> {
    override suspend fun handle(command: CompleteLinkedInConnectionCommand): SocialConnectionResult {
        val principalCtx = principalContextProvider.require()
        requireEmailVerification(
            principalCtx,
            principalIdentityLookup,
            emailVerificationPolicy,
            AuthFeature.CONNECT_SOCIAL,
        )
        val resourceContext = resourceContextProvider.requireWorkspaceContext()
        val workspaceId = requireNotNull(resourceContext.workspaceId)
        validateState(command, principalCtx.principalId, workspaceId)
        val providerResult = socialConnectionProvider.completeConnection(
            CompleteProviderConnectionCommand(
                workspaceId = workspaceId,
                actorPrincipalId = principalCtx.principalId,
                authorizationCode = command.authorizationCode,
                redirectUri = command.redirectUri,
            ),
        )

        val (connection, account) = persistConnectionAndAccount(workspaceId, providerResult)

        channelEventPublisher.publish(
            ChannelEvent(
                type = ChannelEventType.CONNECTED_CHANNEL_UPDATED,
                workspaceId = workspaceId,
                socialAccountId = account.id,
                occurredAt = clock.instant(),
            ),
        )

        return SocialConnectionResult(
            connectionId = connection.id,
            workspaceId = connection.workspaceId,
            provider = connection.provider,
            status = connection.status,
            account = account.toSocialAccountSummary(),
        )
    }

    private suspend fun persistConnectionAndAccount(
        workspaceId: String,
        providerResult: ProviderConnectionResult,
    ): Pair<SocialConnection, SocialAccount> = transactionRunner.runAtomically {
        val conn = socialConnectionRepository.upsert(
            SocialConnection(
                id = "soconn-${UUID.randomUUID()}",
                workspaceId = workspaceId,
                provider = SocialProvider.LINKEDIN,
                providerConnectionRef = providerResult.providerConnectionRef,
                status = SocialConnectionStatus.ACTIVE,
                credentialReference = providerResult.credentialReference,
                connectedAt = clock.instant(),
            ),
        )
        val acc = socialAccountRepository.upsert(
            SocialAccount(
                id = "soacc-${UUID.randomUUID()}",
                socialConnectionId = conn.id,
                workspaceId = workspaceId,
                provider = SocialProvider.LINKEDIN,
                providerAccountId = providerResult.account.providerAccountId,
                kind = providerResult.account.kind,
                displayName = providerResult.account.displayName,
                profileUrn = providerResult.account.profileUrn,
                avatarUrl = providerResult.account.avatarUrl,
                status = SocialConnectionStatus.ACTIVE,
            ),
        )
        conn to acc
    }

    private fun SocialAccount.toSocialAccountSummary() = SocialAccountSummary(
        accountId = id,
        providerAccountId = providerAccountId,
        displayName = displayName,
        kind = kind,
        profileUrn = profileUrn,
    )

    private fun validateState(command: CompleteLinkedInConnectionCommand, principalId: String, workspaceId: String) {
        val payload = oauthStateSigner.verify(command.state)
        if (!payload.expiresAt.isAfter(clock.instant())) {
            throw ExpiredOAuthStateException()
        }
        requireOAuthState(payload.provider == SocialProvider.LINKEDIN) {
            "OAuth state provider does not match LinkedIn."
        }
        requireOAuthState(payload.workspaceId == workspaceId) {
            "OAuth state workspace does not match the active workspace."
        }
        requireOAuthState(payload.principalId == principalId) {
            "OAuth state principal does not match the active principal."
        }
        requireOAuthState(payload.redirectUri == command.redirectUri) {
            "OAuth state redirect URI does not match the completion request."
        }
    }

    private fun requireOAuthState(condition: Boolean, message: () -> String) {
        if (!condition) {
            throw InvalidOAuthStateException(message())
        }
    }
}

@Service
internal class ListConnectedChannelsHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val connectedSocialChannelReadRepository: ConnectedSocialChannelReadRepository,
) : QueryHandler<ListConnectedChannelsQuery, ConnectedChannelsResponse> {
    override suspend fun handle(query: ListConnectedChannelsQuery): ConnectedChannelsResponse {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val statuses = query.status?.let { setOf(it) } ?: SocialConnectionStatus.entries.toSet()
        val channels = connectedSocialChannelReadRepository
            .listByWorkspace(workspaceId = workspaceId, statuses = statuses)
            .map { it.toSummary() }
        return ConnectedChannelsResponse(channels)
    }
}
