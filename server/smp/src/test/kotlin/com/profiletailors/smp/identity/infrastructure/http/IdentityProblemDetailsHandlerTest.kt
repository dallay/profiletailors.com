package com.profiletailors.smp.identity.infrastructure.http

import com.profiletailors.smp.identity.application.AuthFeature
import com.profiletailors.smp.identity.application.FeatureEmailVerificationRequired
import com.profiletailors.smp.identity.application.UnverifiedEmailException
import java.net.URI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpStatus

/**
 * Unit tests for [IdentityProblemDetailsHandler] exception-to-ProblemDetail mappings.
 *
 * Verifies RFC 9457 problem detail structure for email-verification-gated scenarios:
 * - UnverifiedEmailException → 403 with EMAIL_VERIFICATION_REQUIRED code
 * - FeatureEmailVerificationRequired → 403 with EMAIL_VERIFICATION_REQUIRED code
 */
class IdentityProblemDetailsHandlerTest {

    private val handler = IdentityProblemDetailsHandler()

    @ParameterizedTest
    @MethodSource("emailVerificationExceptions")
    fun `email verification exceptions map to RFC 9457 problem detail`(
        exception: UnverifiedEmailException,
    ) {
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
