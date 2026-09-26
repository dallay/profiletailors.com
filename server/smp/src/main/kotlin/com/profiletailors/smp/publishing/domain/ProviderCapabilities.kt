package com.profiletailors.smp.publishing.domain

import com.profiletailors.common.domain.ValueObject

@ValueObject
enum class PublishingCapability {
    TEXT,
    SINGLE_IMAGE,
    SINGLE_VIDEO,
    MIXED_MEDIA_CAROUSEL,
}

@ValueObject
data class ProviderCapabilitySet(
    val accountKinds: Set<SocialAccountKind>,
    val supportedCapabilities: Set<PublishingCapability>,
    val carouselItemRange: IntRange,
) {
    init {
        require(accountKinds.isNotEmpty()) { "At least one account kind is required." }
        require(supportedCapabilities.isNotEmpty()) { "At least one publishing capability is required." }
        require(!carouselItemRange.isEmpty()) { "Carousel item range must not be empty." }
    }
}

class ProviderCapabilityRegistry private constructor(
    private val capabilitiesByProvider: Map<SocialProvider, ProviderCapabilitySet>,
    private val validatorsByProvider: Map<SocialProvider, ProviderCapabilityValidator> = emptyMap(),
) {
    fun capabilities(provider: SocialProvider): ProviderCapabilitySet? = capabilitiesByProvider[provider]
    fun validator(provider: SocialProvider): ProviderCapabilityValidator? = validatorsByProvider[provider]

    companion object {
        fun from(vararg entries: Pair<SocialProvider, ProviderCapabilitySet>): ProviderCapabilityRegistry =
            ProviderCapabilityRegistry(entries.toMap())

        fun fromValidators(
            vararg entries: Pair<SocialProvider, ProviderCapabilityValidator>,
        ): ProviderCapabilityRegistry = ProviderCapabilityRegistry(emptyMap(), entries.toMap())

        fun from(
            capabilities: Map<SocialProvider, ProviderCapabilitySet>,
            validators: Map<SocialProvider, ProviderCapabilityValidator>,
        ): ProviderCapabilityRegistry = ProviderCapabilityRegistry(capabilities, validators)
    }
}

class ProviderConnectionRegistry private constructor(
    private val providers: Map<SocialProvider, SocialConnectionProvider>,
) {
    fun provider(provider: SocialProvider): SocialConnectionProvider? = providers[provider]
    fun requireProvider(provider: SocialProvider): SocialConnectionProvider =
        provider(provider) ?: error("No connection provider is registered for $provider.")

    companion object {
        fun from(vararg entries: Pair<SocialProvider, SocialConnectionProvider>): ProviderConnectionRegistry =
            ProviderConnectionRegistry(entries.toMap())
    }
}

class ProviderPublishingRegistry private constructor(private val publishers: Map<SocialProvider, SocialPublisher>) {
    fun publisher(provider: SocialProvider): SocialPublisher? = publishers[provider]

    companion object {
        fun from(vararg entries: Pair<SocialProvider, SocialPublisher>): ProviderPublishingRegistry =
            ProviderPublishingRegistry(entries.toMap())
    }
}
