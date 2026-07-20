package com.profiletailors.smp.identity.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.PublishStrategy
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.notification.Notification
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.common.testfixture.CredentialGenerator
import com.profiletailors.smp.credentials.application.RefreshSessionProperties
import com.profiletailors.smp.credentials.application.RefreshSessionToken
import com.profiletailors.smp.credentials.infrastructure.RefreshSessionCookieFactory
import com.profiletailors.smp.identity.application.AuthTokens
import com.profiletailors.smp.identity.application.LocalAuthSessionResult
import com.profiletailors.smp.identity.application.LoginUserCommand
import com.profiletailors.smp.identity.application.LogoutUserSessionCommand
import com.profiletailors.smp.identity.application.LogoutUserSessionResult
import com.profiletailors.smp.identity.application.RefreshUserSessionCommand
import com.profiletailors.smp.identity.application.RegisterUserCommand
import com.profiletailors.smp.identity.application.ResendVerificationCommand
import com.profiletailors.smp.identity.application.ResendVerificationResult
import com.profiletailors.smp.identity.application.VerifyEmailCommand
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.server.reactive.ServerHttpRequest

class LocalAuthControllerTest {

    private val validPassword = CredentialGenerator.generateValidPassword()
    private val cookieFactory = RefreshSessionCookieFactory(
        RefreshSessionProperties(
            cookieName = "pt_refresh",
            cookiePath = "/api/auth",
            sameSite = "Lax",
            secure = false,
            ttlSeconds = 604800,
        ),
    )
    private val cookieProperties = RefreshSessionProperties(
        cookieName = "pt_refresh",
        cookiePath = "/api/auth",
        sameSite = "Lax",
        secure = false,
        ttlSeconds = 604800,
    )

    @Test
    fun `dispatches register command and returns 201 with session tokens`() = runTest {
        val registrationResult = sessionResult("token-1", "user-1", "yuniel@example.com", "yuniel", "PENDING")
        val mediator = CapturingMediator(sessionResult = registrationResult)
        val controller = controller(mediator)

        val response = controller.register(
            RegisterUserRequest(
                email = "yuniel@example.com",
                password = validPassword,
                confirmedAgeEligibility = true,
                acceptedTermsVersion = "terms-v1.0.0",
            ),
        )

        assertEquals(201, response.statusCode.value())
        assertEquals("token-1", response.body?.accessToken)
        assertEquals("user-1", response.body?.principalId)
        assertEquals("yuniel@example.com", response.body?.email)
        assertEquals("PENDING", response.body?.emailStatus)
        // Verify refresh cookie is set
        assertTrue(
            response.headers["Set-Cookie"]?.first()?.contains("pt_refresh=refresh-lookup.refresh-secret") == true,
        )
        assertEquals(
            RegisterUserCommand(
                email = "yuniel@example.com",
                password = validPassword,
                confirmedAgeEligibility = true,
                acceptedTermsVersion = "terms-v1.0.0",
            ),
            mediator.lastRequest,
        )
    }

    @Test
    fun `dispatches login command for pending user and sets refresh cookie`() = runTest {
        val expected = sessionResult("token-2", "user-2", "login@example.com", "login", "PENDING")
        val mediator = CapturingMediator(sessionResult = expected)
        val controller = controller(mediator)

        val response = controller.login(
            LoginUserRequest(
                email = "login@example.com",
                password = validPassword,
            ),
        )

        assertEquals(200, response.statusCode.value())
        assertEquals("token-2", response.body?.accessToken)
        assertEquals("PENDING", response.body?.emailStatus)
        assertTrue(
            response.headers["Set-Cookie"]?.first()?.contains("pt_refresh=refresh-lookup.refresh-secret") == true,
        )
        assertEquals(
            LoginUserCommand(
                email = "login@example.com",
                password = validPassword,
            ),
            mediator.lastRequest,
        )
    }

    @Test
    fun `dispatches refresh command using refresh cookie`() = runTest {
        val expected = sessionResult("token-3", "user-3", "refresh@example.com", "refresh", "VERIFIED")
        val mediator = CapturingMediator(sessionResult = expected)
        val controller = controller(mediator)

        val response = controller.refresh(requestWithCookie("pt_refresh", "refresh-lookup.refresh-secret"))

        assertEquals("token-3", response.body?.accessToken)
        assertEquals(RefreshUserSessionCommand("refresh-lookup.refresh-secret"), mediator.lastRequest)
    }

    @Test
    fun `dispatches logout command and clears refresh cookie`() = runTest {
        val mediator =
            CapturingMediator(
                sessionResult = sessionResult("token-4", "user-4", "logout@example.com", "logout", "VERIFIED"),
            )
        mediator.logoutResult = LogoutUserSessionResult()
        val controller = controller(mediator)

        val response = controller.logout(requestWithCookie("pt_refresh", "refresh-lookup.refresh-secret"))

        assertEquals(204, response.statusCode.value())
        assertTrue(response.headers["Set-Cookie"]?.first()?.contains("Max-Age=0") == true)
        assertEquals(LogoutUserSessionCommand("refresh-lookup.refresh-secret"), mediator.lastRequest)
    }

    @Test
    fun `dispatches verify email command from request body token and returns verified session`() = runTest {
        val expected = sessionResult("token-5", "user-5", "verify@example.com", "verify", "VERIFIED")
        val mediator = CapturingMediator(sessionResult = expected)
        val controller = controller(mediator)

        val response = controller.verifyEmail(VerifyEmailRequest(token = "test-token"))

        assertEquals(200, response.statusCode.value())
        assertEquals("token-5", response.body?.accessToken)
        assertEquals("VERIFIED", response.body?.emailStatus)
        assertTrue(
            response.headers["Set-Cookie"]?.first()?.contains("pt_refresh=refresh-lookup.refresh-secret") == true,
        )
        assertEquals(VerifyEmailCommand(token = "test-token"), mediator.lastRequest)
    }

    @Test
    fun `dispatches resend verification command and returns 202`() = runTest {
        val mediator = CapturingMediator()
        mediator.resendResult = ResendVerificationResult()
        val controller = controller(mediator)

        val response = controller.resendVerification(
            ResendVerificationRequest(
                email = "yuniel@example.com",
            ),
        )

        assertEquals(202, response.statusCode.value())
        assertEquals(
            ResendVerificationCommand(email = "yuniel@example.com"),
            mediator.lastRequest,
        )
    }

    private fun controller(mediator: Mediator) = LocalAuthController(
        mediator = mediator,
        refreshSessionCookieFactory = cookieFactory,
        refreshSessionProperties = cookieProperties,
    )

    private fun sessionResult(
        accessToken: String,
        principalId: String,
        email: String,
        username: String,
        emailStatus: String,
    ) = LocalAuthSessionResult(
        tokens = AuthTokens(
            accessToken = accessToken,
            expiresIn = 900,
            principalId = principalId,
            email = email,
            username = username,
            emailStatus = emailStatus,
        ),
        refreshToken = RefreshSessionToken("refresh-lookup", "refresh-secret"),
    )

    private fun requestWithCookie(name: String, value: String): ServerHttpRequest =
        org.springframework.mock.http.server.reactive.MockServerHttpRequest.post("/api/auth/refresh")
            .cookie(org.springframework.http.HttpCookie(name, value))
            .build()

    private class CapturingMediator(private val sessionResult: LocalAuthSessionResult? = null) : Mediator {
        var lastRequest: Any? = null
        var logoutResult: LogoutUserSessionResult = LogoutUserSessionResult()
        var resendResult: ResendVerificationResult = ResendVerificationResult()

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TQuery : Query<TResponse>, TResponse> send(query: TQuery): TResponse {
            error(NOT_USED_MESSAGE)
        }

        override suspend fun <TCommand : Command> send(command: TCommand) {
            error(NOT_USED_MESSAGE)
        }

        @Suppress("UNCHECKED_CAST")
        override suspend fun <TCommand : CommandWithResult<TResult>, TResult> send(command: TCommand): TResult {
            lastRequest = command
            return when (command) {
                is LogoutUserSessionCommand -> logoutResult as TResult
                is ResendVerificationCommand -> resendResult as TResult
                else -> sessionResult as TResult
            }
        }

        override suspend fun <T : Notification> publish(notification: T) {
            error(NOT_USED_MESSAGE)
        }

        override suspend fun <T : Notification> publish(notification: T, publishStrategy: PublishStrategy) {
            error(NOT_USED_MESSAGE)
        }

        companion object {
            private const val NOT_USED_MESSAGE = "Not used in this test"
        }
    }
}
