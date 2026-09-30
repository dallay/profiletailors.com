package com.profiletailors.smp.publishing.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration

class CredentialRetentionRuleTest {
    @Test
    fun `should resolve defaults when no provider override exists`() {
        val rule = CredentialRetentionRule(
            activityId = "pa-006",
            policyVersion = "",
            expiredMetadataRetention = Duration.ofDays(30),
            disconnectGrace = Duration.ZERO,
            enabled = false,
        )

        val resolved = rule.resolveFor(SocialProvider.LINKEDIN)

        resolved.activityId shouldBe "pa-006"
        resolved.provider shouldBe SocialProvider.LINKEDIN
        resolved.expiredMetadataRetention shouldBe Duration.ofDays(30)
        resolved.disconnectGrace shouldBe Duration.ZERO
        resolved.enabled shouldBe false
    }

    @Test
    fun `should prefer provider override over defaults`() {
        val rule = CredentialRetentionRule(
            activityId = "pa-006",
            policyVersion = "v1",
            expiredMetadataRetention = Duration.ofDays(30),
            disconnectGrace = Duration.ZERO,
            enabled = false,
            providerOverrides = mapOf(
                "linkedin" to CredentialProviderRetentionOverride(
                    expiredMetadataRetention = Duration.ofDays(7),
                    enabled = true,
                ),
            ),
        )

        val resolved = rule.resolveFor(SocialProvider.LINKEDIN)

        resolved.expiredMetadataRetention shouldBe Duration.ofDays(7)
        resolved.disconnectGrace shouldBe Duration.ZERO
        resolved.enabled shouldBe true
        resolved.policyVersion shouldBe "v1"
    }

    @Test
    fun `should isolate overrides per provider`() {
        val rule = CredentialRetentionRule(
            activityId = "pa-006",
            policyVersion = "",
            expiredMetadataRetention = Duration.ofDays(30),
            disconnectGrace = Duration.ZERO,
            enabled = false,
            providerOverrides = mapOf(
                "linkedin" to CredentialProviderRetentionOverride(enabled = true),
            ),
        )

        val resolved = rule.resolveFor(SocialProvider.THREADS)

        resolved.enabled shouldBe false
        resolved.expiredMetadataRetention shouldBe Duration.ofDays(30)
    }

    @Test
    fun `should reject blank activity id`() {
        shouldThrow<IllegalArgumentException> {
            CredentialRetentionRule(
                activityId = "",
                policyVersion = "",
                expiredMetadataRetention = Duration.ofDays(30),
                disconnectGrace = Duration.ZERO,
                enabled = false,
            )
        }
    }

    @Test
    fun `should reject negative retentions`() {
        shouldThrow<IllegalArgumentException> {
            CredentialRetentionRule(
                activityId = "pa-006",
                policyVersion = "",
                expiredMetadataRetention = Duration.ofDays(-1),
                disconnectGrace = Duration.ZERO,
                enabled = false,
            )
        }
        shouldThrow<IllegalArgumentException> {
            CredentialRetentionRule(
                activityId = "pa-006",
                policyVersion = "",
                expiredMetadataRetention = Duration.ofDays(30),
                disconnectGrace = Duration.ofMinutes(-1),
                enabled = false,
            )
        }
    }
}
