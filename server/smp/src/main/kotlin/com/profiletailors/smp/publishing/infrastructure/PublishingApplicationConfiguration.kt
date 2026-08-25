package com.profiletailors.smp.publishing.infrastructure

import com.profiletailors.smp.publishing.application.DefaultProviderCatalogPolicy
import com.profiletailors.smp.publishing.application.PublishingMediaIntegrationSettings
import com.profiletailors.smp.publishing.domain.ConnectedSocialChannelReadRepository
import com.profiletailors.smp.publishing.domain.ProviderCatalogAvailability
import com.profiletailors.smp.publishing.domain.ProviderCatalogConnectionCounter
import com.profiletailors.smp.publishing.domain.ProviderCatalogPolicy
import com.profiletailors.smp.publishing.domain.ProviderWorkspaceCapacityPolicy
import com.profiletailors.smp.publishing.domain.ProviderWorkspaceEntitlementPolicy
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.linkedin.ConfigurableLinkedInAuthorizationUrlBuilder
import com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInPublishingProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PublishingApplicationConfiguration {
    @Bean
    fun publishingMediaIntegrationSettings(
        @Value("\${platform.media.context.integration.enabled:true}") enabled: Boolean,
    ): PublishingMediaIntegrationSettings = PublishingMediaIntegrationSettings(enabled = enabled)

    @Bean
    fun providerCatalogPolicy(
        linkedInPublishingProperties: LinkedInPublishingProperties,
        linkedInAuthorizationUrlBuilder: ConfigurableLinkedInAuthorizationUrlBuilder,
        connectedSocialChannelReadRepository: ConnectedSocialChannelReadRepository,
    ): ProviderCatalogPolicy = DefaultProviderCatalogPolicy(
        availability = ProviderCatalogAvailability { provider ->
            provider == SocialProvider.LINKEDIN &&
                linkedInPublishingProperties.isConfigured() &&
                linkedInAuthorizationUrlBuilder.isConfigured()
        },
        entitlementPolicy = ProviderWorkspaceEntitlementPolicy { _, _ -> true },
        capacityPolicy = ProviderWorkspaceCapacityPolicy { _, _ -> true },
        connectionCounter = ProviderCatalogConnectionCounter { provider, workspaceId ->
            connectedSocialChannelReadRepository.listByWorkspace(workspaceId)
                .count { it.provider == provider }
        },
    )
}
