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
 * - `SMP_LINKEDIN_STATE_SIGNING_SECRET` must not be empty because it protects the OAuth state.
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

        val violations: List<String> = listOfNotNull(
            checkDatabasePassword(),
            checkPublishingKey(),
            checkJwtSecret(activeProfiles),
            checkMediaSigningSecret(),
            checkLinkedInStateSecret(),
        )

        if (violations.isNotEmpty()) {
            val message = buildValidationFailureMessage(violations)
            logger.error(message)
            error(message)
        } else {
            logger.info("✅ Production credentials validation passed (no default values detected)")
        }
    }

    private fun checkDatabasePassword(): String? {
        val dbPassword = environment.getProperty("SMP_DB_PASSWORD").orEmpty().trim()
        return if (dbPassword.isBlank() || UNSAFE_CREDENTIAL_PREFIXES.any(dbPassword::startsWith)) {
            "SMP_DB_PASSWORD is not configured or uses an unsafe placeholder. " +
                "Set a strong password (minimum 32 characters). " +
                SECRET_GENERATION_GUIDANCE
        } else {
            null
        }
    }

    private fun checkPublishingKey(): String? {
        val credentialsKey = environment.getProperty("PUBLISHING_CREDENTIALS_KEY").orEmpty()
        return if (credentialsKey.isBlank()) {
            "PUBLISHING_CREDENTIALS_KEY is not configured. This key is required to encrypt " +
                "OAuth access/refresh tokens stored in the database. Without it, LinkedIn " +
                "publishing will fail. $SECRET_GENERATION_GUIDANCE"
        } else {
            null
        }
    }

    private fun checkJwtSecret(activeProfiles: Set<String>): String? {
        val jwtSecret = environment.getProperty("SMP_LOCAL_JWT_SECRET").orEmpty()
        val jwtFallback = environment.getProperty("SMP_LOCAL_JWT_DEV_FALLBACK").orEmpty()
        if (jwtSecret.isNotBlank()) return null
        if (jwtFallback.isNotBlank() && "dev" in activeProfiles) return null

        return if (jwtFallback.isNotBlank()) {
            "SMP_LOCAL_JWT_DEV_FALLBACK is allowed only with the dev profile. " +
                "Configure SMP_LOCAL_JWT_SECRET outside development. " +
                SECRET_GENERATION_GUIDANCE
        } else {
            "SMP_LOCAL_JWT_SECRET and SMP_LOCAL_JWT_DEV_FALLBACK are both empty. " +
                "Configure SMP_LOCAL_JWT_SECRET outside development. " +
                SECRET_GENERATION_GUIDANCE
        }
    }

    private fun checkMediaSigningSecret(): String? {
        val signingSecret = environment.getProperty("SMP_MEDIA_PREVIEW_SIGNING_SECRET").orEmpty()
        return if (signingSecret.isBlank()) {
            "SMP_MEDIA_PREVIEW_SIGNING_SECRET is not configured. This key signs public " +
                "media preview URLs and must be unique per environment. " +
                SECRET_GENERATION_GUIDANCE
        } else {
            null
        }
    }

    private fun checkLinkedInStateSecret(): String? {
        val signingSecret = environment.getProperty("SMP_LINKEDIN_STATE_SIGNING_SECRET").orEmpty().ifBlank {
            environment.getProperty("publishing.linkedin.state-signing-secret").orEmpty()
        }
        return if (signingSecret.isBlank() || UNSAFE_CREDENTIAL_PREFIXES.any(signingSecret::startsWith)) {
            "SMP_LINKEDIN_STATE_SIGNING_SECRET is not configured or uses an unsafe placeholder. " +
                "This key signs LinkedIn " +
                "OAuth state and must be unique per environment. " +
                SECRET_GENERATION_GUIDANCE
        } else {
            null
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
        private val UNSAFE_CREDENTIAL_PREFIXES = setOf("CHANGE_ME")
        private const val SECRET_GENERATION_GUIDANCE = "Generate with: openssl rand -base64 32"
    }
}
