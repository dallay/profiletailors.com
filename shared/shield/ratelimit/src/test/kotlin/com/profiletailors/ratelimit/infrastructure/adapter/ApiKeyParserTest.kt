package com.profiletailors.ratelimit.infrastructure.adapter

import com.profiletailors.ratelimit.infrastructure.config.RateLimitProperties
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

internal class ApiKeyParserTest {

    @Test
    fun `should extract professional tier name when API key starts with professional prefix`() {
        // Arrange
        val properties = RateLimitProperties(
            apiKeyPrefixes = RateLimitProperties.ApiKeyPrefixConfig(
                professional = "PX001-",
                basic = "BX001-",
            ),
        )
        val parser = ApiKeyParser(properties)

        // Act
        val tierName = parser.extractTierName("PX001-abc123xyz")

        // Assert
        tierName shouldBe "professional"
    }

    @Test
    fun `should extract basic tier name when API key starts with basic prefix`() {
        // Arrange
        val properties = RateLimitProperties(
            apiKeyPrefixes = RateLimitProperties.ApiKeyPrefixConfig(
                professional = "PX001-",
                basic = "BX001-",
            ),
        )
        val parser = ApiKeyParser(properties)

        // Act
        val tierName = parser.extractTierName("BX001-xyz789abc")

        // Assert
        tierName shouldBe "basic"
    }

    @Test
    fun `should default to free tier name when API key has no recognized prefix`() {
        // Arrange
        val properties = RateLimitProperties(
            apiKeyPrefixes = RateLimitProperties.ApiKeyPrefixConfig(
                professional = "PX001-",
                basic = "BX001-",
            ),
        )
        val parser = ApiKeyParser(properties)

        // Act & Assert
        assertAll(
            { parser.extractTierName("unknown-key") shouldBe "free" },
            { parser.extractTierName("FX001-test") shouldBe "free" },
            { parser.extractTierName("random-string") shouldBe "free" },
            { parser.extractTierName("") shouldBe "free" },
        )
    }

    @Test
    fun `should support custom prefix configuration`() {
        // Arrange
        val properties = RateLimitProperties(
            apiKeyPrefixes = RateLimitProperties.ApiKeyPrefixConfig(
                professional = "PRO-",
                basic = "STD-",
            ),
        )
        val parser = ApiKeyParser(properties)

        // Act & Assert
        assertAll(
            { parser.extractTierName("PRO-custom-key") shouldBe "professional" },
            { parser.extractTierName("STD-standard-key") shouldBe "basic" },
            { parser.extractTierName("PX001-old-format") shouldBe "free" },
        )
    }

    @Test
    fun `should handle edge cases gracefully`() {
        // Arrange
        val properties = RateLimitProperties(
            apiKeyPrefixes = RateLimitProperties.ApiKeyPrefixConfig(
                professional = "PX001-",
                basic = "BX001-",
            ),
        )
        val parser = ApiKeyParser(properties)

        // Act & Assert
        assertAll(
            // Empty string
            { parser.extractTierName("") shouldBe "free" },

            // Only prefix (no key content)
            { parser.extractTierName("PX001-") shouldBe "professional" },
            { parser.extractTierName("BX001-") shouldBe "basic" },

            // Case sensitivity (prefixes are case-sensitive)
            { parser.extractTierName("px001-lowercase") shouldBe "free" },
            { parser.extractTierName("BXOO1-uppercase") shouldBe "free" },

            // Prefix in the middle or end (should not match)
            { parser.extractTierName("key-PX001-middle") shouldBe "free" },
            { parser.extractTierName("key-ending-PX001-") shouldBe "free" },
        )
    }

    @Test
    fun `should prioritize professional prefix over basic when both could match`() {
        // Arrange
        val properties = RateLimitProperties(
            apiKeyPrefixes = RateLimitProperties.ApiKeyPrefixConfig(
                professional = "P",
                basic = "PX",
            ),
        )
        val parser = ApiKeyParser(properties)

        // Act
        val tierName = parser.extractTierName("PX001-test")

        // Assert - Professional is checked first
        tierName shouldBe "professional"
    }

    @Test
    fun `should use default prefix values when not configured`() {
        // Arrange
        val properties = RateLimitProperties() // Uses defaults
        val parser = ApiKeyParser(properties)

        // Act & Assert
        assertAll(
            { parser.extractTierName("PX001-default-prof") shouldBe "professional" },
            { parser.extractTierName("BX001-default-basic") shouldBe "basic" },
            { parser.extractTierName("FX001-no-match") shouldBe "free" },
        )
    }
}
