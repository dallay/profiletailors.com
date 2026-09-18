package com.profiletailors.smp.identity.infrastructure.http

import com.profiletailors.smp.identity.application.ExpiredPasswordResetTokenException
import com.profiletailors.smp.identity.application.InvalidPasswordResetTokenException
import com.profiletailors.smp.identity.application.PasswordRecoveryDisabledException
import com.profiletailors.smp.identity.application.RegistrationDisabledException
import com.profiletailors.smp.identity.application.RegistrationInvitationRequiredException
import com.profiletailors.smp.identity.application.UsedPasswordResetTokenException
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
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

        result.status shouldBe HttpStatus.SERVICE_UNAVAILABLE.value()
        result.title shouldBe "Registration disabled"
        result.type shouldBe URI("/problems/registration-disabled")
        result.detail shouldBe "Registration is not available."
        result.properties?.get("code") shouldBe "REGISTRATION_DISABLED"
    }

    @Test
    fun `invitation required maps to exact problem detail`() {
        val result = handler.handle(RegistrationInvitationRequiredException())

        result.status shouldBe HttpStatus.FORBIDDEN.value()
        result.title shouldBe "Invitation required"
        result.type shouldBe URI("/problems/registration-invitation-required")
        result.detail shouldBe "A valid invitation is required to register."
        result.properties?.get("code") shouldBe "REGISTRATION_INVITATION_REQUIRED"
    }

    @Test
    fun `workspace override maps to a generic invitation problem detail`() {
        val result = handler.handle()

        result.status shouldBe HttpStatus.BAD_REQUEST.value()
        result.title shouldBe "Invalid invitation workspace selection"
        result.type shouldBe URI("/problems/invitation-workspace-override")
        result.detail shouldBe "Workspace selection is not allowed for invitation registration."
        result.properties?.get("code") shouldBe "INVITATION_WORKSPACE_OVERRIDE_NOT_ALLOWED"
        result.detail?.contains("workspace-id", ignoreCase = true) shouldBe false
    }

    @Test
    fun `invalid credentials map to generic problem detail`() {
        val result = handler.handleInvalidEmailPassword()

        result.status shouldBe HttpStatus.UNAUTHORIZED.value()
        result.title shouldBe "Invalid credentials"
        result.detail shouldBe "Invalid email or password."
    }

    @Test
    fun `user already exists omits email from problem detail`() {
        val result = handler.handleUserAlreadyExists()

        result.status shouldBe HttpStatus.CONFLICT.value()
        result.title shouldBe "User already exists"
        result.detail shouldBe "Unable to complete registration with the provided credentials."
        result.properties?.get("code") shouldBe "USER_ALREADY_EXISTS"
        result.properties?.get("email").shouldBeNull()
    }

    @Test
    fun `invalid registration input maps to generic problem detail`() {
        val result = handler.handleInvalidRegistrationInput()

        result.status shouldBe HttpStatus.BAD_REQUEST.value()
        result.title shouldBe "Invalid registration input"
        result.detail shouldBe "Registration request is invalid."
    }

    @Test
    fun `registration validation maps to generic problem detail`() {
        val result = handler.handleRegistrationValidation()

        result.status shouldBe HttpStatus.UNPROCESSABLE_CONTENT.value()
        result.title shouldBe "Registration validation failed"
        result.detail shouldBe "Registration validation failed."
    }

    @Test
    fun `invalid verification token maps to generic problem detail`() {
        val result = handler.handleInvalidVerificationToken()

        result.status shouldBe HttpStatus.BAD_REQUEST.value()
        result.title shouldBe "Invalid verification token"
        result.detail shouldBe "Invalid verification token."
    }

    @Test
    fun `unverified email maps to RFC 9457 problem detail`() {
        val result = handler.handleUnverifiedEmail()

        result.status shouldBe HttpStatus.FORBIDDEN.value()
        result.title shouldBe "Email verification required"
        result.type shouldBe URI("https://api.profiletailors.com/errors/email-verification-required")
        result.detail shouldBe "Please verify your email before using this feature."
        result.properties?.get("code") shouldBe "EMAIL_VERIFICATION_REQUIRED"
    }

    @Test
    fun `feature email verification maps to RFC 9457 problem detail`() {
        val result = handler.handleFeatureEmailRequired()

        result.status shouldBe HttpStatus.FORBIDDEN.value()
        result.title shouldBe "Email verification required"
        result.type shouldBe URI("https://api.profiletailors.com/errors/email-verification-required")
        result.detail shouldBe "Please verify your email before using this feature."
        result.properties?.get("code") shouldBe "EMAIL_VERIFICATION_REQUIRED"
    }

    @Test
    fun `web exchange bind failure maps to validation problem detail`() {
        val result = handler.handleWebExchangeBind()

        result.status shouldBe HttpStatus.BAD_REQUEST.value()
        result.title shouldBe "Validation failed"
        result.detail shouldBe "Validation failure"
        result.properties?.get("code") shouldBe "VALIDATION_ERROR"
    }

    @Test
    fun `server web input failure maps to invalid request problem detail`() {
        val result = handler.handleServerWebInput()

        result.status shouldBe HttpStatus.BAD_REQUEST.value()
        result.title shouldBe "Invalid request"
        result.detail shouldBe "Validation failure"
        result.properties?.get("code") shouldBe "VALIDATION_ERROR"
    }

    @Test
    fun `password recovery disabled maps to service unavailable`() {
        val result = handler.handle(PasswordRecoveryDisabledException())

        result.status shouldBe HttpStatus.SERVICE_UNAVAILABLE.value()
        result.title shouldBe "Password recovery disabled"
        result.detail shouldBe "Password recovery is disabled."
        result.properties?.get("code") shouldBe "PASSWORD_RECOVERY_DISABLED"
    }

    @Test
    fun `password reset token errors retain distinct codes and identical public detail`() {
        val invalid = handler.handle(InvalidPasswordResetTokenException())
        val expired = handler.handle(ExpiredPasswordResetTokenException())
        val used = handler.handle(UsedPasswordResetTokenException())

        invalid.properties?.get("code") shouldBe "INVALID_PASSWORD_RESET_TOKEN"
        expired.properties?.get("code") shouldBe "EXPIRED_PASSWORD_RESET_TOKEN"
        used.properties?.get("code") shouldBe "USED_PASSWORD_RESET_TOKEN"
        setOf(invalid.detail, expired.detail, used.detail).size shouldBe 1
    }

    @Test
    fun `close account confirmation maps to redacted generic problem detail`() {
        val result = handler.handleCloseConfirmation()

        result.status shouldBe HttpStatus.BAD_REQUEST.value()
        result.title shouldBe "Invalid account closure confirmation"
        result.detail shouldBe "Account closure confirmation is invalid."
    }

    @Test
    fun `close account rate limit maps to redacted generic problem detail`() {
        val result = handler.handleAccountClosureRateLimit()

        result.status shouldBe HttpStatus.TOO_MANY_REQUESTS.value()
        result.title shouldBe "Account closure rate limit exceeded"
        result.type shouldBe URI("https://api.profiletailors.com/errors/account-closure-rate-limit")
        result.detail shouldBe "Account closure rate limit exceeded."
        result.properties?.get("code") shouldBe "ACCOUNT_CLOSURE_RATE_LIMIT"
    }
}
