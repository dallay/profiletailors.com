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
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.http.server.reactive.ServerHttpRequest

@RestController
@RequestMapping("/api/auth")
class LocalAuthController(
    private val mediator: Mediator,
    private val refreshSessionCookieFactory: RefreshSessionCookieFactory,
    private val refreshSessionProperties: RefreshSessionProperties,
) {

    @PostMapping("/register")
    suspend fun register(@RequestBody request: RegisterUserRequest): ResponseEntity<AuthTokens> =
        sessionResponse(
            mediator.send(
                RegisterUserCommand(
                    email = request.email,
                    password = request.password,
                    username = request.username,
                ),
            ),
        )

    @PostMapping("/login")
    suspend fun login(@RequestBody request: LoginUserRequest): ResponseEntity<AuthTokens> =
        sessionResponse(
            mediator.send(
                LoginUserCommand(
                    email = request.email,
                    password = request.password,
                ),
            ),
        )

    @PostMapping("/refresh")
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

    @PostMapping("/logout")
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

data class RegisterUserRequest(
    val email: String,
    val password: String,
    val username: String? = null,
)

data class LoginUserRequest(
    val email: String,
    val password: String,
)
