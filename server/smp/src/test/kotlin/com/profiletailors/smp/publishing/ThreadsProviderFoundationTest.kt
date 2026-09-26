package com.profiletailors.smp.publishing

import com.profiletailors.smp.publishing.domain.ProviderCapabilityRegistry
import com.profiletailors.smp.publishing.domain.PublishingCapability
import com.profiletailors.smp.publishing.domain.SocialAccountKind
import com.profiletailors.smp.publishing.domain.SocialProvider
import com.profiletailors.smp.publishing.infrastructure.threads.ThreadsCapabilitySet
import com.profiletailors.smp.publishing.infrastructure.threads.ThreadsPublishingProperties
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration

class ThreadsProviderFoundationTest {
    @Test
    fun `threads is a personal profile provider with bounded publishing capabilities`() {
        ThreadsCapabilitySet.default().supportedCapabilities shouldContainExactly setOf(
            PublishingCapability.TEXT,
            PublishingCapability.SINGLE_IMAGE,
            PublishingCapability.SINGLE_VIDEO,
            PublishingCapability.MIXED_MEDIA_CAROUSEL,
        )
        ThreadsCapabilitySet.default().accountKinds shouldContainExactly setOf(SocialAccountKind.PERSONAL_PROFILE)
        ThreadsCapabilitySet.default().carouselItemRange shouldBe 2..20
    }

    @Test
    fun `provider capability registry resolves threads without exposing provider implementation`() = runTest {
        val registry = ProviderCapabilityRegistry.from(SocialProvider.THREADS to ThreadsCapabilitySet.default())

        registry.capabilities(SocialProvider.THREADS) shouldBe ThreadsCapabilitySet.default()
        registry.capabilities(SocialProvider.LINKEDIN) shouldBe null
    }

    @Test
    fun `threads configuration carries approved scopes and bounded workflow defaults`() {
        val properties = ThreadsPublishingProperties()

        properties.requiredScopes shouldContainExactly setOf("threads_basic", "threads_content_publish")
        properties.refreshAhead shouldBe Duration.ofHours(1)
        properties.containerPollInterval shouldBe Duration.ofSeconds(2)
        properties.containerPollTimeout shouldBe Duration.ofSeconds(60)
        properties.containerPollMaxAttempts shouldBe 30
        properties.mediaUrlTtl shouldBe Duration.ofMinutes(25)
    }
}
