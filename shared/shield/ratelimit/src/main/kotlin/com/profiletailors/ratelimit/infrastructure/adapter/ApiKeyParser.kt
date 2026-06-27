package com.profiletailors.ratelimit.infrastructure.adapter

import com.profiletailors.ratelimit.infrastructure.config.RateLimitProperties
import com.profiletailors.ratelimit.infrastructure.config.RateLimitProperties.Companion.TIER_BASIC
import com.profiletailors.ratelimit.infrastructure.config.RateLimitProperties.Companion.TIER_FREE
import com.profiletailors.ratelimit.infrastructure.config.RateLimitProperties.Companion.TIER_PROFESSIONAL
import org.springframework.stereotype.Component

/**
 * Component responsible for parsing API keys and extracting subscription tier information.
 *
 * This component externalizes the API key prefix detection logic from the rate limiter,
 * making the system more maintainable and following the Open/Closed Principle.
 * New tier prefixes can be added through configuration without code changes.
 *
 * @property properties Rate limit configuration containing API key prefix mappings
 */
@Component
class ApiKeyParser(private val properties: RateLimitProperties) {

    /**
     * Extracts the subscription tier name (lowercase) from an API key based on its prefix.
     *
     * The prefix-to-tier mapping is configured in [RateLimitProperties.apiKeyPrefixes].
     * This allows for flexible tier detection without hardcoding prefix values.
     *
     * **Matching Strategy:**
     * - Checks if the API key starts with the configured professional prefix
     * - If not, checks if it starts with the configured basic prefix
     * - If neither match, defaults to FREE tier
     *
     * **Examples** (with default prefixes):
     * - `"PX001-abc123"` → `"professional"`
     * - `"BX001-xyz789"` → `"basic"`
     * - `"unknown-key"` → `"free"`
     * - `""` → `"free"`
     *
     * @param apiKey The API key to parse. Must not be null.
     * @return The tier name in lowercase (e.g., "free", "basic", "professional").
     */
    fun extractTierName(apiKey: String): String {
        val prefixes = properties.apiKeyPrefixes

        return when {
            apiKey.startsWith(prefixes.professional) -> TIER_PROFESSIONAL
            apiKey.startsWith(prefixes.basic) -> TIER_BASIC
            else -> TIER_FREE
        }
    }
}
