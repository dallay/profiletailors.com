package com.profiletailors.spring.boot

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.common.domain.bus.command.Command
import com.profiletailors.common.domain.bus.command.CommandWithResult
import com.profiletailors.common.domain.bus.query.Query
import com.profiletailors.common.domain.bus.query.Response
import com.profiletailors.config.ContextKeys.WORKSPACE_CONTEXT_KEY
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.MessageSource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.util.context.Context
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class ApiControllerTest {

    private val mediator = mockk<Mediator>()
    private val controller = TestApiController(mediator)

    @Test
    fun `should dispatch command`() = runTest {
        val command = mockk<Command>()
        coEvery { mediator.send(command) } returns Unit

        controller.testDispatch(command)

        coVerify(exactly = 1) { mediator.send(command) }
    }

    @Test
    fun `should dispatch command with result`() = runTest {
        val command = mockk<CommandWithResult<String>>()
        coEvery { mediator.send(command) } returns "success"

        val result = controller.testDispatchWithResult(command)

        result shouldBe "success"
        coVerify(exactly = 1) { mediator.send(command) }
    }

    @Test
    fun `should ask query`() = runTest {
        val query = mockk<Query<TestResponse>>()
        val response = TestResponse()
        coEvery { mediator.send(query) } returns response

        val result = controller.testAsk(query)

        result shouldBe response
        coVerify(exactly = 1) { mediator.send(query) }
    }

    @Test
    fun `should return authentication`() = runTest {
        val auth = mockk<Authentication>()
        val context = SecurityContextImpl(auth)

        val result = Mono.defer {
            Mono.just(controller)
        }.flatMap {
            mono { it.testGetAuthentication() }
        }.contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)))
        .block()

        result shouldBe auth
    }

    @Test
    fun `should return userId from JwtAuthenticationToken`() = runTest {
        val jwt = mockk<Jwt>()
        coEvery { jwt.subject } returns "user-123"
        val auth = JwtAuthenticationToken(jwt)
        val context = SecurityContextImpl(auth)

        val result = Mono.defer {
            Mono.just(controller)
        }.flatMap {
            mono { it.testGetUserId() }
        }.contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)))
        .block()

        result shouldBe "user-123"
    }

    @Test
    fun `should return null userId for non-Jwt tokens`() = runTest {
        val auth = UsernamePasswordAuthenticationToken("user", "pass")
        val context = SecurityContextImpl(auth)

        val result = Mono.defer {
            Mono.just(controller)
        }.flatMap {
            mono { it.testGetUserId() }
        }.contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)))
        .block()

        result shouldBe null
    }

    @Test
    fun `should return userEmail from JwtAuthenticationToken`() = runTest {
        val jwt = mockk<Jwt>()
        val attributes = mapOf("email" to "test@example.com")
        coEvery { jwt.claims } returns attributes
        val auth = JwtAuthenticationToken(jwt)
        val context = SecurityContextImpl(auth)

        val result = Mono.defer {
            Mono.just(controller)
        }.flatMap {
            mono { it.testGetUserEmail() }
        }.contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)))
        .block()

        result shouldBe "test@example.com"
    }

    @Test
    fun `should get userId as UUID from token`() = runTest {
        val userId = UUID.randomUUID()
        val jwt = mockk<Jwt>()
        coEvery { jwt.subject } returns userId.toString()
        val auth = JwtAuthenticationToken(jwt)
        val context = SecurityContextImpl(auth)

        val result = Mono.defer {
            Mono.just(controller)
        }.flatMap {
            mono { it.testGetUserIdFromToken() }
        }.contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)))
        .block()

        result shouldBe userId
    }

    @Test
    fun `should throw UNAUTHORIZED when userId is missing in token`() = runTest {
        val auth = UsernamePasswordAuthenticationToken("user", "pass")
        val context = SecurityContextImpl(auth)

        val mono = Mono.defer {
            Mono.just(controller)
        }.flatMap {
            mono { it.testGetUserIdFromToken() }
        }.contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)))

        val exception = assertThrows<ResponseStatusException> {
            mono.block()
        }
        exception.statusCode shouldBe HttpStatus.UNAUTHORIZED
    }

    @Test
    fun `should throw BAD_REQUEST when userId format is invalid`() = runTest {
        val jwt = mockk<Jwt>()
        coEvery { jwt.subject } returns "invalid-uuid"
        val auth = JwtAuthenticationToken(jwt)
        val context = SecurityContextImpl(auth)

        val mono = Mono.defer {
            Mono.just(controller)
        }.flatMap {
            mono { it.testGetUserIdFromToken() }
        }.contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)))

        val exception = assertThrows<ResponseStatusException> {
            mono.block()
        }
        exception.statusCode shouldBe HttpStatus.BAD_REQUEST
    }

    @Test
    fun `should get workspaceId from context`() = runTest {
        val workspaceId = UUID.randomUUID()

        val result = mono { controller.testGetWorkspaceIdFromContext() }
            .contextWrite(Context.of(WORKSPACE_CONTEXT_KEY, workspaceId))
            .block()

        result shouldBe workspaceId
    }

    @Test
    fun `should throw BAD_REQUEST when workspaceId is missing in context`() = runTest {
        val mono = mono { controller.testGetWorkspaceIdFromContext() }

        val exception = assertThrows<ResponseStatusException> {
            mono.block()
        }
        exception.statusCode shouldBe HttpStatus.BAD_REQUEST
    }

    @Test
    fun `should sanitize path variable`() {
        controller.testSanitizePathVariable("valid-id-123") shouldBe "valid-id-123"
        controller.testSanitizePathVariable("another_one") shouldBe "another_one"

        assertThrows<IllegalArgumentException> {
            controller.testSanitizePathVariable("invalid/path")
        }
        assertThrows<IllegalArgumentException> {
            controller.testSanitizePathVariable("invalid space")
        }
    }

    @Test
    fun `should get localized message`() {
        val request = mockk<ServerHttpRequest>()
        val headers = HttpHeaders()
        headers.acceptLanguage = Locale.LanguageRange.parse("es")
        coEvery { request.headers } returns headers

        val messageSource = mockk<MessageSource>()
        coEvery { messageSource.getMessage("key", null, Locale("es")) } returns "hola"

        val message = controller.testGetLocalizedMessage("key", request, messageSource)

        message shouldBe "hola"
    }

    // Helper classes
    private class TestApiController(mediator: Mediator) : ApiController(mediator) {
        suspend fun testDispatch(command: Command) = dispatch(command)
        suspend fun <TResult> testDispatchWithResult(command: CommandWithResult<TResult>) = dispatch(command)
        suspend fun <TResponse : Response> testAsk(query: Query<TResponse>) = ask(query)
        suspend fun testGetAuthentication() = authentication()
        suspend fun testGetUserId() = userId()
        suspend fun testGetUserEmail() = userEmail()
        suspend fun testGetUserIdFromToken() = userIdFromToken()
        suspend fun testGetWorkspaceIdFromContext() = workspaceIdFromContext()
        fun testSanitizePathVariable(path: String) = sanitizePathVariable(path)
        fun testGetLocalizedMessage(key: String, request: ServerHttpRequest, source: MessageSource) =
            getLocalizedMessage(key, request, source)
    }

    private class TestResponse : Response

    // Bridge mono for suspend functions in tests
    private fun <T> mono(block: suspend () -> T): Mono<T> = Mono.defer {
        val result = kotlinx.coroutines.runBlocking { block() }
        if (result == null) Mono.empty() else Mono.just(result)
    }
}
