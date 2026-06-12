package com.profiletailors.smp.identity.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.credentials.application.RefreshSessionCookieFactory
import com.profiletailors.smp.credentials.application.RefreshSessionFailureReason
import com.profiletailors.smp.credentials.application.RefreshSessionNotActiveException
import com.profiletailors.smp.credentials.application.RefreshSessionProperties
import com.profiletailors.smp.identity.application.AuthTokens
import com.profiletailors.smp.identity.application.LocalAuthSessionResult
import com.profiletailors.smp.identity.application.LoginUserCommand
import com.profiletailors.smp.identity.application.LogoutUserSessionCommand
import com.profiletailors.smp.identity.application.RefreshUserSessionCommand
import com.profiletailors.smp.identity.application.RegisterUserCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

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
    suspend fun register(
        @Valid @RequestBody request: RegisterUserRequest,
    ): ResponseEntity<AuthTokens> =
        sessionResponse(
            mediator.send(
                RegisterUserCommand(
                    email = request.email,
                    password = request.password,
                ),
            ),
        )

    @Operation(summary = "Authenticate user with email and password")
    @PostMapping("/login", consumes = ["application/json"], version = "1")
    suspend fun login(
        @Valid @RequestBody request: LoginUserRequest,
    ): ResponseEntity<AuthTokens> =
        sessionResponse(
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
    suspend fun logout(request: ServerHttpRequest): ResponseEntity<Void> {
        mediator.send(LogoutUserSessionCommand(readRefreshCookie(request)))
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, refreshSessionCookieFactory.buildClearCookie().toString())
            .build()
    }

    private fun sessionResponse(result: LocalAuthSessionResult): ResponseEntity<AuthTokens> =
        ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshSessionCookieFactory.buildSetCookie(result.refreshToken).toString())
            .body(result.tokens)

    private fun readRefreshCookie(request: ServerHttpRequest): String? =
        request.cookies.getFirst(refreshSessionProperties.cookieName)?.value
}

/**
 * Request body for user registration.
 *
 * Contains the required information to create a new user account.
 *
 * @property email User's email address (must be valid email format).
 * @property password User's password (must meet security requirements).
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
    @field:Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    @field:Schema(
        description = "User's password (minimum 8 characters)",
        example = "SecureP@ssw0rd",
        required = true,
        minLength = 8,
        maxLength = 128,
        format = "password",
    )
    val password: String,
)

/**
 * Request body for user login.
 *
 * Contains the credentials required to authenticate a user.
 *
 * @property email User's email address.
 * @property password User's password.
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
