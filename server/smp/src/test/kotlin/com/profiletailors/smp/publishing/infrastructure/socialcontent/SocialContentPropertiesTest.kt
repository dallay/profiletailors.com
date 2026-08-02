package com.profiletailors.smp.publishing.infrastructure.socialcontent

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import java.time.Duration

class SocialContentPropertiesTest {
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(TestConfiguration::class.java)

    @Test
    fun `all social content features are disabled by default`() {
        contextRunner.run { context ->
            val properties = context.getBean(SocialContentProperties::class.java)

            properties.discoveryEnabled shouldBe false
            properties.importEnabled shouldBe false
            properties.inboxEnabled shouldBe false
            properties.repliesEnabled shouldBe false
            properties.syncEnabled shouldBe false
            properties.webhooks.enabled shouldBe false
            properties.purge.enabled shouldBe false
        }
    }

    @Test
    fun `binds feature gates and operational limits`() {
        contextRunner
            .withPropertyValues(
                "publishing.social-content.discovery-enabled=true",
                "publishing.social-content.import-enabled=true",
                "publishing.social-content.inbox-enabled=true",
                "publishing.social-content.replies-enabled=true",
                "publishing.social-content.sync-enabled=true",
                "publishing.social-content.api-version=202701",
                "publishing.social-content.polling.interval=PT2M",
                "publishing.social-content.polling.overlap=PT15M",
                "publishing.social-content.polling.page-size=25",
                "publishing.social-content.polling.max-pages=4",
                "publishing.social-content.activity-cache-ttl=PT36H",
                "publishing.social-content.commenter-profile-cache-ttl=PT12H",
                "publishing.social-content.quota.requests-per-minute=80",
                "publishing.social-content.quota.burst=12",
                "publishing.social-content.webhooks.enabled=true",
                "publishing.social-content.webhooks.signing-secret=secret",
                "publishing.social-content.purge.enabled=true",
                "publishing.social-content.purge.interval=PT6H",
            ).run { context ->
                val properties = context.getBean(SocialContentProperties::class.java)

                properties.discoveryEnabled shouldBe true
                properties.importEnabled shouldBe true
                properties.inboxEnabled shouldBe true
                properties.repliesEnabled shouldBe true
                properties.syncEnabled shouldBe true
                properties.apiVersion shouldBe "202701"
                properties.polling.interval shouldBe Duration.ofMinutes(2)
                properties.polling.overlap shouldBe Duration.ofMinutes(15)
                properties.polling.pageSize shouldBe 25
                properties.polling.maxPages shouldBe 4
                properties.activityCacheTtl shouldBe Duration.ofHours(36)
                properties.commenterProfileCacheTtl shouldBe Duration.ofHours(12)
                properties.quota.requestsPerMinute shouldBe 80
                properties.quota.burst shouldBe 12
                properties.webhooks.enabled shouldBe true
                properties.webhooks.signingSecret shouldBe "secret"
                properties.purge.enabled shouldBe true
                properties.purge.interval shouldBe Duration.ofHours(6)
            }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SocialContentProperties::class)
    private class TestConfiguration
}
