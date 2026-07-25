package com.profiletailors.smp.publishing.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.common.domain.bus.query.QueryHandler
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.smp.publishing.domain.ProviderCatalogAvailability
import com.profiletailors.smp.publishing.domain.ProviderCatalogConnectionCounter
import com.profiletailors.smp.publishing.domain.ProviderCatalogItem
import com.profiletailors.smp.publishing.domain.ProviderCatalogPolicy
import com.profiletailors.smp.publishing.domain.ProviderCatalogState
import com.profiletailors.smp.publishing.domain.ProviderConnectionNotAvailableException
import com.profiletailors.smp.publishing.domain.ProviderLockReason
import com.profiletailors.smp.publishing.domain.ProviderWorkspaceCapacityPolicy
import com.profiletailors.smp.publishing.domain.ProviderWorkspaceEntitlementPolicy
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialProvider

data object ListProviderCatalogQuery : Query<ProviderCatalogResponse>

data class ProviderCatalogResponse(val providers: List<ProviderCatalogItem>)

@Service
internal class ListProviderCatalogHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val providerCatalogPolicy: ProviderCatalogPolicy,
) : QueryHandler<ListProviderCatalogQuery, ProviderCatalogResponse> {
    override suspend fun handle(query: ListProviderCatalogQuery): ProviderCatalogResponse {
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        return ProviderCatalogResponse(
            SocialProvider.entries
                .map { providerCatalogPolicy.evaluate(it, workspaceId) }
                .filterNot { it.state == ProviderCatalogState.HIDDEN },
        )
    }
}

@Suppress("LongParameterList")
class DefaultProviderCatalogPolicy(
    private val availability: ProviderCatalogAvailability,
    private val entitlementPolicy: ProviderWorkspaceEntitlementPolicy,
    private val capacityPolicy: ProviderWorkspaceCapacityPolicy,
    private val connectionCounter: ProviderCatalogConnectionCounter,
) : ProviderCatalogPolicy {
    override suspend fun evaluate(provider: SocialProvider, workspaceId: String): ProviderCatalogItem {
        val connectedChannelCount = connectionCounter.count(provider, workspaceId)
        val canConnectMore = capacityPolicy.canConnect(provider, workspaceId)
        val stateAndReason = when {
            !availability.isAvailable(provider) -> ProviderCatalogState.HIDDEN to null
            !entitlementPolicy.isEntitled(provider, workspaceId) ->
                ProviderCatalogState.LOCKED to ProviderLockReason.NOT_ENTITLED
            !canConnectMore -> ProviderCatalogState.LOCKED to ProviderLockReason.CAPACITY_REACHED
            else -> ProviderCatalogState.AVAILABLE to null
        }
        return ProviderCatalogItem(
            provider = provider,
            accountKinds = setOf(SocialAccountKind.PERSONAL_PROFILE.name),
            state = stateAndReason.first,
            reason = stateAndReason.second,
            channelLimit = null,
            connectedChannelCount = connectedChannelCount,
            canConnectMore = canConnectMore,
        )
    }
}

suspend fun ProviderCatalogPolicy.requireAvailable(provider: SocialProvider, workspaceId: String) {
    val item = evaluate(provider, workspaceId)
    if (item.state != ProviderCatalogState.AVAILABLE) {
        throw ProviderConnectionNotAvailableException(provider, item.state, item.reason)
    }
}
