package com.profiletailors.smp.publishing.domain

data class CompleteProviderConnectionCommand(
    val workspaceId: String,
    val actorPrincipalId: String,
    val authorizationCode: String,
    val redirectUri: String,
)

data class ProviderConnectionResult(
    val provider: SocialProvider,
    val providerConnectionRef: String,
    val credentialReference: String?,
    val account: ProviderAccountProfile,
)

data class ProviderAccountProfile(
    val providerAccountId: String,
    val displayName: String,
    val kind: SocialAccountKind,
    val profileUrn: String? = null,
)

data class ProviderPublishCommand(
    val publicationId: String,
    val workspaceId: String,
    val socialAccount: SocialAccount,
    val publication: PublicationDraft,
    val assets: List<PublicationAsset>,
)

data class ProviderPublishResult(
    val externalPublicationId: String,
    val providerMessage: String? = null,
)

data class ProviderCapabilityValidationInput(
    val provider: SocialProvider,
    val socialAccount: SocialAccount,
    val publication: PublicationDraft,
    val assets: List<PublicationAsset>,
)

interface SocialConnectionProvider {
    suspend fun completeConnection(command: CompleteProviderConnectionCommand): ProviderConnectionResult
}

interface SocialPublisher {
    suspend fun publish(command: ProviderPublishCommand): ProviderPublishResult
}

interface ProviderCapabilityValidator {
    fun validate(input: ProviderCapabilityValidationInput)
}
