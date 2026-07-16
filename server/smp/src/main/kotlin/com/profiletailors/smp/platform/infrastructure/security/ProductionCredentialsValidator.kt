package com.profiletailors.smp.platform.infrastructure.security

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import kotlin.runCatching

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
 * - `SMP_MEDIA_PREVIEW_SIGNING_SECRET` must not be empty because signed public media URLs
 *   rely on it as their access-control boundary.
 *
 * **When it runs:**
 * - After `ApplicationContext.refresh()` / `finishRefresh()` when the embedded server is already
 *   started (via [ApplicationStartedEvent]).
 * - Before application runners execute, not before server startup.
 * - Skipped in test profile (where test credentials are acceptable).
 * - Skipped in Spring Boot test contexts (BDD, integration tests) detected via test-specific
 *   properties.
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

        if (isTestContext()) {
            logger.debug("Skipping production credentials validation (test context detected)")
            return
        }

        val violations = mutableListOf<String>()
        validateDatabasePassword(violations)
        validatePublishingKey(violations)
        validateJwtSecret(violations)
        validateMediaSigningSecret(violations)

        if (violations.isNotEmpty()) {
            val message = buildValidationFailureMessage(violations)
            logger.error(message)
            error(message)
        } else {
            logger.info("✅ Production credentials validation passed (no default values detected)")
        }
    }

    private fun validateDatabasePassword(violations: MutableList<String>) {
        val dbPassword = environment.getProperty("SMP_DB_PASSWORD").orEmpty()
        if (dbPassword.isBlank() || dbPassword == UNSAFE_CREDENTIAL_SENTINEL) {
            violations.add(
                "SMP_DB_PASSWORD is not configured or is set to the unsafe default '$UNSAFE_CREDENTIAL_SENTINEL'. " +
                    "Set a strong password (minimum 32 characters). " +
                    "Generate with: openssl rand -base64 32",
            )
        }
    }

    private fun validatePublishingKey(violations: MutableList<String>) {
        val credentialsKey = environment.getProperty("PUBLISHING_CREDENTIALS_KEY").orEmpty()
        if (credentialsKey.isBlank()) {
            violations.add(
                "PUBLISHING_CREDENTIALS_KEY is not configured. This key is required to encrypt " +
                    "OAuth access/refresh tokens stored in the database. Without it, LinkedIn " +
                    "publishing will fail. Generate with: openssl rand -base64 32",
            )
        }
    }

    private fun validateJwtSecret(violations: MutableList<String>) {
        val jwtSecret = environment.getProperty("SMP_LOCAL_JWT_SECRET").orEmpty()
        val jwtFallback = environment.getProperty("SMP_LOCAL_JWT_DEV_FALLBACK").orEmpty()
        if (jwtSecret.isBlank() && jwtFallback.isBlank()) {
            violations.add(
                "SMP_LOCAL_JWT_SECRET and SMP_LOCAL_JWT_DEV_FALLBACK are both empty. " +
                    "At least one must be configured for JWT signing. " +
                    "Generate with: openssl rand -base64 32",
            )
        }
    }

    private fun validateMediaSigningSecret(violations: MutableList<String>) {
        val signingSecret = environment.getProperty("media.preview-signing-secret")
            .orEmpty()
            .ifBlank { environment.getProperty("SMP_MEDIA_PREVIEW_SIGNING_SECRET").orEmpty() }
        if (signingSecret.isBlank()) {
            violations.add(
                "SMP_MEDIA_PREVIEW_SIGNING_SECRET is not configured. This key signs public " +
                    "media preview URLs and must be unique per environment. " +
                    "Generate with: openssl rand -base64 32",
            )
        }
    }

    private fun buildValidationFailureMessage(violations: List<String>): String = buildString {
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

    /**
     * Detects whether the application is running in a Spring Boot test context.
     *
     * Detection strategy:
     * - `bdd.variant` is set by Cucumber-specific test configurations.
     * - JUnit 5 on the classpath indicates a test context, BUT only when the
     *   Environment is a real Spring Boot environment (not MockEnvironment).
     *   Plain JUnit unit tests (validator's own tests) use MockEnvironment and
     *   call validateCredentials() directly — those should NOT skip validation.
     *   [@SpringBootTest] integration tests (Postgres, etc.) use a real Environment
     *   and have JUnit on the classpath — those SHOULD skip validation.
     * - `environment.getProperty("spring.test.context.cache.maxSize")` is NOT used
     *   here because Spring does not expose that TestContext attribute through the
     *   Environment — the check would never match.
     */
    private fun isTestContext(): Boolean {
        if (environment.getProperty("bdd.variant") != null) return true

        val isMockEnvironment = runCatching {
            environment::class.qualifiedName?.contains("MockEnvironment") == true
        }.getOrDefault(false)
        if (isMockEnvironment) return false

        return runCatching {
            Class.forName("org.junit.jupiter.api.Test")
            true
        }.getOrDefault(false)
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(ProductionCredentialsValidator::class.java)
        private const val UNSAFE_CREDENTIAL_SENTINEL = "CHANGE_ME"
    }
}
