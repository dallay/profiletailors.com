package com.profiletailors.smp.platform.infrastructure.security

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment

/**
 * Unit tests for [ProductionCredentialsValidator].
 *
 * Tests verify that the validator **crashes the application** when unsafe default credentials
 * are detected, and allows startup when all credentials are properly configured.
 *
 * @see ProductionCredentialsValidator
 */
class ProductionCredentialsValidatorTest {

    @Test
    fun `should fail when SMP_DB_PASSWORD is CHANGE_ME`() {
        val environment = MockEnvironment().apply {
            setProperty("SMP_DB_PASSWORD", "CHANGE_ME")
            setProperty("PUBLISHING_CREDENTIALS_ENCRYPTION_KEY", "valid-key-here")
            setProperty("SMP_LOCAL_JWT_SECRET", "valid-secret-32-bytes-minimum!")
        }
        val validator = ProductionCredentialsValidator(environment)

        val exception = shouldThrow<IllegalStateException> {
            validator.validateCredentials()
        }

        exception.message shouldContain "SMP_DB_PASSWORD"
        exception.message shouldContain "unsafe placeholder"
    }

    @Test
    fun `should fail when SMP_DB_PASSWORD uses the documented placeholder`() {
        val environment = validEnvironment().apply {
            setProperty("SMP_DB_PASSWORD", "CHANGE_ME_gK2fcFZg5cgVu9U")
        }
        val validator = ProductionCredentialsValidator(environment)

        val exception = shouldThrow<IllegalStateException> {
            validator.validateCredentials()
        }

        exception.message shouldContain "SMP_DB_PASSWORD"
        exception.message shouldContain "unsafe placeholder"
    }

    @Test
    fun `should fail when SMP_DB_PASSWORD is blank`() {
        val environment = MockEnvironment().apply {
            setProperty("SMP_DB_PASSWORD", "")
            setProperty("PUBLISHING_CREDENTIALS_ENCRYPTION_KEY", "valid-key-here")
            setProperty("SMP_LOCAL_JWT_SECRET", "valid-secret-32-bytes-minimum!")
        }
        val validator = ProductionCredentialsValidator(environment)

        val exception = shouldThrow<IllegalStateException> {
            validator.validateCredentials()
        }

        exception.message shouldContain "SMP_DB_PASSWORD"
    }

    @Test
    fun `should fail when normalized SMP_DB_PASSWORD is shorter than 32 characters`() {
        val environment = validEnvironment().apply {
            setProperty("SMP_DB_PASSWORD", "  short-password-under-32-chars  ")
        }
        val validator = ProductionCredentialsValidator(environment)

        val exception = shouldThrow<IllegalStateException> {
            validator.validateCredentials()
        }

        exception.message shouldContain "SMP_DB_PASSWORD"
        exception.message shouldContain "minimum 32 characters"
    }

    @Test
    fun `should fail when PUBLISHING_CREDENTIALS_ENCRYPTION_KEY is blank`() {
        val environment = MockEnvironment().apply {
            setProperty("SMP_DB_PASSWORD", "strong-password-here")
            setProperty("PUBLISHING_CREDENTIALS_ENCRYPTION_KEY", "")
            setProperty("SMP_LOCAL_JWT_SECRET", "valid-secret-32-bytes-minimum!")
        }
        val validator = ProductionCredentialsValidator(environment)

        val exception = shouldThrow<IllegalStateException> {
            validator.validateCredentials()
        }

        exception.message shouldContain "PUBLISHING_CREDENTIALS_ENCRYPTION_KEY"
        exception.message shouldContain "OAuth"
    }

    @Test
    fun `should reject a publishing key placeholder with leading whitespace`() {
        val environment = validEnvironment().apply {
            setProperty("PUBLISHING_CREDENTIALS_ENCRYPTION_KEY", "  CHANGE_ME_PUBLISHING_KEY")
        }
        val validator = ProductionCredentialsValidator(environment)

        val exception = shouldThrow<IllegalStateException> {
            validator.validateCredentials()
        }

        exception.message shouldContain "PUBLISHING_CREDENTIALS_ENCRYPTION_KEY"
        exception.message shouldContain "unsafe placeholder"
    }

    @Test
    fun `should fail when both SMP_LOCAL_JWT_SECRET and SMP_LOCAL_JWT_DEV_FALLBACK are blank`() {
        val environment = MockEnvironment().apply {
            setProperty("SMP_DB_PASSWORD", "strong-password-here")
            setProperty("PUBLISHING_CREDENTIALS_ENCRYPTION_KEY", "valid-key-here")
            setProperty("SMP_LOCAL_JWT_SECRET", "")
            setProperty("SMP_LOCAL_JWT_DEV_FALLBACK", "")
        }
        val validator = ProductionCredentialsValidator(environment)

        val exception = shouldThrow<IllegalStateException> {
            validator.validateCredentials()
        }

        exception.message shouldContain "SMP_LOCAL_JWT_SECRET"
        exception.message shouldContain "SMP_LOCAL_JWT_DEV_FALLBACK"
    }

    @Test
    fun `should fail when SMP_MEDIA_PREVIEW_SIGNING_SECRET is blank`() {
        val environment = MockEnvironment().apply {
            setProperty("SMP_DB_PASSWORD", "strong-password-here")
            setProperty("PUBLISHING_CREDENTIALS_ENCRYPTION_KEY", "valid-key-here")
            setProperty("SMP_LOCAL_JWT_SECRET", "valid-secret-32-bytes-minimum!")
            setProperty("SMP_MEDIA_PREVIEW_SIGNING_SECRET", "")
        }
        val validator = ProductionCredentialsValidator(environment)

        val exception = shouldThrow<IllegalStateException> {
            validator.validateCredentials()
        }

        exception.message shouldContain "SMP_MEDIA_PREVIEW_SIGNING_SECRET"
        exception.message shouldContain "media preview"
    }

    @Test
    fun `should reject a media signing placeholder with leading whitespace`() {
        val environment = validEnvironment().apply {
            setProperty("SMP_MEDIA_PREVIEW_SIGNING_SECRET", "  CHANGE_ME_MEDIA_SIGNING_SECRET")
        }
        val validator = ProductionCredentialsValidator(environment)

        val exception = shouldThrow<IllegalStateException> {
            validator.validateCredentials()
        }

        exception.message shouldContain "SMP_MEDIA_PREVIEW_SIGNING_SECRET"
        exception.message shouldContain "unsafe placeholder"
    }

    @Test
    fun `should fail when SMP_LINKEDIN_STATE_SIGNING_SECRET is blank`() {
        val environment = validEnvironment().apply {
            setProperty("SMP_LINKEDIN_STATE_SIGNING_SECRET", "")
        }
        val validator = ProductionCredentialsValidator(environment)

        val exception = shouldThrow<IllegalStateException> {
            validator.validateCredentials()
        }

        exception.message shouldContain "SMP_LINKEDIN_STATE_SIGNING_SECRET"
        exception.message shouldContain "OAuth state"
    }

    @Test
    fun `should reject the LinkedIn state placeholder`() {
        val environment = validEnvironment().apply {
            setProperty("SMP_LINKEDIN_STATE_SIGNING_SECRET", "  CHANGE_ME_LINKEDIN_STATE")
        }
        val validator = ProductionCredentialsValidator(environment)

        val exception = shouldThrow<IllegalStateException> {
            validator.validateCredentials()
        }

        exception.message shouldContain "SMP_LINKEDIN_STATE_SIGNING_SECRET"
        exception.message shouldContain "unsafe placeholder"
    }

    @Test
    fun `should not accept media preview YAML fallback like random uuid`() {
        val environment = MockEnvironment().apply {
            setProperty("SMP_DB_PASSWORD", "strong-password-here")
            setProperty("PUBLISHING_CREDENTIALS_ENCRYPTION_KEY", "valid-key-here")
            setProperty("SMP_LOCAL_JWT_SECRET", "valid-secret-32-bytes-minimum!")
            setProperty("media.preview-signing-secret", "\${random.uuid}")
            setProperty("SMP_LINKEDIN_STATE_SIGNING_SECRET", "valid-linkedin-state-secret-32-bytes")
        }
        val validator = ProductionCredentialsValidator(environment)

        val exception = shouldThrow<IllegalStateException> {
            validator.validateCredentials()
        }

        exception.message shouldContain "SMP_MEDIA_PREVIEW_SIGNING_SECRET"
    }

    @Test
    fun `should fail with multiple violations when multiple secrets are invalid`() {
        val environment = MockEnvironment().apply {
            setProperty("SMP_DB_PASSWORD", "CHANGE_ME")
            setProperty("PUBLISHING_CREDENTIALS_ENCRYPTION_KEY", "")
            setProperty("SMP_LOCAL_JWT_SECRET", "")
            setProperty("SMP_LOCAL_JWT_DEV_FALLBACK", "")
            setProperty("SMP_MEDIA_PREVIEW_SIGNING_SECRET", "")
        }
        val validator = ProductionCredentialsValidator(environment)

        val exception = shouldThrow<IllegalStateException> {
            validator.validateCredentials()
        }

        exception.message shouldContain "SMP_DB_PASSWORD"
        exception.message shouldContain "PUBLISHING_CREDENTIALS_ENCRYPTION_KEY"
        exception.message shouldContain "SMP_LOCAL_JWT_SECRET"
        exception.message shouldContain "SMP_MEDIA_PREVIEW_SIGNING_SECRET"
        exception.message shouldContain "SMP_LINKEDIN_STATE_SIGNING_SECRET"
    }

    @Test
    fun `should pass when all credentials are properly configured`() {
        val environment = MockEnvironment().apply {
            setProperty("SMP_DB_PASSWORD", "  strong-random-password-at-least-32-chars  ")
            setProperty("PUBLISHING_CREDENTIALS_ENCRYPTION_KEY", "  base64-encoded-32-byte-key-here  ")
            setProperty("SMP_LOCAL_JWT_SECRET", "  valid-secret-minimum-32-bytes!  ")
            setProperty("SMP_MEDIA_PREVIEW_SIGNING_SECRET", "  valid-media-preview-secret-32-bytes  ")
            setProperty("SMP_LINKEDIN_STATE_SIGNING_SECRET", "  valid-linkedin-state-secret-32-bytes  ")
        }
        val validator = ProductionCredentialsValidator(environment)

        // Should not throw
        validator.validateCredentials()
    }

    @Test
    fun `should pass when SMP_LOCAL_JWT_DEV_FALLBACK is set instead of SMP_LOCAL_JWT_SECRET`() {
        val environment = MockEnvironment().apply {
            setProperty("SMP_DB_PASSWORD", "strong-random-password-at-least-32-chars")
            setProperty("PUBLISHING_CREDENTIALS_ENCRYPTION_KEY", "base64-encoded-32-byte-key-here")
            setProperty("SMP_LOCAL_JWT_SECRET", "")
            setProperty("SMP_LOCAL_JWT_DEV_FALLBACK", "valid-fallback-secret-32-bytes!")
            setProperty("SMP_MEDIA_PREVIEW_SIGNING_SECRET", "valid-media-preview-secret-32-bytes")
            setProperty("SMP_LINKEDIN_STATE_SIGNING_SECRET", "valid-linkedin-state-secret-32-bytes")
            setActiveProfiles("dev")
        }
        val validator = ProductionCredentialsValidator(environment)

        // Should not throw (fallback is acceptable)
        validator.validateCredentials()
    }

    @Test
    fun `should reject SMP_LOCAL_JWT_DEV_FALLBACK outside dev`() {
        val environment = validEnvironment().apply {
            setProperty("SMP_LOCAL_JWT_SECRET", "")
            setProperty("SMP_LOCAL_JWT_DEV_FALLBACK", "valid-fallback-secret-32-bytes!")
            setActiveProfiles("prod")
        }
        val validator = ProductionCredentialsValidator(environment)

        val exception = shouldThrow<IllegalStateException> {
            validator.validateCredentials()
        }

        exception.message shouldContain "SMP_LOCAL_JWT_SECRET"
        exception.message shouldContain "only with the dev profile"
    }

    @Test
    fun `should reject SMP_LOCAL_JWT_DEV_FALLBACK when dev and prod are both active`() {
        val environment = validEnvironment().apply {
            setProperty("SMP_LOCAL_JWT_SECRET", "")
            setProperty("SMP_LOCAL_JWT_DEV_FALLBACK", "valid-fallback-secret-32-bytes!")
            setActiveProfiles("dev", "prod")
        }
        val validator = ProductionCredentialsValidator(environment)

        val exception = shouldThrow<IllegalStateException> {
            validator.validateCredentials()
        }

        exception.message shouldContain "SMP_LOCAL_JWT_DEV_FALLBACK"
        exception.message shouldContain "only with the dev profile"
    }

    @Test
    fun `should reject JWT placeholders with leading whitespace`() {
        val environment = validEnvironment().apply {
            setProperty("SMP_LOCAL_JWT_SECRET", "  CHANGE_ME_JWT_SECRET")
            setProperty("SMP_LOCAL_JWT_DEV_FALLBACK", "  CHANGE_ME_JWT_FALLBACK")
            setActiveProfiles("dev")
        }
        val validator = ProductionCredentialsValidator(environment)

        val exception = shouldThrow<IllegalStateException> {
            validator.validateCredentials()
        }

        exception.message shouldContain "SMP_LOCAL_JWT_SECRET"
        exception.message shouldContain "unsafe"
    }

    @Test
    fun `should skip validation in test profile`() {
        val environment = MockEnvironment().apply {
            // Intentionally use unsafe values
            setProperty("SMP_DB_PASSWORD", "CHANGE_ME")
            setProperty("PUBLISHING_CREDENTIALS_ENCRYPTION_KEY", "")
            setProperty("SMP_LOCAL_JWT_SECRET", "")
            setActiveProfiles("test")
        }
        val validator = ProductionCredentialsValidator(environment)

        // Should not throw (test profile bypasses validation)
        validator.validateCredentials()
    }

    @Test
    fun `should fail in dev profile when credentials are invalid`() {
        val environment = MockEnvironment().apply {
            setProperty("SMP_DB_PASSWORD", "CHANGE_ME")
            setProperty("PUBLISHING_CREDENTIALS_ENCRYPTION_KEY", "")
            setProperty("SMP_LOCAL_JWT_SECRET", "")
            setActiveProfiles("dev")
        }
        val validator = ProductionCredentialsValidator(environment)

        // Should throw even in dev profile (only test profile is exempt)
        shouldThrow<IllegalStateException> {
            validator.validateCredentials()
        }
    }

    @Test
    fun `should validate in plain JUnit test with MockEnvironment even if test-like properties are set`() {
        // Plain JUnit unit tests use MockEnvironment and call validateCredentials()
        // directly. The classpath-based test context detection (which relies on
        // JUnit being available on the classpath) is ONLY activated for real
        // Spring Boot environments, NOT for MockEnvironment. This ensures unit
        // tests thoroughly exercise the validation logic even when JUnit happens
        // to be on the classpath.
        val environment = MockEnvironment().apply {
            // Intentionally use unsafe values
            setProperty("SMP_DB_PASSWORD", "CHANGE_ME")
            setProperty("PUBLISHING_CREDENTIALS_ENCRYPTION_KEY", "")
            setProperty("SMP_LOCAL_JWT_SECRET", "")
            // Previous detection attempted to use this property, but it's never
            // exposed through the Environment by Spring Boot TestContext framework
            setProperty("spring.test.context.cache.maxSize", "32")
        }
        val validator = ProductionCredentialsValidator(environment)

        // Should throw — MockEnvironment is not a real Spring Boot environment
        shouldThrow<IllegalStateException> {
            validator.validateCredentials()
        }
    }

    @Test
    fun `should skip validation in BDD test context via bdd variant property`() {
        val environment = MockEnvironment().apply {
            // Intentionally use unsafe values
            setProperty("SMP_DB_PASSWORD", "CHANGE_ME")
            setProperty("PUBLISHING_CREDENTIALS_ENCRYPTION_KEY", "")
            setProperty("SMP_LOCAL_JWT_SECRET", "")
            // Simulate BDD test context (set in CucumberSpringConfiguration)
            setProperty("bdd.variant", "fast")
        }
        val validator = ProductionCredentialsValidator(environment)

        // Should not throw (BDD test context detected)
        validator.validateCredentials()
    }

    @Test
    fun `should fail when web application type is NONE and credentials are invalid`() {
        val environment = MockEnvironment().apply {
            // Intentionally use unsafe values
            setProperty("SMP_DB_PASSWORD", "CHANGE_ME")
            setProperty("PUBLISHING_CREDENTIALS_ENCRYPTION_KEY", "")
            setProperty("SMP_LOCAL_JWT_SECRET", "")
            // Simulate non-web production context (batch/worker app)
            setProperty("spring.main.web-application-type", "NONE")
        }
        val validator = ProductionCredentialsValidator(environment)

        // Should throw (NONE is a valid production config, not a test indicator)
        shouldThrow<IllegalStateException> {
            validator.validateCredentials()
        }
    }

    private fun validEnvironment() = MockEnvironment().apply {
        setProperty("SMP_DB_PASSWORD", "strong-random-password-at-least-32-chars")
        setProperty("PUBLISHING_CREDENTIALS_ENCRYPTION_KEY", "base64-encoded-32-byte-key-here")
        setProperty("SMP_LOCAL_JWT_SECRET", "valid-secret-minimum-32-bytes!")
        setProperty("SMP_MEDIA_PREVIEW_SIGNING_SECRET", "valid-media-preview-secret-32-bytes")
        setProperty("SMP_LINKEDIN_STATE_SIGNING_SECRET", "valid-linkedin-state-secret-32-bytes")
    }
}
