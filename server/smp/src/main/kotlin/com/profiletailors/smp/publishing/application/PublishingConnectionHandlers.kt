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
import com.profiletailors.smp.publishing.domain.ConnectedSocialChannelReadRepository
import com.profiletailors.smp.publishing.domain.ExpiredOAuthStateException
import com.profiletailors.smp.publishing.domain.InvalidOAuthStateException
import com.profiletailors.smp.publishing.domain.LinkedInAuthorizationUrlBuilder
import com.profiletailors.smp.publishing.domain.LinkedInOAuthStatePayload
import com.profiletailors.smp.publishing.domain.OAuthStatePayload
import com.profiletailors.smp.publishing.domain.OAuthStateSigner
import com.profiletailors.smp.publishing.domain.ProviderAuthorizationRegistry
import com.profiletailors.smp.publishing.domain.ProviderCatalogPolicy
import com.profiletailors.smp.publishing.domain.ProviderConnectionRegistry
import com.profiletailors.smp.publishing.domain.ProviderConnectionResult
import com.profiletailors.smp.publishing.domain.ProviderCredentialInvalidator
import com.profiletailors.smp.publishing.domain.ProviderNotConfiguredException
import com.profiletailors.smp.publishing.domain.SocialAccount
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialAccountRepository
import com.profiletailors.smp.publishing.domain.SocialConnection
import com.profiletailors.smp.publishing.domain.SocialConnectionProvider
import com.profiletailors.smp.publishing.domain.SocialConnectionRepository
import com.profiletailors.smp.publishing.domain.SocialConnectionStatus
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.domain.permissiveProviderCatalogPolicy
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext
import java.time.Clock
import java.util.UUID
import com.profiletailors.smp.publishing.domain.CompleteProviderConnectionCommand as ProviderCompletion

data class PublishingMediaIntegrationSettings(val enabled: Boolean)

@Suppress("LongParameterList")
@Service
internal class InitiateProviderConnectionHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val oauthStateSigner: OAuthStateSigner,
    private val authorizationRegistry: ProviderAuthorizationRegistry,
    private val clock: Clock,
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy = permissiveEmailVerificationPolicy,
    private val providerCatalogPolicy: ProviderCatalogPolicy = permissiveProviderCatalogPolicy,
) : CommandWithResultHandler<InitiateProviderConnectionCommand, ProviderConnectionInitiationResult> {
    override suspend fun handle(command: InitiateProviderConnectionCommand): ProviderConnectionInitiationResult {
        val principalCtx = principalContextProvider.require()
        requireEmailVerification(
            principalCtx,
            principalIdentityLookup,
            emailVerificationPolicy,
            AuthFeature.CONNECT_SOCIAL,
        )
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        providerCatalogPolicy.requireAvailable(command.provider, workspaceId)
        val builder = authorizationRegistry.builder(command.provider)
            ?: throw ProviderNotConfiguredException(command.provider)
        if (!builder.isConfigured()) throw ProviderNotConfiguredException(command.provider)
        if (!builder.isAllowedRedirectUri(command.redirectUri)) {
            throw InvalidOAuthStateException("OAuth redirect URI is not allowed.")
        }
        val issuedAt = clock.instant()
        val expiresAt = issuedAt.plus(STATE_TTL)
        val state = oauthStateSigner.sign(
            OAuthStatePayload(
                provider = command.provider,
                workspaceId = workspaceId,
                principalId = principalCtx.principalId,
                redirectUri = command.redirectUri,
                nonce = UUID.randomUUID().toString(),
                issuedAt = issuedAt,
                expiresAt = expiresAt,
            ),
        )
        return ProviderConnectionInitiationResult(
            builder.buildAuthorizationUrl(state, command.redirectUri),
            state,
            expiresAt,
        )
    }

    private companion object {
        val STATE_TTL: java.time.Duration = java.time.Duration.ofMinutes(10)
    }
}

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
    private val providerCatalogPolicy: ProviderCatalogPolicy = permissiveProviderCatalogPolicy,
) : CommandWithResultHandler<InitiateLinkedInConnectionCommand, LinkedInConnectionInitiationResult> {
    override suspend fun handle(command: InitiateLinkedInConnectionCommand): LinkedInConnectionInitiationResult {
        val principalCtx = principalContextProvider.require()
        requireEmailVerification(
            principalCtx,
            principalIdentityLookup,
            emailVerificationPolicy,
            AuthFeature.CONNECT_SOCIAL,
        )

        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        providerCatalogPolicy.requireAvailable(SocialProvider.LINKEDIN, workspaceId)
        if (!authorizationUrlBuilder.isConfigured()) {
            throw ProviderNotConfiguredException(SocialProvider.LINKEDIN)
        }
        if (!authorizationUrlBuilder.isAllowedRedirectUri(command.redirectUri)) {
            throw InvalidOAuthStateException("LinkedIn initiation redirect URI is not allowed.")
        }
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
internal class CompleteProviderConnectionHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val connectionRegistry: ProviderConnectionRegistry,
    private val oauthStateSigner: OAuthStateSigner,
    private val authorizationRegistry: ProviderAuthorizationRegistry,
    private val socialConnectionRepository: SocialConnectionRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val channelEventPublisher: ChannelEventPublisher,
    private val clock: Clock,
    private val transactionRunner: AtomicTransactionRunner,
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy = permissiveEmailVerificationPolicy,
) : CommandWithResultHandler<
    CompleteProviderConnectionCommand,
    SocialConnectionResult,
    > {
    override suspend fun handle(command: CompleteProviderConnectionCommand): SocialConnectionResult {
        val principalCtx = principalContextProvider.require()
        requireEmailVerification(
            principalCtx,
            principalIdentityLookup,
            emailVerificationPolicy,
            AuthFeature.CONNECT_SOCIAL,
        )
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val payload = oauthStateSigner.verify(command.state)
        validateState(command, payload, workspaceId, principalCtx.principalId)
        val builder = authorizationRegistry.builder(command.provider)
            ?: throw ProviderNotConfiguredException(command.provider)
        requireOAuthState(builder.isAllowedRedirectUri(command.redirectUri)) {
            "OAuth redirect URI is not allowed."
        }
        oauthStateSigner.consume(payload)
        val result = connectionRegistry.requireProvider(command.provider).completeConnection(
            ProviderCompletion(
                workspaceId,
                principalCtx.principalId,
                command.authorizationCode,
                command.redirectUri,
            ),
        )
        return persistProviderResult(workspaceId, result)
    }

    private fun validateState(
        command: CompleteProviderConnectionCommand,
        payload: OAuthStatePayload,
        workspaceId: String,
        principalId: String,
    ) {
        if (!payload.expiresAt.isAfter(clock.instant())) throw ExpiredOAuthStateException()
        requireOAuthState(payload.provider == command.provider) {
            "OAuth state provider does not match the requested provider."
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

    private suspend fun persistProviderResult(
        workspaceId: String,
        result: ProviderConnectionResult,
    ): SocialConnectionResult {
        val (connection, account) = transactionRunner.runAtomically {
            val savedConnection = socialConnectionRepository.upsert(
                SocialConnection(
                    "soconn-${UUID.randomUUID()}",
                    workspaceId,
                    result.provider,
                    result.providerConnectionRef,
                    SocialConnectionStatus.ACTIVE,
                    result.credentialReference,
                    clock.instant(),
                ),
            )
            val savedAccount = socialAccountRepository.upsert(
                SocialAccount(
                    "soacc-${UUID.randomUUID()}",
                    savedConnection.id,
                    workspaceId,
                    result.provider,
                    result.account.providerAccountId,
                    result.account.kind,
                    result.account.displayName,
                    result.account.profileUrn,
                    result.account.avatarUrl,
                    SocialConnectionStatus.ACTIVE,
                ),
            )
            savedConnection to savedAccount
        }
        channelEventPublisher.publish(
            ChannelEvent(
                ChannelEventType.CONNECTED_CHANNEL_UPDATED,
                workspaceId,
                account.id,
                clock.instant(),
            ),
        )
        return SocialConnectionResult(
            connection.id,
            connection.workspaceId,
            connection.provider,
            connection.status,
            account.toSocialAccountSummary(),
        )
    }

    private fun requireOAuthState(condition: Boolean, message: () -> String) {
        if (!condition) throw InvalidOAuthStateException(message())
    }

    private fun SocialAccount.toSocialAccountSummary() = SocialAccountSummary(
        id,
        providerAccountId,
        displayName,
        kind,
        profileUrn,
    )
}

@Suppress("LongParameterList")
@Service
internal class CompleteLinkedInConnectionHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val socialConnectionProvider: SocialConnectionProvider,
    private val oauthStateSigner: OAuthStateSigner,
    private val authorizationUrlBuilder: LinkedInAuthorizationUrlBuilder,
    private val socialConnectionRepository: SocialConnectionRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val channelEventPublisher: ChannelEventPublisher,
    private val clock: Clock,
    private val transactionRunner: AtomicTransactionRunner,
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy =
        permissiveEmailVerificationPolicy,
) : CommandWithResultHandler<CompleteLinkedInConnectionCommand, SocialConnectionResult> {
    private val delegate = CompleteProviderConnectionHandler(
        principalContextProvider = principalContextProvider,
        resourceContextProvider = resourceContextProvider,
        connectionRegistry = ProviderConnectionRegistry.from(SocialProvider.LINKEDIN to socialConnectionProvider),
        oauthStateSigner = oauthStateSigner,
        authorizationRegistry = ProviderAuthorizationRegistry.from(SocialProvider.LINKEDIN to authorizationUrlBuilder),
        socialConnectionRepository = socialConnectionRepository,
        socialAccountRepository = socialAccountRepository,
        channelEventPublisher = channelEventPublisher,
        clock = clock,
        transactionRunner = transactionRunner,
        principalIdentityLookup = principalIdentityLookup,
        emailVerificationPolicy = emailVerificationPolicy,
    )

    override suspend fun handle(command: CompleteLinkedInConnectionCommand): SocialConnectionResult = delegate.handle(
        CompleteProviderConnectionCommand(
            provider = SocialProvider.LINKEDIN,
            authorizationCode = command.authorizationCode,
            redirectUri = command.redirectUri,
            state = command.state,
        ),
    )
}

@Service
internal class DisconnectProviderConnectionHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val socialConnectionRepository: SocialConnectionRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val credentialGateway: ProviderCredentialInvalidator,
    private val channelEventPublisher: ChannelEventPublisher,
    private val transactionRunner: AtomicTransactionRunner,
    private val clock: Clock,
) : CommandWithResultHandler<DisconnectProviderConnectionCommand, SocialConnectionResult> {
    override suspend fun handle(command: DisconnectProviderConnectionCommand): SocialConnectionResult {
        principalContextProvider.require()
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val connection = socialConnectionRepository.findByWorkspaceAndId(workspaceId, command.connectionId)
            ?: throw IllegalArgumentException("Publishing connection was not found.")
        require(connection.provider == command.provider) { "Publishing provider does not match the connection." }
        transactionRunner.runAtomically {
            socialAccountRepository.deleteByConnectionId(connection.id)
            socialConnectionRepository.deleteByWorkspaceAndId(workspaceId, connection.id)
            connection.credentialReference?.let { reference ->
                if (!socialConnectionRepository.existsByCredentialReference(reference)) {
                    credentialGateway.invalidateCredential(UUID.fromString(reference))
                }
            }
            Unit
        }
        channelEventPublisher.publish(
            ChannelEvent(
                type = ChannelEventType.CONNECTED_CHANNEL_REMOVED,
                workspaceId = workspaceId,
                socialAccountId = null,
                occurredAt = clock.instant(),
            ),
        )
        return SocialConnectionResult(
            connectionId = connection.id,
            workspaceId = workspaceId,
            provider = connection.provider,
            status = SocialConnectionStatus.DELETED,
            account = SocialAccountSummary(
                accountId = "",
                providerAccountId = connection.providerConnectionRef,
                displayName = "",
                kind = SocialAccountKind.PERSONAL_PROFILE,
                profileUrn = null,
            ),
        )
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
