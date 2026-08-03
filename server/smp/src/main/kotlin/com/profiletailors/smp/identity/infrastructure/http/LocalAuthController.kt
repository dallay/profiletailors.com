package com.profiletailors.smp.identity.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.credentials.application.RefreshSessionFailureReason
import com.profiletailors.smp.credentials.application.RefreshSessionNotActiveException
import com.profiletailors.smp.credentials.application.RefreshSessionProperties
import com.profiletailors.smp.credentials.domain.SessionCookie
import com.profiletailors.smp.credentials.infrastructure.RefreshSessionCookieFactory
import com.profiletailors.smp.identity.application.AuthTokens
import com.profiletailors.smp.identity.application.LocalAuthSessionResult
import com.profiletailors.smp.identity.application.LoginUserCommand
import com.profiletailors.smp.identity.application.LogoutUserSessionCommand
import com.profiletailors.smp.identity.application.RefreshUserSessionCommand
import com.profiletailors.smp.identity.application.RegisterUserCommand
import com.profiletailors.smp.identity.application.RequestPasswordResetCommand
import com.profiletailors.smp.identity.application.ResendVerificationCommand
import com.profiletailors.smp.identity.application.ResetPasswordCommand
import com.profiletailors.smp.identity.application.VerifyEmailCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@Validated
@RestController
@RequestMapping(value = ["/api/auth"])
@Tag(name = "Authentication", description = "Local authentication endpoints")
class LocalAuthController(
    private val mediator: Mediator,
    private val refreshSessionCookieFactory: RefreshSessionCookieFactory,
    private val refreshSessionProperties: RefreshSessionProperties,
) {

    @Operation(summary = "Register a new user account")
    @PostMapping("/register", consumes = ["application/json"], version = "1")
    suspend fun register(@Valid @RequestBody request: RegisterUserRequest): ResponseEntity<AuthTokens> {
        val result = mediator.send(
            RegisterUserCommand(
                email = request.email,
                password = request.password,
                confirmedAgeEligibility = request.confirmedAgeEligibility,
                acceptedTermsVersion = request.acceptedTermsVersion,
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED)
            .header(
                HttpHeaders.SET_COOKIE,
                refreshSessionCookieFactory.buildSetCookie(result.refreshToken).toResponseCookie().toString(),
            )
            .body(result.tokens)
    }

    @Operation(summary = "Authenticate user with email and password")
    @PostMapping("/login", consumes = ["application/json"], version = "1")
    suspend fun login(@Valid @RequestBody request: LoginUserRequest): ResponseEntity<AuthTokens> = sessionResponse(
        mediator.send(
            LoginUserCommand(
                email = request.email,
                password = request.password,
            ),
        ),
    )

    @Operation(summary = "Refresh user session")
    @PostMapping("/refresh", version = "1")
    suspend fun refresh(request: ServerHttpRequest): ResponseEntity<AuthTokens> {
        val result = mediator.send(
            RefreshUserSessionCommand(
                rawRefreshToken = readRefreshCookie(request) ?: throw RefreshSessionNotActiveException(
                    lookupKey = "missing",
                    reason = RefreshSessionFailureReason.MISSING,
                ),
            ),
        )
        return sessionResponse(result)
    }

    @Operation(summary = "Logout user and invalidate session")
    @PostMapping("/logout", version = "1")
    suspend fun logout(request: ServerHttpRequest): ResponseEntity<Unit> {
        mediator.send(LogoutUserSessionCommand(readRefreshCookie(request)))
        return ResponseEntity.noContent()
            .header(
                HttpHeaders.SET_COOKIE,
                refreshSessionCookieFactory.buildClearCookie().toResponseCookie().toString(),
            )
            .build()
    }

    @Operation(summary = "Verify email address using verification token")
    @PostMapping("/verify-email", consumes = ["application/json"], version = "1")
    suspend fun verifyEmail(@Valid @RequestBody request: VerifyEmailRequest): ResponseEntity<AuthTokens> =
        sessionResponse(
            mediator.send(VerifyEmailCommand(token = request.token)),
        )

    @Operation(summary = "Resend verification email")
    @PostMapping("/resend-verification", consumes = ["application/json"], version = "1")
    suspend fun resendVerification(@Valid @RequestBody request: ResendVerificationRequest): ResponseEntity<Unit> {
        mediator.send(
            ResendVerificationCommand(
                email = request.email,
            ),
        )
        return ResponseEntity.accepted().build()
    }

    @Operation(summary = "Request a password reset email")
    @PostMapping("/forgot-password", consumes = ["application/json"], version = "1")
    suspend fun forgotPassword(
        @Valid @RequestBody request: ForgotPasswordRequest,
        @RequestHeader(HttpHeaders.ACCEPT_LANGUAGE, required = false) acceptLanguage: String?,
    ): ResponseEntity<Unit> {
        mediator.send(
            RequestPasswordResetCommand(
                email = request.email,
                locale = preferredLocale(acceptLanguage),
            ),
        )
        return ResponseEntity.accepted().build()
    }

    @Operation(summary = "Reset a password using a recovery token")
    @PostMapping("/reset-password", consumes = ["application/json"], version = "1")
    suspend fun resetPassword(@Valid @RequestBody request: ResetPasswordRequest): ResponseEntity<Unit> {
        mediator.send(
            ResetPasswordCommand(
                token = request.token,
                newPassword = request.newPassword,
            ),
        )
        return ResponseEntity.noContent().build()
    }

    private fun sessionResponse(result: LocalAuthSessionResult): ResponseEntity<AuthTokens> = ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE,
            refreshSessionCookieFactory.buildSetCookie(result.refreshToken).toResponseCookie().toString(),
        )
        .body(result.tokens)

    private fun readRefreshCookie(request: ServerHttpRequest): String? =
        request.cookies.getFirst(refreshSessionProperties.cookieName)?.value

    private fun preferredLocale(acceptLanguage: String?): String =
        if (acceptLanguage?.trim()?.lowercase()?.startsWith("es") == true) "es" else "en"

    private fun SessionCookie.toResponseCookie(): ResponseCookie = ResponseCookie.from(name, value)
        .httpOnly(httpOnly)
        .secure(secure)
        .sameSite(sameSite)
        .path(path)
        .maxAge(Duration.ofSeconds(maxAgeSeconds))
        .build()
}

/**
 * Request body for user registration.
 */
@Schema(description = "User registration request")
data class RegisterUserRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    @field:Schema(
        description = "User's email address",
        example = "user@example.com",
        required = true,
        format = "email",
    )
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 12, max = 128, message = "Password must be between 12 and 128 characters")
    @field:Schema(
        description = "User's password (minimum 12 characters)",
        example = "SecureP@ssw0rd",
        required = true,
        minLength = 12,
        maxLength = 128,
        format = "password",
    )
    val password: String,

    @field:AssertTrue(message = "You must confirm you are 18 or older")
    @field:Schema(
        description = "Confirmation that the user is 18 years of age or older",
        example = "true",
        required = true,
    )
    val confirmedAgeEligibility: Boolean,

    @field:NotBlank(message = "You must accept the terms of service")
    @field:Schema(
        description = "Accepted terms version identifier",
        example = "terms-v1.0.0",
        required = true,
    )
    val acceptedTermsVersion: String,
)

/**
 * Request body for user login.
 */
@Schema(description = "User login request")
data class LoginUserRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    @field:Schema(
        description = "User's email address",
        example = "user@example.com",
        required = true,
        format = "email",
    )
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Schema(
        description = "User's password",
        example = "SecureP@ssw0rd",
        required = true,
        format = "password",
    )
    val password: String,
)

/**
 * Request body for resending verification email.
 */
@Schema(description = "Resend verification email request")
data class ResendVerificationRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    @field:Schema(
        description = "User's email address",
        example = "user@example.com",
        required = true,
        format = "email",
    )
    val email: String,
)

/**
 * Request body for email verification.
 */
@Schema(description = "Email verification request")
data class VerifyEmailRequest(
    @field:NotBlank(message = "Verification token is required")
    @field:Schema(
        description = "Email verification token",
        example = "abc123def456",
        required = true,
    )
    val token: String,
)

@Schema(description = "Password reset request")
data class ForgotPasswordRequest(
    @field:NotBlank(message = "Email is required")
    @field:Pattern(
        regexp = """\s*[^@\s]+@[^@\s]+\.[^@\s]+\s*""",
        message = "Email must be valid",
    )
    @field:Schema(description = "Account email address", example = "user@example.com", required = true)
    val email: String,
)

@Schema(description = "Password reset completion request")
data class ResetPasswordRequest(
    @field:NotBlank(message = "Reset token is required")
    @field:Schema(description = "Raw password reset token", required = true)
    val token: String,

    @field:Schema(
        description = "New account password",
        required = true,
        minLength = 12,
        maxLength = 128,
        format = "password",
    )
    val newPassword: String,
)
