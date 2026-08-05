package com.profiletailors.smp.publishing.infrastructure.socialcontent

import io.kotest.assertions.throwables.shouldThrow
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
    fun `should disable every social content feature by default`() {
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
    fun `should bind feature gates and operational limits when configuration is supplied`() {
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

    @Test
    fun `should reject polling interval that is zero or negative`() {
        shouldThrow<IllegalArgumentException> {
            SocialContentProperties.Polling(interval = Duration.ZERO)
        }
        shouldThrow<IllegalArgumentException> {
            SocialContentProperties.Polling(interval = Duration.ofMinutes(-5))
        }
    }

    @Test
    fun `should reject polling overlap that is negative`() {
        shouldThrow<IllegalArgumentException> {
            SocialContentProperties.Polling(overlap = Duration.ofMinutes(-1))
        }
    }

    @Test
    fun `should reject polling page size outside the inclusive range`() {
        shouldThrow<IllegalArgumentException> {
            SocialContentProperties.Polling(pageSize = 0)
        }
        shouldThrow<IllegalArgumentException> {
            SocialContentProperties.Polling(pageSize = -1)
        }
        shouldThrow<IllegalArgumentException> {
            SocialContentProperties.Polling(pageSize = 101)
        }
    }

    @Test
    fun `should reject polling max pages below one`() {
        shouldThrow<IllegalArgumentException> {
            SocialContentProperties.Polling(maxPages = 0)
        }
        shouldThrow<IllegalArgumentException> {
            SocialContentProperties.Polling(maxPages = -3)
        }
    }

    @Test
    fun `should reject quota requestsPerMinute below the minimum`() {
        shouldThrow<IllegalArgumentException> {
            SocialContentProperties.Quota(requestsPerMinute = 0)
        }
    }

    @Test
    fun `should reject quota burst below the minimum`() {
        shouldThrow<IllegalArgumentException> {
            SocialContentProperties.Quota(burst = 0)
        }
    }

    @Test
    fun `should reject purge interval that is zero or negative`() {
        shouldThrow<IllegalArgumentException> {
            SocialContentProperties.Purge(interval = Duration.ZERO)
        }
        shouldThrow<IllegalArgumentException> {
            SocialContentProperties.Purge(interval = Duration.ofMinutes(-1))
        }
    }

    @Test
    fun `should reject apiVersion that does not match the six digit YYYYMM format`() {
        shouldThrow<IllegalArgumentException> {
            SocialContentProperties(apiVersion = "2026")
        }
        shouldThrow<IllegalArgumentException> {
            SocialContentProperties(apiVersion = "abcdef")
        }
        shouldThrow<IllegalArgumentException> {
            SocialContentProperties(apiVersion = "")
        }
        shouldThrow<IllegalArgumentException> {
            SocialContentProperties(apiVersion = "20A606")
        }
    }

    @Test
    fun `should reject activity cache TTL that is zero or negative`() {
        shouldThrow<IllegalArgumentException> {
            SocialContentProperties(activityCacheTtl = Duration.ZERO)
        }
        shouldThrow<IllegalArgumentException> {
            SocialContentProperties(activityCacheTtl = Duration.ofHours(-1))
        }
    }

    @Test
    fun `should reject commenter profile cache TTL that is zero or negative`() {
        shouldThrow<IllegalArgumentException> {
            SocialContentProperties(commenterProfileCacheTtl = Duration.ZERO)
        }
        shouldThrow<IllegalArgumentException> {
            SocialContentProperties(commenterProfileCacheTtl = Duration.ofHours(-1))
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SocialContentProperties::class)
    private class TestConfiguration
}
