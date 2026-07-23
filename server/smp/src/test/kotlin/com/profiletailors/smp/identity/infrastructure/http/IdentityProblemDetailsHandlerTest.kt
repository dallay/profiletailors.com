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
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
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

        result.status shouldBe HttpStatus.FORBIDDEN.value()
        result.title shouldBe "Registration disabled"
        result.type shouldBe URI("/problems/registration-disabled")
        result.detail shouldBe "Registration is not available."
        result.properties?.get("code") shouldBe "registration_disabled"
    }

    @Test
    fun `invalid credentials map to generic problem detail`() {
        val result = handler.handle(InvalidEmailPasswordException())

        result.status shouldBe HttpStatus.UNAUTHORIZED.value()
        result.title shouldBe "Invalid credentials"
        result.detail shouldBe "Invalid email or password."
    }

    @Test
    fun `user already exists omits email from problem detail`() {
        val result = handler.handle(UserAlreadyExistsException("test@example.com"))

        result.status shouldBe HttpStatus.CONFLICT.value()
        result.title shouldBe "User already exists"
        result.detail shouldBe "Unable to complete registration with the provided credentials."
        result.properties?.get("code") shouldBe "USER_ALREADY_EXISTS"
        result.properties?.get("email").shouldBeNull()
    }

    @Test
    fun `invalid registration input maps to generic problem detail`() {
        val result = handler.handle(InvalidRegistrationInputException("email format leaked"))

        result.status shouldBe HttpStatus.BAD_REQUEST.value()
        result.title shouldBe "Invalid registration input"
        result.detail shouldBe "Registration request is invalid."
    }

    @Test
    fun `registration validation maps to generic problem detail`() {
        val result = handler.handle(RegistrationValidationException("password policy leaked"))

        result.status shouldBe HttpStatus.UNPROCESSABLE_ENTITY.value()
        result.title shouldBe "Registration validation failed"
        result.detail shouldBe "Registration validation failed."
    }

    @Test
    fun `invalid verification token maps to generic problem detail`() {
        val result = handler.handle(InvalidVerificationTokenException("expired token details leaked"))

        result.status shouldBe HttpStatus.BAD_REQUEST.value()
        result.title shouldBe "Invalid verification token"
        result.detail shouldBe "Invalid verification token."
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
        result.status shouldBe HttpStatus.FORBIDDEN.value()
        result.title shouldBe "Email verification required"
        result.type shouldBe URI("https://api.profiletailors.com/errors/email-verification-required")
        result.detail shouldBe "Please verify your email before using this feature."
        result.properties?.get("code") shouldBe "EMAIL_VERIFICATION_REQUIRED"
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
