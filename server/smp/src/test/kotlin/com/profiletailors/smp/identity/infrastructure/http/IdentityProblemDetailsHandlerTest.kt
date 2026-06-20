package com.profiletailors.smp.identity.infrastructure.http

import com.profiletailors.smp.identity.application.AuthFeature
import com.profiletailors.smp.identity.application.FeatureEmailVerificationRequired
import com.profiletailors.smp.identity.application.UnverifiedEmailException
import java.net.URI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
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

    @Test
    fun `UnverifiedEmailException returns 403 with structured problem detail`() {
        val exception = UnverifiedEmailException("test@example.com")
        val result = handler.handle(exception)

        assertEquals(HttpStatus.FORBIDDEN.value(), result.status)
        assertEquals("Email verification required", result.title)
        assertEquals(
            URI("https://api.profiletailors.com/errors/email-verification-required"),
            result.type,
        )
        assertEquals("Please verify your email before using this feature.", result.detail)
        assertEquals("EMAIL_VERIFICATION_REQUIRED", result.properties?.get("code"))
    }

    @Test
    fun `FeatureEmailVerificationRequired CONNECT_SOCIAL returns 403 with structured problem detail`() {
        val exception = FeatureEmailVerificationRequired(AuthFeature.CONNECT_SOCIAL)
        val result = handler.handle(exception)

        assertEquals(HttpStatus.FORBIDDEN.value(), result.status)
        assertEquals("Email verification required", result.title)
        assertEquals(
            URI("https://api.profiletailors.com/errors/email-verification-required"),
            result.type,
        )
        assertEquals("Please verify your email before using this feature.", result.detail)
        assertEquals("EMAIL_VERIFICATION_REQUIRED", result.properties?.get("code"))
    }

    @Test
    fun `FeatureEmailVerificationRequired PUBLISH_CONTENT returns 403 with structured problem detail`() {
        val exception = FeatureEmailVerificationRequired(AuthFeature.PUBLISH_CONTENT)
        val result = handler.handle(exception)

        assertEquals(HttpStatus.FORBIDDEN.value(), result.status)
        assertEquals("Email verification required", result.title)
        assertEquals(
            URI("https://api.profiletailors.com/errors/email-verification-required"),
            result.type,
        )
        assertEquals("Please verify your email before using this feature.", result.detail)
        assertEquals("EMAIL_VERIFICATION_REQUIRED", result.properties?.get("code"))
    }

    @Test
    fun `FeatureEmailVerificationRequired SCHEDULE_POST returns 403 with structured problem detail`() {
        val exception = FeatureEmailVerificationRequired(AuthFeature.SCHEDULE_POST)
        val result = handler.handle(exception)

        assertEquals(HttpStatus.FORBIDDEN.value(), result.status)
        assertEquals("Email verification required", result.title)
        assertEquals(
            URI("https://api.profiletailors.com/errors/email-verification-required"),
            result.type,
        )
        assertEquals("Please verify your email before using this feature.", result.detail)
        assertEquals("EMAIL_VERIFICATION_REQUIRED", result.properties?.get("code"))
    }

    @Test
    fun `UnverifiedEmailException problem detail has correct RFC 9457 structure`() {
        val exception = UnverifiedEmailException("user@profiletailors.com")
        val problemDetail = handler.handle(exception)

        // RFC 9457 requires: type, title, status (derived from status)
        assertEquals(HttpStatus.FORBIDDEN.value(), problemDetail.status)
        assertEquals("Email verification required", problemDetail.title)
        assertEquals(
            URI("https://api.profiletailors.com/errors/email-verification-required"),
            problemDetail.type,
        )
        // Custom property: code
        assertEquals("EMAIL_VERIFICATION_REQUIRED", problemDetail.properties?.get("code"))
        // detail
        assertEquals("Please verify your email before using this feature.", problemDetail.detail)
    }
}
