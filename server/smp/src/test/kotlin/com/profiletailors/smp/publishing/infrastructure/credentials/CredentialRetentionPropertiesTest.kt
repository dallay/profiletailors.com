package com.profiletailors.smp.publishing.infrastructure.credentials

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import java.time.Duration

class CredentialRetentionPropertiesTest {
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(TestConfiguration::class.java)

    @Test
    fun `should bind pa-006 defaults when no configuration is supplied`() {
        contextRunner.run { context ->
            val properties = context.getBean(CredentialRetentionProperties::class.java)

            properties.activityId shouldBe "pa-006"
            properties.policyVersion shouldBe ""
            properties.enabled shouldBe false
            properties.expiredMetadataRetention shouldBe Duration.ofDays(30)
            properties.disconnectGrace shouldBe Duration.ZERO
            properties.interval shouldBe Duration.ofHours(6)
            properties.initialDelay shouldBe Duration.ofMinutes(5)
            properties.batchSize shouldBe 100
            properties.dryRun shouldBe false
            properties.providers shouldBe emptyMap()
        }
    }

    @Test
    fun `should bind globals and provider overrides when configuration is supplied`() {
        contextRunner
            .withPropertyValues(
                "publishing.credentials.retention.activity-id=pa-006",
                "publishing.credentials.retention.policy-version=v1",
                "publishing.credentials.retention.enabled=true",
                "publishing.credentials.retention.expired-metadata-retention=P7D",
                "publishing.credentials.retention.disconnect-grace=PT1H",
                "publishing.credentials.retention.interval=PT6H",
                "publishing.credentials.retention.batch-size=50",
                "publishing.credentials.retention.dry-run=true",
                "publishing.credentials.retention.providers.linkedin.expired-metadata-retention=P7D",
                "publishing.credentials.retention.providers.linkedin.enabled=true",
            ).run { context ->
                val properties = context.getBean(CredentialRetentionProperties::class.java)

                properties.enabled shouldBe true
                properties.expiredMetadataRetention shouldBe Duration.ofDays(7)
                properties.disconnectGrace shouldBe Duration.ofHours(1)
                properties.batchSize shouldBe 50
                properties.dryRun shouldBe true
                properties.providers["linkedin"]?.expiredMetadataRetention shouldBe Duration.ofDays(7)
                properties.providers["linkedin"]?.enabled shouldBe true

                val rule = properties.toRule()
                val resolved = rule.resolveFor(com.profiletailors.smp.publishing.domain.SocialProvider.LINKEDIN)
                resolved.expiredMetadataRetention shouldBe Duration.ofDays(7)
                resolved.enabled shouldBe true
            }
    }

    @Test
    fun `should reject negative expired metadata retention`() {
        shouldThrow<IllegalArgumentException> {
            CredentialRetentionProperties(expiredMetadataRetention = Duration.ofDays(-1))
        }
    }

    @Test
    fun `should reject negative disconnect grace`() {
        shouldThrow<IllegalArgumentException> {
            CredentialRetentionProperties(disconnectGrace = Duration.ofMinutes(-1))
        }
    }

    @Test
    fun `should reject non-positive purge interval`() {
        shouldThrow<IllegalArgumentException> {
            CredentialRetentionProperties(interval = Duration.ZERO)
        }
        shouldThrow<IllegalArgumentException> {
            CredentialRetentionProperties(interval = Duration.ofMinutes(-5))
        }
    }

    @Test
    fun `should reject batch size outside the inclusive range`() {
        shouldThrow<IllegalArgumentException> {
            CredentialRetentionProperties(batchSize = 0)
        }
        shouldThrow<IllegalArgumentException> {
            CredentialRetentionProperties(batchSize = 1001)
        }
    }

    @Test
    fun `should reject blank activity id`() {
        shouldThrow<IllegalArgumentException> {
            CredentialRetentionProperties(activityId = "")
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CredentialRetentionProperties::class)
    private class TestConfiguration
}
