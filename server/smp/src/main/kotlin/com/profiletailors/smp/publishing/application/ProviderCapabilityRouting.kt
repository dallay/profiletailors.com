package com.profiletailors.smp.publishing.application

import com.profiletailors.smp.publishing.domain.ProviderCapabilityRegistry
import com.profiletailors.smp.publishing.domain.ProviderCapabilityValidator
import com.profiletailors.smp.publishing.domain.SocialProvider

internal fun resolveCapabilityValidator(
    provider: SocialProvider,
    registry: ProviderCapabilityRegistry?,
    fallback: ProviderCapabilityValidator,
): ProviderCapabilityValidator = registry?.validatorOr(provider, fallback) ?: fallback
