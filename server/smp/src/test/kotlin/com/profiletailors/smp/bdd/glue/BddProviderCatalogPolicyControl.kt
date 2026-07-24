package com.profiletailors.smp.bdd.glue

import com.profiletailors.smp.publishing.domain.ConnectedSocialChannelReadRepository
import com.profiletailors.smp.publishing.domain.ProviderCatalogItem
import com.profiletailors.smp.publishing.domain.ProviderCatalogPolicy
import com.profiletailors.smp.publishing.domain.ProviderCatalogState
import com.profiletailors.smp.publishing.domain.ProviderLockReason
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialProvider
import java.util.concurrent.ConcurrentHashMap

class BddProviderCatalogPolicyControl(
    private val connectedSocialChannelReadRepository: ConnectedSocialChannelReadRepository,
) : ProviderCatalogPolicy {
    private val linkedInStateByWorkspace = ConcurrentHashMap<String, LinkedInProviderPolicyState>()

    fun reset() {
        linkedInStateByWorkspace.clear()
    }

    fun setAvailable(workspaceId: String) {
        linkedInStateByWorkspace[workspaceId] = LinkedInProviderPolicyState.AVAILABLE
    }

    fun setEntitlementLocked(workspaceId: String) {
        linkedInStateByWorkspace[workspaceId] = LinkedInProviderPolicyState.NOT_ENTITLED
    }

    fun setCapacityLocked(workspaceId: String) {
        linkedInStateByWorkspace[workspaceId] = LinkedInProviderPolicyState.CAPACITY_REACHED
    }

    fun setHidden(workspaceId: String) {
        linkedInStateByWorkspace[workspaceId] = LinkedInProviderPolicyState.HIDDEN
    }

    override suspend fun evaluate(provider: SocialProvider, workspaceId: String): ProviderCatalogItem {
        val policyState = if (provider == SocialProvider.LINKEDIN) {
            linkedInStateByWorkspace[workspaceId] ?: LinkedInProviderPolicyState.AVAILABLE
        } else {
            LinkedInProviderPolicyState.HIDDEN
        }
        val connectedChannelCount = connectedSocialChannelReadRepository.listByWorkspace(workspaceId)
            .count { it.provider == provider }

        return ProviderCatalogItem(
            provider = provider,
            accountKinds = setOf(SocialAccountKind.PERSONAL_PROFILE.name),
            state = policyState.catalogState,
            reason = policyState.reason,
            channelLimit = null,
            connectedChannelCount = connectedChannelCount,
            canConnectMore = policyState != LinkedInProviderPolicyState.CAPACITY_REACHED,
        )
    }

    private enum class LinkedInProviderPolicyState(
        val catalogState: ProviderCatalogState,
        val reason: ProviderLockReason?,
    ) {
        AVAILABLE(ProviderCatalogState.AVAILABLE, null),
        NOT_ENTITLED(ProviderCatalogState.LOCKED, ProviderLockReason.NOT_ENTITLED),
        CAPACITY_REACHED(ProviderCatalogState.LOCKED, ProviderLockReason.CAPACITY_REACHED),
        HIDDEN(ProviderCatalogState.HIDDEN, null),
    }
}
