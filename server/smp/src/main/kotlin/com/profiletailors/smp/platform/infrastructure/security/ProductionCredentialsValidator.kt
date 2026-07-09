package com.profiletailors.smp.platform.infrastructure.security

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Validates that no default or placeholder credentials are active when the application starts.
 *
 * This component runs during Spring Boot startup (on [ApplicationStartedEvent])
 * and checks critical secrets against a deny-list of known unsafe values. If any unsafe
 * credential is detected, the application **crashes immediately** with a clear error message
 * rather than silently running with insecure defaults.
 *
 * **Why this exists:**
 * - `application.yaml` contains `CHANGE_ME_gK2fcFZg5cgVu9U` defaults for local dev convenience.
 * - Without validation, an operator could deploy to production without overriding those values.
 * - This validator is the safety net — it prevents accidental production deploys with dev credentials.
 *
 * **What it checks:**
 * - `SMP_DB_PASSWORD` must not be `CHANGE_ME_gK2fcFZg5cgVu9U` or empty.
 * - `PUBLISHING_CREDENTIALS_KEY` must not be empty (required for OAuth token encryption).
 * - `SMP_LOCAL_JWT_SECRET` must not be empty when `SMP_LOCAL_JWT_DEV_FALLBACK` is also empty
 *   (enforced separately by `LocalJwtSecretResolver`, but double-checked here).
 *
 * **When it runs:**
 * - After application context is fully initialized but before the web server starts accepting connections (via [ApplicationStartedEvent]).
 * - Skipped in test profile (where test credentials are acceptable).
 * - Skipped in Spring Boot test contexts (BDD, integration tests) detected via test-specific properties.
 *
 * **Related:**
 * - Issue #233 (MVP Launch Readiness): "No default credentials can reach production"
 * - Issue #176: `PUBLISHING_CREDENTIALS_KEY` has no validation
 * - [Production Secrets Reference](docs/production-secrets.md)
 *
 * @see com.profiletailors.smp.identity.infrastructure.security.LocalJwtSecretResolver
 * @since 1.0.0
 */
@Component
class ProductionCredentialsValidator(private val environment: Environment) {

    @EventListener(ApplicationStartedEvent::class)
    fun validateCredentials() {
        val activeProfiles = environment.activeProfiles.toSet()

        // Skip validation in test profile (tests use mock/ephemeral credentials)
        if ("test" in activeProfiles) {
            logger.debug("Skipping production credentials validation (test profile active)")
            return
        }

        // Skip validation in Spring Boot test contexts (BDD, integration tests, etc.)
        // These tests may use Testcontainers or mock credentials that would fail validation
        val isTestContext = environment.getProperty("spring.test.context.cache.maxSize") != null ||
            environment.getProperty("bdd.variant") != null
        if (isTestContext) {
            logger.debug("Skipping production credentials validation (test context detected)")
            return
        }

        val violations = mutableListOf<String>()

        // 1. Database password
        val dbPassword = environment.getProperty("SMP_DB_PASSWORD").orEmpty()
        if (dbPassword.isBlank() || dbPassword == DEFAULT_UNSAFE_PASSWORD) {
            violations.add(
                "SMP_DB_PASSWORD is not configured or is set to the unsafe default '$DEFAULT_UNSAFE_PASSWORD'. " +
                    "Set a strong password (minimum 32 characters). " +
                    "Generate with: openssl rand -base64 32",
            )
        }

        // 2. Publishing credentials encryption key (OAuth tokens at rest)
        val credentialsKey = environment.getProperty("PUBLISHING_CREDENTIALS_KEY").orEmpty()
        if (credentialsKey.isBlank()) {
            violations.add(
                "PUBLISHING_CREDENTIALS_KEY is not configured. This key is required to encrypt " +
                    "OAuth access/refresh tokens stored in the database. Without it, LinkedIn " +
                    "publishing will fail. Generate with: openssl rand -base64 32",
            )
        }

        // 3. JWT signing secret (when local JWT mode is enabled)
        // Note: LocalJwtSecretResolver already enforces this, but we double-check here for clarity.
        val jwtSecret = environment.getProperty("SMP_LOCAL_JWT_SECRET").orEmpty()
        val jwtFallback = environment.getProperty("SMP_LOCAL_JWT_DEV_FALLBACK").orEmpty()
        if (jwtSecret.isBlank() && jwtFallback.isBlank()) {
            violations.add(
                "SMP_LOCAL_JWT_SECRET and SMP_LOCAL_JWT_DEV_FALLBACK are both empty. " +
                    "At least one must be configured for JWT signing. " +
                    "Generate with: openssl rand -base64 32",
            )
        }

        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine("❌ PRODUCTION CREDENTIAL VALIDATION FAILED")
                appendLine()
                appendLine("The application cannot start because unsafe or missing credentials were detected:")
                appendLine()
                violations.forEachIndexed { index, violation ->
                    appendLine("${index + 1}. $violation")
                    appendLine()
                }
                appendLine("See docs/production-secrets.md for full secret generation and rotation guide.")
            }
            logger.error(message)
            error(message)
        } else {
            logger.info("✅ Production credentials validation passed (no default values detected)")
        }
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(ProductionCredentialsValidator::class.java)
        private const val DEFAULT_UNSAFE_PASSWORD = "CHANGE_ME_gK2fcFZg5cgVu9U"
    }
}
