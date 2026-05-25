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
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for local authentication operations.
 *
 * This controller handles user registration, login, session refresh, and logout operations
 * using email/password credentials. It manages JWT access tokens and HTTP-only refresh tokens
 * stored in secure cookies.
 *
 * Uses media-type versioning via Accept header: application/vnd.api.v1+json
 *
 * ## Security Features:
 * - Password-based authentication with secure hashing
 * - JWT access tokens for API authentication
 * - HTTP-only secure cookies for refresh tokens
 * - Automatic session refresh mechanism
 * - Secure logout with cookie clearing
 *
 * @property mediator The mediator for dispatching commands.
 * @property refreshSessionCookieFactory Factory for creating secure refresh token cookies.
 * @property refreshSessionProperties Configuration properties for refresh sessions.
 * @created 2026-05-24
 */
@Validated
@RestController
@RequestMapping(value = ["/api/auth"])
@Tag(
    name = "Authentication",
    description = "Local authentication endpoints for user registration, login, and session management",
)
class LocalAuthController(
    private val mediator: Mediator,
    private val refreshSessionCookieFactory: RefreshSessionCookieFactory,
    private val refreshSessionProperties: RefreshSessionProperties,
) {

    /**
     * Register a new user account.
     *
     * Creates a new user account with email and password credentials. Optionally accepts a username.
     * Upon successful registration, returns JWT access tokens and sets a secure HTTP-only cookie
     * with the refresh token.
     *
     * @param request The registration request containing email, password, and optional username.
     * @return ResponseEntity with HTTP 200 OK and authentication tokens.
     */
    @Operation(
        summary = "Register a new user account",
        description = "Creates a new user account with email/password credentials. Returns JWT access tokens " +
            "and sets a secure HTTP-only refresh token cookie. The password must meet security requirements.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Registration successful - User account created and tokens returned",
                content = [Content(schema = Schema(implementation = AuthTokens::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request data - Email format invalid or password does not meet requirements",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "Conflict - Email already registered",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error during registration",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
        ],
    )
    @PostMapping("/register", consumes = ["application/json"], version = "1")
    suspend fun register(
        @Valid @RequestBody request: RegisterUserRequest,
    ): ResponseEntity<AuthTokens> =
        sessionResponse(
            mediator.send(
                RegisterUserCommand(
                    email = request.email,
                    password = request.password,
                    username = request.username,
                ),
            ),
        )

    /**
     * Authenticate user with email and password.
     *
     * Validates user credentials and returns JWT access tokens if authentication succeeds.
     * Sets a secure HTTP-only cookie with the refresh token for session management.
     *
     * @param request The login request containing email and password.
     * @return ResponseEntity with HTTP 200 OK and authentication tokens.
     */
    @Operation(
        summary = "Authenticate user with email and password",
        description = "Validates user credentials and returns JWT access tokens upon successful authentication. " +
            "Sets a secure HTTP-only refresh token cookie for session management.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Authentication successful - Tokens returned",
                content = [Content(schema = Schema(implementation = AuthTokens::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request data - Email format invalid",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized - Invalid credentials",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error during authentication",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
        ],
    )
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

    /**
     * Refresh user session using refresh token cookie.
     *
     * Validates the refresh token from the HTTP-only cookie and issues new JWT access tokens
     * if the refresh token is valid and not expired. Updates the refresh token cookie.
     *
     * @param request The HTTP request containing the refresh token cookie.
     * @return ResponseEntity with HTTP 200 OK and new authentication tokens.
     */
    @Operation(
        summary = "Refresh user session",
        description = "Validates the refresh token from the HTTP-only cookie and issues new JWT access tokens. " +
            "Updates the refresh token cookie with a new token.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Session refreshed successfully - New tokens returned",
                content = [Content(schema = Schema(implementation = AuthTokens::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized - Refresh token missing, invalid, or expired",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error during session refresh",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
        ],
    )
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

    /**
     * Logout user and invalidate session.
     *
     * Invalidates the current refresh token and clears the refresh token cookie.
     * The client should discard the access token after receiving this response.
     *
     * @param request The HTTP request containing the refresh token cookie.
     * @return ResponseEntity with HTTP 204 No Content.
     */
    @Operation(
        summary = "Logout user and invalidate session",
        description = "Invalidates the current refresh token and clears the refresh token cookie. " +
            "The client should discard the access token after logout.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204",
                description = "Logout successful - Session invalidated and cookie cleared",
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error during logout",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
        ],
    )
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
 * @property username Optional username for the account.
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

    @field:Size(max = 50, message = "Username must not exceed 50 characters")
    @field:Schema(
        description = "Optional username for the account",
        example = "johndoe",
        required = false,
        maxLength = 50,
    )
    val username: String? = null,
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
