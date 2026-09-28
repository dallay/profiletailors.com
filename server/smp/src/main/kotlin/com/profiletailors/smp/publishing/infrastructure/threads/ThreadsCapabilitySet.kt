package com.profiletailors.smp.publishing.infrastructure.threads

import com.profiletailors.smp.publishing.domain.ProviderCapabilitySet
import com.profiletailors.smp.publishing.domain.PublishingCapability
import com.profiletailors.smp.publishing.domain.SocialAccountKind

object ThreadsCapabilitySet {
    fun default(): ProviderCapabilitySet = ProviderCapabilitySet(
        accountKinds = setOf(SocialAccountKind.PERSONAL_PROFILE),
        supportedCapabilities = setOf(
            PublishingCapability.TEXT,
            PublishingCapability.SINGLE_IMAGE,
            PublishingCapability.SINGLE_VIDEO,
            PublishingCapability.MIXED_MEDIA_CAROUSEL,
        ),
        carouselItemRange = 2..20,
    )
}
