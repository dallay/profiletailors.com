package com.profiletailors.smp.platform.infrastructure.security

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
    fun `should fail when SMP_DB_PASSWORD is CHANGE_ME_gK2fcFZg5cgVu9U`() {
        val environment = MockEnvironment().apply {
            setProperty("SMP_DB_PASSWORD", "CHANGE_ME_gK2fcFZg5cgVu9U")
            setProperty("PUBLISHING_CREDENTIALS_KEY", "valid-key-here")
            setProperty("SMP_LOCAL_JWT_SECRET", "valid-secret-32-bytes-minimum!")
        }
        val validator = ProductionCredentialsValidator(environment)

        val exception = assertThrows<IllegalStateException> {
            validator.validateCredentials()
        }

        assert(exception.message!!.contains("SMP_DB_PASSWORD"))
        assert(exception.message!!.contains("CHANGE_ME_gK2fcFZg5cgVu9U"))
    }

    @Test
    fun `should fail when SMP_DB_PASSWORD is blank`() {
        val environment = MockEnvironment().apply {
            setProperty("SMP_DB_PASSWORD", "")
            setProperty("PUBLISHING_CREDENTIALS_KEY", "valid-key-here")
            setProperty("SMP_LOCAL_JWT_SECRET", "valid-secret-32-bytes-minimum!")
        }
        val validator = ProductionCredentialsValidator(environment)

        val exception = assertThrows<IllegalStateException> {
            validator.validateCredentials()
        }

        assert(exception.message!!.contains("SMP_DB_PASSWORD"))
    }

    @Test
    fun `should fail when PUBLISHING_CREDENTIALS_KEY is blank`() {
        val environment = MockEnvironment().apply {
            setProperty("SMP_DB_PASSWORD", "strong-password-here")
            setProperty("PUBLISHING_CREDENTIALS_KEY", "")
            setProperty("SMP_LOCAL_JWT_SECRET", "valid-secret-32-bytes-minimum!")
        }
        val validator = ProductionCredentialsValidator(environment)

        val exception = assertThrows<IllegalStateException> {
            validator.validateCredentials()
        }

        assert(exception.message!!.contains("PUBLISHING_CREDENTIALS_KEY"))
        assert(exception.message!!.contains("OAuth"))
    }

    @Test
    fun `should fail when both SMP_LOCAL_JWT_SECRET and SMP_LOCAL_JWT_DEV_FALLBACK are blank`() {
        val environment = MockEnvironment().apply {
            setProperty("SMP_DB_PASSWORD", "strong-password-here")
            setProperty("PUBLISHING_CREDENTIALS_KEY", "valid-key-here")
            setProperty("SMP_LOCAL_JWT_SECRET", "")
            setProperty("SMP_LOCAL_JWT_DEV_FALLBACK", "")
        }
        val validator = ProductionCredentialsValidator(environment)

        val exception = assertThrows<IllegalStateException> {
            validator.validateCredentials()
        }

        assert(exception.message!!.contains("SMP_LOCAL_JWT_SECRET"))
        assert(exception.message!!.contains("SMP_LOCAL_JWT_DEV_FALLBACK"))
    }

    @Test
    fun `should fail with multiple violations when multiple secrets are invalid`() {
        val environment = MockEnvironment().apply {
            setProperty("SMP_DB_PASSWORD", "CHANGE_ME_gK2fcFZg5cgVu9U")
            setProperty("PUBLISHING_CREDENTIALS_KEY", "")
            setProperty("SMP_LOCAL_JWT_SECRET", "")
            setProperty("SMP_LOCAL_JWT_DEV_FALLBACK", "")
        }
        val validator = ProductionCredentialsValidator(environment)

        val exception = assertThrows<IllegalStateException> {
            validator.validateCredentials()
        }

        assert(exception.message!!.contains("SMP_DB_PASSWORD"))
        assert(exception.message!!.contains("PUBLISHING_CREDENTIALS_KEY"))
        assert(exception.message!!.contains("SMP_LOCAL_JWT_SECRET"))
    }

    @Test
    fun `should pass when all credentials are properly configured`() {
        val environment = MockEnvironment().apply {
            setProperty("SMP_DB_PASSWORD", "strong-random-password-32-chars")
            setProperty("PUBLISHING_CREDENTIALS_KEY", "base64-encoded-32-byte-key-here")
            setProperty("SMP_LOCAL_JWT_SECRET", "valid-secret-minimum-32-bytes!")
        }
        val validator = ProductionCredentialsValidator(environment)

        // Should not throw
        validator.validateCredentials()
    }

    @Test
    fun `should pass when SMP_LOCAL_JWT_DEV_FALLBACK is set instead of SMP_LOCAL_JWT_SECRET`() {
        val environment = MockEnvironment().apply {
            setProperty("SMP_DB_PASSWORD", "strong-random-password-32-chars")
            setProperty("PUBLISHING_CREDENTIALS_KEY", "base64-encoded-32-byte-key-here")
            setProperty("SMP_LOCAL_JWT_SECRET", "")
            setProperty("SMP_LOCAL_JWT_DEV_FALLBACK", "valid-fallback-secret-32-bytes!")
        }
        val validator = ProductionCredentialsValidator(environment)

        // Should not throw (fallback is acceptable)
        validator.validateCredentials()
    }

    @Test
    fun `should skip validation in test profile`() {
        val environment = MockEnvironment().apply {
            // Intentionally use unsafe values
            setProperty("SMP_DB_PASSWORD", "CHANGE_ME_gK2fcFZg5cgVu9U")
            setProperty("PUBLISHING_CREDENTIALS_KEY", "")
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
            setProperty("SMP_DB_PASSWORD", "CHANGE_ME_gK2fcFZg5cgVu9U")
            setProperty("PUBLISHING_CREDENTIALS_KEY", "")
            setProperty("SMP_LOCAL_JWT_SECRET", "")
            setActiveProfiles("dev")
        }
        val validator = ProductionCredentialsValidator(environment)

        // Should throw even in dev profile (only test profile is exempt)
        assertThrows<IllegalStateException> {
            validator.validateCredentials()
        }
    }

    @Test
    fun `should skip validation in Spring Boot test context via test cache property`() {
        val environment = MockEnvironment().apply {
            // Intentionally use unsafe values
            setProperty("SMP_DB_PASSWORD", "CHANGE_ME_gK2fcFZg5cgVu9U")
            setProperty("PUBLISHING_CREDENTIALS_KEY", "")
            setProperty("SMP_LOCAL_JWT_SECRET", "")
            // Simulate Spring Boot test context (set by @SpringBootTest)
            setProperty("spring.test.context.cache.maxSize", "32")
        }
        val validator = ProductionCredentialsValidator(environment)

        // Should not throw (test context detected)
        validator.validateCredentials()
    }

    @Test
    fun `should skip validation in BDD test context via bdd variant property`() {
        val environment = MockEnvironment().apply {
            // Intentionally use unsafe values
            setProperty("SMP_DB_PASSWORD", "CHANGE_ME_gK2fcFZg5cgVu9U")
            setProperty("PUBLISHING_CREDENTIALS_KEY", "")
            setProperty("SMP_LOCAL_JWT_SECRET", "")
            // Simulate BDD test context (set in CucumberSpringConfiguration)
            setProperty("bdd.variant", "fast")
        }
        val validator = ProductionCredentialsValidator(environment)

        // Should not throw (BDD test context detected)
        validator.validateCredentials()
    }

    @Test
    fun `should skip validation when web application type is NONE`() {
        val environment = MockEnvironment().apply {
            // Intentionally use unsafe values
            setProperty("SMP_DB_PASSWORD", "CHANGE_ME_gK2fcFZg5cgVu9U")
            setProperty("PUBLISHING_CREDENTIALS_KEY", "")
            setProperty("SMP_LOCAL_JWT_SECRET", "")
            // Simulate non-web test context
            setProperty("spring.main.web-application-type", "NONE")
        }
        val validator = ProductionCredentialsValidator(environment)

        // Should not throw (non-web test context detected)
        validator.validateCredentials()
    }
}
