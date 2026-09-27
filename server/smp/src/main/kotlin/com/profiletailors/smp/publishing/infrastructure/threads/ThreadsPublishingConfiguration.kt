package com.profiletailors.smp.publishing.infrastructure.threads

import com.fasterxml.jackson.databind.ObjectMapper
import com.profiletailors.smp.publishing.domain.OAuthAuthorizationUrlBuilder
import com.profiletailors.smp.publishing.domain.ProviderAuthorizationRegistry
import com.profiletailors.smp.publishing.domain.ProviderCapabilityRegistry
import com.profiletailors.smp.publishing.domain.ProviderConnectionRegistry
import com.profiletailors.smp.publishing.domain.ProviderPublishingRegistry
import com.profiletailors.smp.publishing.domain.RefreshAwareCredentialResolver
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.credentials.ProviderCredentialGateway
import com.profiletailors.smp.publishing.infrastructure.linkedin.LinkedInHttpTransport
import com.profiletailors.smp.publishing.infrastructure.linkedin.RealLinkedInConnectionProvider
import com.profiletailors.storage.domain.AttachmentsStorageBinding
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
@EnableConfigurationProperties(ThreadsPublishingProperties::class)
class ThreadsPublishingConfiguration {
    @Bean
    @Qualifier("threadsAuthorizationUrlBuilder")
    fun threadsAuthorizationUrlBuilder(properties: ThreadsPublishingProperties): OAuthAuthorizationUrlBuilder =
        ThreadsAuthorizationUrlBuilder(properties)

    @Bean
    fun providerAuthorizationRegistry(
        linkedInAuthorizationUrlBuilder: com.profiletailors.smp.publishing.domain.LinkedInAuthorizationUrlBuilder,
        @Qualifier("threadsAuthorizationUrlBuilder")
        threadsAuthorizationUrlBuilder: OAuthAuthorizationUrlBuilder,
    ): ProviderAuthorizationRegistry = ProviderAuthorizationRegistry.from(
        SocialProvider.LINKEDIN to linkedInAuthorizationUrlBuilder,
        SocialProvider.THREADS to threadsAuthorizationUrlBuilder,
    )

    @Bean
    fun threadsConnectionProvider(
        properties: ThreadsPublishingProperties,
        objectMapper: ObjectMapper,
        linkedInHttpTransport: LinkedInHttpTransport,
        credentialGateway: ProviderCredentialGateway,
        clock: Clock,
    ): ThreadsConnectionProvider = ThreadsConnectionProvider(
        properties,
        objectMapper,
        linkedInHttpTransport,
        credentialGateway,
        clock,
    )

    @Bean
    fun providerConnectionRegistry(
        socialConnectionProvider: RealLinkedInConnectionProvider,
        threadsConnectionProvider: ThreadsConnectionProvider,
    ): ProviderConnectionRegistry = ProviderConnectionRegistry.from(
        SocialProvider.LINKEDIN to socialConnectionProvider,
        SocialProvider.THREADS to threadsConnectionProvider,
    )

    @Bean
    fun threadsMediaUrlResolver(
        properties: ThreadsPublishingProperties,
        attachmentsStorageBinding: AttachmentsStorageBinding,
        clock: Clock,
    ): ThreadsProviderMediaUrlResolver = ThreadsProviderMediaUrlResolver(properties, attachmentsStorageBinding, clock)

    @Bean
    fun threadsPublisher(
        properties: ThreadsPublishingProperties,
        objectMapper: ObjectMapper,
        linkedInHttpTransport: LinkedInHttpTransport,
        credentialResolver: RefreshAwareCredentialResolver,
        threadsMediaUrlResolver: ThreadsProviderMediaUrlResolver,
        clock: Clock,
    ): ThreadsPublishingAdapter = ThreadsPublishingAdapter(
        properties,
        objectMapper,
        linkedInHttpTransport,
        credentialResolver,
        threadsMediaUrlResolver,
        clock,
    )

    @Bean
    fun providerPublishingRegistry(
        socialPublisher: com.profiletailors.smp.publishing.domain.SocialPublisher,
        threadsPublisher: ThreadsPublishingAdapter,
    ): ProviderPublishingRegistry = ProviderPublishingRegistry.from(
        SocialProvider.LINKEDIN to socialPublisher,
        SocialProvider.THREADS to threadsPublisher,
    )

    @Bean
    fun providerCapabilityRegistry(): ProviderCapabilityRegistry = ProviderCapabilityRegistry.from(
        mapOf(SocialProvider.THREADS to ThreadsCapabilitySet.default()),
        mapOf(SocialProvider.THREADS to ThreadsCapabilityValidator()),
    )
}
