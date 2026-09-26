package com.profiletailors.smp.publishing.infrastructure.threads

import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidationInput
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.PublicationAsset
import com.profiletailors.smp.publishing.domain.PublicationValidationException
import com.profiletailors.smp.publishing.domain.PublishingCapability
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialProvider

class ThreadsCapabilityValidator : ProviderCapabilityValidator {
    override fun validate(input: ProviderCapabilityValidationInput) {
        require(input.provider == SocialProvider.THREADS) {
            "Threads capability validator only supports THREADS."
        }
        require(input.socialAccount.kind == SocialAccountKind.PERSONAL_PROFILE) {
            "Threads publishing supports personal profiles only."
        }
        val capability = capability(input)
        validateCapability(capability)
        validateCarouselSize(capability, input.assets.size)
        validateMedia(input.assets)
    }

    private fun validateCapability(capability: PublishingCapability) {
        if (capability !in ThreadsCapabilitySet.default().supportedCapabilities) {
            throw PublicationValidationException("Unsupported Threads publication content.")
        }
    }

    private fun validateCarouselSize(capability: PublishingCapability, assetCount: Int) {
        if (capability == PublishingCapability.MIXED_MEDIA_CAROUSEL &&
            assetCount !in ThreadsCapabilitySet.default().carouselItemRange
        ) {
            throw PublicationValidationException("Threads carousels must contain 2 to 20 assets.")
        }
    }

    private fun validateMedia(assets: List<PublicationAsset>) {
        if (assets.any { it.mediaType.lowercase() !in SUPPORTED_MEDIA_TYPES }) {
            throw PublicationValidationException("Unsupported Threads media type.")
        }
        if (assets.any { it.status.name != "READY" }) {
            throw PublicationValidationException("Threads media must be ready before publishing.")
        }
    }

    private fun capability(input: ProviderCapabilityValidationInput): PublishingCapability = when {
        input.assets.isEmpty() && !input.publication.bodyText.isNullOrBlank() -> PublishingCapability.TEXT
        input.assets.size == 1 && input.assets.single().mediaType.lowercase().startsWith("image/") ->
            PublishingCapability.SINGLE_IMAGE
        input.assets.size == 1 && input.assets.single().mediaType.lowercase().startsWith("video/") ->
            PublishingCapability.SINGLE_VIDEO
        input.assets.size in ThreadsCapabilitySet.default().carouselItemRange ->
            PublishingCapability.MIXED_MEDIA_CAROUSEL
        else -> throw PublicationValidationException("Unsupported Threads publication content.")
    }

    private companion object {
        val SUPPORTED_MEDIA_TYPES = setOf("image/jpeg", "image/png", "video/mp4")
    }
}
