package com.profiletailors.smp.publishing.domain

enum class ProviderCatalogState {
    AVAILABLE,
    LOCKED,
    HIDDEN,
}

enum class ProviderLockReason {
    NOT_ENTITLED,
    CAPACITY_REACHED,
}

data class ProviderCatalogItem(
    val provider: SocialProvider,
    val accountKinds: Set<String>,
    val state: ProviderCatalogState,
    val reason: ProviderLockReason?,
    val channelLimit: Int?,
    val connectedChannelCount: Int,
    val canConnectMore: Boolean,
)

fun interface ProviderCatalogPolicy {
    suspend fun evaluate(provider: SocialProvider, workspaceId: String): ProviderCatalogItem
}

fun interface ProviderCatalogAvailability {
    fun isAvailable(provider: SocialProvider): Boolean
}

fun interface ProviderWorkspaceEntitlementPolicy {
    fun isEntitled(provider: SocialProvider, workspaceId: String): Boolean
}

fun interface ProviderWorkspaceCapacityPolicy {
    fun canConnect(provider: SocialProvider, workspaceId: String): Boolean
}

fun interface ProviderCatalogConnectionCounter {
    suspend fun count(provider: SocialProvider, workspaceId: String): Int
}

class ProviderConnectionNotAvailableException(
    val provider: SocialProvider,
    val state: ProviderCatalogState,
    val reason: ProviderLockReason?,
) : IllegalStateException("Provider '$provider' is not available for a new connection.")

val permissiveProviderCatalogPolicy = ProviderCatalogPolicy { provider, _ ->
    ProviderCatalogItem(
        provider = provider,
        accountKinds = setOf(SocialAccountKind.PERSONAL_PROFILE.name),
        state = ProviderCatalogState.AVAILABLE,
        reason = null,
        channelLimit = null,
        connectedChannelCount = 0,
        canConnectMore = true,
    )
}
