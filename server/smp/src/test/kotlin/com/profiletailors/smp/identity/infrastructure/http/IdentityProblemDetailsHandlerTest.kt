package com.profiletailors.smp.identity.infrastructure.http

import com.profiletailors.smp.identity.application.AuthFeature
import com.profiletailors.smp.identity.application.FeatureEmailVerificationRequired
import com.profiletailors.smp.identity.application.InvalidEmailPasswordException
import com.profiletailors.smp.identity.application.InvalidRegistrationInputException
import com.profiletailors.smp.identity.application.InvalidVerificationTokenException
import com.profiletailors.smp.identity.application.RegistrationDisabledException
import com.profiletailors.smp.identity.application.RegistrationValidationException
import com.profiletailors.smp.identity.application.UnverifiedEmailException
import com.profiletailors.smp.identity.application.UserAlreadyExistsException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpStatus
import java.net.URI

/**
 * Unit tests for [IdentityProblemDetailsHandler] exception-to-ProblemDetail mappings.
 *
 * Verifies RFC 9457 problem detail structure for email-verification-gated scenarios:
 * - UnverifiedEmailException → 403 with EMAIL_VERIFICATION_REQUIRED code
 * - FeatureEmailVerificationRequired → 403 with EMAIL_VERIFICATION_REQUIRED code
 */
class IdentityProblemDetailsHandlerTest {

    private val handler = IdentityProblemDetailsHandler()

    @Test
    fun `registration disabled maps to exact problem detail`() {
        val result = handler.handle(RegistrationDisabledException())

        assertEquals(HttpStatus.FORBIDDEN.value(), result.status)
        assertEquals("Registration disabled", result.title)
        assertEquals(URI("/problems/registration-disabled"), result.type)
        assertEquals("Registration is not available.", result.detail)
        assertEquals("registration_disabled", result.properties?.get("code"))
    }

    @Test
    fun `invalid credentials map to generic problem detail`() {
        val result = handler.handle(InvalidEmailPasswordException())

        assertEquals(HttpStatus.UNAUTHORIZED.value(), result.status)
        assertEquals("Invalid credentials", result.title)
        assertEquals("Invalid email or password.", result.detail)
    }

    @Test
    fun `user already exists omits email from problem detail`() {
        val result = handler.handle(UserAlreadyExistsException("test@example.com"))

        assertEquals(HttpStatus.CONFLICT.value(), result.status)
        assertEquals("User already exists", result.title)
        assertEquals("Unable to complete registration with the provided credentials.", result.detail)
        assertEquals("USER_ALREADY_EXISTS", result.properties?.get("code"))
    }

    @Test
    fun `invalid registration input maps to generic problem detail`() {
        val result = handler.handle(InvalidRegistrationInputException("email format leaked"))

        assertEquals(HttpStatus.BAD_REQUEST.value(), result.status)
        assertEquals("Invalid registration input", result.title)
        assertEquals("Registration request is invalid.", result.detail)
    }

    @Test
    fun `registration validation maps to generic problem detail`() {
        val result = handler.handle(RegistrationValidationException("password policy leaked"))

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), result.status)
        assertEquals("Registration validation failed", result.title)
        assertEquals("Registration validation failed.", result.detail)
    }

    @Test
    fun `invalid verification token maps to generic problem detail`() {
        val result = handler.handle(InvalidVerificationTokenException("expired token details leaked"))

        assertEquals(HttpStatus.BAD_REQUEST.value(), result.status)
        assertEquals("Invalid verification token", result.title)
        assertEquals("Invalid verification token.", result.detail)
    }

    @ParameterizedTest
    @MethodSource("emailVerificationExceptions")
    fun `email verification exceptions map to RFC 9457 problem detail`(exception: UnverifiedEmailException) {
        assertProblemDetail(handler.handle(exception))
    }

    @ParameterizedTest
    @MethodSource("featureExceptions")
    fun `feature email verification exceptions map to RFC 9457 problem detail`(
        exception: FeatureEmailVerificationRequired,
    ) {
        assertProblemDetail(handler.handle(exception))
    }

    private fun assertProblemDetail(result: org.springframework.http.ProblemDetail) {
        assertEquals(HttpStatus.FORBIDDEN.value(), result.status)
        assertEquals("Email verification required", result.title)
        assertEquals(
            URI("https://api.profiletailors.com/errors/email-verification-required"),
            result.type,
        )
        assertEquals("Please verify your email before using this feature.", result.detail)
        assertEquals("EMAIL_VERIFICATION_REQUIRED", result.properties?.get("code"))
    }

    companion object {
        @JvmStatic
        fun emailVerificationExceptions() = listOf(
            Arguments.of(UnverifiedEmailException("test@example.com")),
        )

        @JvmStatic
        fun featureExceptions() = listOf(
            Arguments.of(FeatureEmailVerificationRequired(AuthFeature.CONNECT_SOCIAL)),
            Arguments.of(FeatureEmailVerificationRequired(AuthFeature.PUBLISH_CONTENT)),
            Arguments.of(FeatureEmailVerificationRequired(AuthFeature.SCHEDULE_POST)),
        )
    }
}
