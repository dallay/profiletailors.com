package com.profiletailors.smp.publishing.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant

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
    val avatarUrl: String? = null,
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

fun interface SocialConnectionProvider {
    suspend fun completeConnection(command: CompleteProviderConnectionCommand): ProviderConnectionResult
}

fun interface SocialPublisher {
    suspend fun publish(command: ProviderPublishCommand): ProviderPublishResult
}

fun interface ProviderCapabilityValidator {
    fun validate(input: ProviderCapabilityValidationInput)
}

fun interface AssetUploader {
    suspend fun uploadAsset(
        asset: PublicationAsset,
        content: Flow<ByteArray>,
        context: AssetUploadContext,
    ): ProviderAssetRef
}

data class AssetUploadContext(
    val socialAccount: SocialAccount,
    val accessToken: String,
    val apiBaseUrl: String,
    val apiVersion: String,
)

class ProviderUploadException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
