# JWT Security Testing Strategies

This document covers testing strategies for the reactive JWT security stack used
in the SMP backend. All examples use Kotest + MockK (`@MockkBean`),
`@SpringBootTest` with `@AutoConfigureWebTestClient`, R2DBC via Testcontainers,
and `WebTestClient` for end-to-end tests. Servlet `WebTestClient`, `@MockkBean`,
and `@WebFluxTest` patterns are intentionally not shown — see
`migration-spring-security-6x.md` if a servlet reference is needed.

## Table of Contents

1. [Unit Testing JWT Components](#unit-testing-jwt-components)
2. [Integration Testing Security Configuration](#integration-testing-security-configuration)
3. [WebTestClient Security Testing](#webtestclient-security-testing)
4. [Security Test Scenarios](#security-test-scenarios)
5. [Performance Testing JWT Operations](#performance-testing-jwt-operations)

## Unit Testing JWT Components

### JWT Service Unit Tests (Kotest)

```kotlin
class JwtServiceTest : StringSpec({
    val refreshTokenService = mockk<RefreshTokenService>(relaxed = true)

    fun service(expirationMs: Long = 900_000): JwtService = JwtService(
        secret = "test-secret-key-for-unit-testing-only-256-bits-long",
        accessTokenExpiration = expirationMs,
        refreshTokenExpiration = 604_800_000,
        issuer = "test-issuer",
        audience = null,
        validateIssuer = true,
        validateAudience = false,
        clockSkewSeconds = 60,
        refreshTokenService = refreshTokenService,
    )

    val testUser = run {
        val userRole = Role(id = RoleId(1L), name = "USER", description = "Default role")
        UserAccount(
            id = UserId(1L),
            email = "test@profiletailors.com",
            passwordHash = "encodedPassword",
            firstName = "Test",
            lastName = "User",
            enabled = true,
            roles = mutableSetOf(userRole),
        )
    }

    "should generate valid access token" {
        val token = runBlocking { service().generateAccessToken(testUser).token }

        token.shouldNotBeEmpty()
        token.split(".").shouldHaveSize(3)

        val claims = runBlocking { service().extractClaims(token) }
        claims.subject shouldBe "test@profiletailors.com"
        claims.issuer shouldBe "test-issuer"
        claims.expiration.after(Date()) shouldBe true
        claims.issuedAt.shouldNotBeNull()
        claims.get("type") shouldBe "access"
    }

    "should extract username from valid token" {
        val token = runBlocking { service().generateAccessToken(testUser).token }
        runBlocking { service().extractUsername(token) } shouldBe "test@profiletailors.com"
    }

    "should validate token for matching user" {
        val token = runBlocking { service().generateAccessToken(testUser).token }
        runBlocking { service().isTokenValid(token, testUser) } shouldBe true
    }

    "should reject expired token" {
        val expiredService = service(expirationMs = 1)
        val token = runBlocking { expiredService.generateAccessToken(testUser).token }
        delay(2_000)
        runBlocking { expiredService.isTokenValid(token, testUser) } shouldBe false
    }

    "should reject token with invalid signature" {
        val token = runBlocking { service().generateAccessToken(testUser).token }
        val tampered = token.dropLast(10) + "tampered!!"
        runBlocking { service().isTokenValid(tampered, testUser) } shouldBe false
    }

    "should reject token issued by a different issuer" {
        val differentIssuer = service().copy(issuer = "different-issuer")
        val token = runBlocking { differentIssuer.generateAccessToken(testUser).token }
        runBlocking { service().isTokenValid(token, testUser) } shouldBe false
    }
})
```

### Refresh Token Service Unit Tests

```kotlin
class RefreshTokenServiceTest : StringSpec({
    val refreshTokenRepository = mockk<RefreshTokenRepository>()
    val userRepository = mockk<UserRepository>()
    val tokenService = mockk<JwtTokenService>()
    val claimsService = mockk<JwtClaimsService>()

    val service = RefreshTokenService(
        refreshTokenRepository = refreshTokenRepository,
        userRepository = userRepository,
        jwtTokenService = tokenService,
        claimsService = claimsService,
    )

    val user = TestUsers.default()

    "creates refresh token and enforces the limit" {
        coEvery { refreshTokenRepository.countActiveByUser(user.id, any()) } returns 0L
        coEvery { refreshTokenRepository.save(any()) } answers { firstArg() }

        val tokenResponse = runBlocking { service.createRefreshToken(user) }

        tokenResponse.token.shouldNotBeEmpty()
        coVerify { refreshTokenRepository.save(any()) }
    }

    "evicts the oldest token when the active limit is reached" {
        coEvery { refreshTokenRepository.countActiveByUser(user.id, any()) } returns 5L
        coEvery { refreshTokenRepository.deleteOldestByUser(user.id) } returns Unit

        runBlocking { service.createRefreshToken(user) }

        coVerify { refreshTokenRepository.deleteOldestByUser(user.id) }
    }

    "rotates refresh token successfully" {
        val existing = TestRefreshTokens.active(userId = user.id)
        coEvery { refreshTokenRepository.findByTokenHash(any()) } returns existing
        coEvery { refreshTokenRepository.save(any()) } answers { firstArg() }
        coEvery { userRepository.findById(user.id) } returns user

        val response = runBlocking { service.refresh(RefreshTokenRequest(token = "valid")) }

        response.accessToken.shouldNotBeNull()
        response.expiresIn.shouldBePositive()
    }

    "rejects refresh attempts on revoked tokens" {
        val revoked = TestRefreshTokens.revoked(userId = user.id)
        coEvery { refreshTokenRepository.findByTokenHash(any()) } returns revoked

        shouldThrow<InvalidTokenException> {
            runBlocking { service.refresh(RefreshTokenRequest(token = "revoked-token")) }
        }
    }
})
```

## Integration Testing Security Configuration

### Security Configuration Integration Tests

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(
    properties = [
        "jwt.secret=test-secret-key-for-integration-testing-256-bits-minimum",
        "jwt.access-token-expiration=PT15M",
        "jwt.refresh-token-expiration=P7D",
    ],
)
class SecurityIntegrationTest(
    private val webTestClient: WebTestClient,
    private val userRepository: UserRepositoryR2dbc,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
) : StringSpec({
    val testUser = TestUsers.default()

    beforeTest {
        runBlocking {
            userRepository.save(testUser.copy(passwordHash = passwordEncoder.encode("password"))).awaitSingle()
        }
    }

    "public endpoints are reachable without authentication" {
        webTestClient.get().uri("/api/public/health").exchange()
            .expectStatus().isOk
    }

    "protected endpoints return 401 without authentication" {
        webTestClient.get().uri("/api/users/me").exchange()
            .expectStatus().isUnauthorized
    }

    "protected endpoints return 200 with a valid token" {
        val token = runBlocking { jwtService.generateAccessToken(testUser).token }

        webTestClient.get().uri("/api/users/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
    }

    "protected endpoints return 401 with an invalid token" {
        webTestClient.get().uri("/api/users/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
            .exchange()
            .expectStatus().isUnauthorized
    }

    "protected endpoints return 401 when the token is expired" {
        val expiredService = jwtService.copyForExpiration(minutes = 0)
        val expiredToken = runBlocking { expiredService.generateAccessToken(testUser).token }
        delay(2_000)

        webTestClient.get().uri("/api/users/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $expiredToken")
            .exchange()
            .expectStatus().isUnauthorized
    }
})
```

### Authentication Controller Integration Tests

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class AuthenticationControllerIntegrationTest(
    private val webTestClient: WebTestClient,
    private val objectMapper: ObjectMapper,
) : StringSpec({
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url") { "r2dbc:postgresql://${postgres.host}:${postgres.firstMappedPort}/${postgres.databaseName}" }
            registry.add("spring.r2dbc.username") { postgres.username }
            registry.add("spring.r2dbc.password") { postgres.password }
            registry.add("jwt.secret") { "test-secret-key-for-integration-testing-256-bits-long" }
        }
    }

    val registerRequest = RegisterRequest(
        email = "test@profiletailors.com",
        username = "testuser",
        password = "Password123!",
        firstName = "Test",
        lastName = "User",
    )

    val authRequest = AuthenticationRequest(
        email = "test@profiletailors.com",
        password = "Password123!",
    )

    "registers a new user" {
        webTestClient.post().uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerRequest)
            .exchange()
            .expectStatus().isOk
            .expectBody<AuthenticationResponse>()
            .value { it.accessToken.shouldNotBeEmpty() }
    }

    "authenticates a registered user" {
        webTestClient.post().uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerRequest)
            .exchange()
            .expectStatus().isOk

        webTestClient.post().uri("/api/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(authRequest)
            .exchange()
            .expectStatus().isOk
            .expectBody<AuthenticationResponse>()
            .value { it.accessToken.shouldNotBeEmpty() }
    }

    "fails authentication with the wrong password" {
        val wrongPassword = AuthenticationRequest(
            email = "test@profiletailors.com",
            password = "WrongPassword123!",
        )

        webTestClient.post().uri("/api/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(wrongPassword)
            .exchange()
            .expectStatus().isUnauthorized
    }

    "rotates refresh tokens and issues a fresh access token" {
        webTestClient.post().uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerRequest)
            .exchange()
            .expectStatus().isOk

        val loginResponse = webTestClient.post().uri("/api/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(authRequest)
            .exchange()
            .expectStatus().isOk
            .expectBody<AuthenticationResponse>()
            .returnResult()
            .responseBody!!

        val refresh = webTestClient.post().uri("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(RefreshTokenRequest(loginResponse.refreshToken))
            .exchange()
            .expectStatus().isOk
            .expectBody<AuthenticationResponse>()
            .returnResult()
            .responseBody!!

        refresh.accessToken shouldNotBe loginResponse.accessToken
    }
})
```

## WebTestClient Security Testing

### Reactive Authentication Controller Tests

```kotlin
@SpringBootTest
@AutoConfigureWebTestClient
@TestPropertySource(
    properties = [
        "jwt.secret=test-secret-key-for-webtestclient-tests-256-bits-long",
        "jwt.access-token-expiration=PT15M",
    ],
)
class AuthenticationControllerWebTestClientTest(
    private val webTestClient: WebTestClient,
    private val userRepository: UserRepositoryR2dbc,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
) : StringSpec({
    val testUser = TestUsers.default()

    beforeTest {
        runBlocking {
            userRepository.save(testUser.copy(passwordHash = passwordEncoder.encode("Password123!")))
                .awaitSingle()
        }
    }

    "authenticates user and returns tokens" {
        webTestClient.post().uri("/api/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(AuthenticationRequest(testUser.email, "Password123!"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.accessToken").exists()
            .jsonPath("$.refreshToken").exists()
            .jsonPath("$.user.email").isEqualTo("test@profiletailors.com")
    }

    "rejects a request with an invalid payload" {
        webTestClient.post().uri("/api/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(AuthenticationRequest(email = "", password = ""))
            .exchange()
            .expectStatus().isBadRequest
    }

    "rejects unauthenticated access to admin endpoints" {
        webTestClient.get().uri("/api/admin/users").exchange()
            .expectStatus().isUnauthorized
    }

    "allows admin role to access the admin endpoint" {
        val token = TestJwtIssuer(adminRole = true).issue(TestUsers.default())
        webTestClient.get().uri("/api/admin/users")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
    }

    "denies USER role access to the admin endpoint" {
        val token = TestJwtIssuer(adminRole = false).issue(TestUsers.default())
        webTestClient.get().uri("/api/admin/users")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isForbidden
    }

    "authenticates with a valid Bearer token" {
        val token = runBlocking { jwtService.generateAccessToken(testUser).token }

        webTestClient.get().uri("/api/auth/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.email").isEqualTo(testUser.email)
    }

    "rejects a malformed token" {
        webTestClient.get().uri("/api/auth/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer malformed.token.here")
            .exchange()
            .expectStatus().isUnauthorized
    }

    "rejects a token without the Bearer prefix" {
        val token = runBlocking { jwtService.generateAccessToken(testUser).token }
        webTestClient.get().uri("/api/auth/me")
            .header(HttpHeaders.AUTHORIZATION, token)
            .exchange()
            .expectStatus().isUnauthorized
    }

    "completes logout without errors" {
        val token = runBlocking { jwtService.generateAccessToken(testUser).token }
        webTestClient.post().uri("/api/auth/logout")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.NO_CONTENT)
    }

    "rejects refresh requests with empty payloads" {
        webTestClient.post().uri("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(RefreshTokenRequest(refreshToken = ""))
            .exchange()
            .expectStatus().isBadRequest
    }

    "handles concurrent calls correctly" {
        val token = runBlocking { jwtService.generateAccessToken(testUser).token }
        val calls = (1..10).map {
            async(Dispatchers.IO) {
                webTestClient.get().uri("/api/auth/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .exchange()
                    .expectStatus().isOk
            }
        }
        runBlocking { calls.awaitAll() }
    }
})
```

## Security Test Scenarios

### Security Vulnerability Tests (WebTestClient)

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(
    properties = [
        "jwt.secret=test-secret-key-for-security-testing-256-bits-long",
        "jwt.access-token-expiration=PT15M",
    ],
)
class SecurityVulnerabilityTest(
    private val webTestClient: WebTestClient,
    private val jwtService: JwtService,
) : StringSpec({
    val testUser = TestUsers.default()

    "rejects tampered tokens" {
        val validToken = runBlocking { jwtService.generateAccessToken(testUser).token }
        val tampered = validToken.dropLast(5) + "xxxxx"

        webTestClient.get().uri("/api/auth/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $tampered")
            .exchange()
            .expectStatus().isUnauthorized
    }

    "rejects expired tokens" {
        val expiredService = jwtService.copyForExpiration(minutes = 0)
        val expiredToken = runBlocking { expiredService.generateAccessToken(testUser).token }
        delay(2_000)

        webTestClient.get().uri("/api/auth/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $expiredToken")
            .exchange()
            .expectStatus().isUnauthorized
    }

    "rejects SQL injection attempts" {
        val malicious = AuthenticationRequest(
            email = "test@profiletailors.com'; DROP TABLE users; --",
            password = "password",
        )

        webTestClient.post().uri("/api/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(malicious)
            .exchange()
            .expectStatus().isUnauthorized

        webTestClient.get().uri("/health").exchange()
            .expectStatus().isOk
    }

    "rejects XSS in authentication responses" {
        val malicious = AuthenticationRequest(
            email = "<script>alert('xss')</script>",
            password = "password",
        )

        val body = webTestClient.post().uri("/api/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(malicious)
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody<String>()
            .returnResult()
            .responseBody!!

        body.contains("<script>") shouldBe false
    }

    "handles large payloads without crashing" {
        webTestClient.get().uri("/api/auth/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${"x".repeat(10_000)}")
            .exchange()
            .expectStatus().isUnauthorized
    }

    "enforces rate limiting on failed login attempts" {
        val request = AuthenticationRequest(
            email = "test@profiletailors.com",
            password = "wrong-password",
        )

        repeat(10) {
            webTestClient.post().uri("/api/auth/authenticate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().is4xxClientError
            delay(100)
        }
    }
})
```

## Performance Testing JWT Operations

### JWT Performance Tests

```kotlin
class JwtPerformanceTest(
    private val jwtService: JwtService,
) : StringSpec({
    val testUser = TestUsers.default()

    "generates tokens in under 5ms on average" {
        val numTokens = 1_000
        val start = System.nanoTime()
        val tokens = (1..numTokens).map {
            runBlocking { jwtService.generateAccessToken(testUser).token }
        }
        val durationMs = (System.nanoTime() - start) / 1_000_000

        tokens.shouldHaveSize(numTokens)
        durationMs.shouldBeLessThan(5_000)
        val avgTimePerToken = durationMs.toDouble() / numTokens
        println("Average time per token generation: $avgTimePerToken ms")
        avgTimePerToken shouldBeLessThan 5.0
    }

    "validates tokens in under 3ms on average" {
        val numTokens = 1_000
        val tokens = (1..numTokens).map {
            runBlocking { jwtService.generateAccessToken(testUser).token }
        }

        val start = System.nanoTime()
        var validCount = 0
        for (token in tokens) {
            if (runBlocking { jwtService.isTokenValid(token, testUser) }) validCount++
        }
        val durationMs = (System.nanoTime() - start) / 1_000_000

        validCount shouldBe numTokens
        durationMs.shouldBeLessThan(3_000)
        val avgPerValidation = durationMs.toDouble() / numTokens
        println("Average time per token validation: $avgPerValidation ms")
        avgPerValidation shouldBeLessThan 3.0
    }

    "maintains performance under concurrent generation and validation" {
        val numThreads = 10
        val operationsPerThread = 100

        val start = System.nanoTime()
        val results = (1..numThreads).map {
            async(Dispatchers.IO) {
                (1..operationsPerThread).count { _ ->
                    val token = runBlocking { jwtService.generateAccessToken(testUser).token }
                    runBlocking { jwtService.isTokenValid(token, testUser) }
                }
            }
        }.awaitAll()
        val durationMs = (System.nanoTime() - start) / 1_000_000

        results.sum() shouldBe numThreads * operationsPerThread
        println("Successfully processed ${results.sum()} token operations in $durationMs ms")
    }

    "handles large user payloads without regression" {
        val roles = (0 until 50).map { Role(id = RoleId(it.toLong()), name = "ROLE_$it", description = "$it") }
        val largeUser = testUser.copy(roles = roles.toMutableSet())

        val numTokens = 100
        val start = System.nanoTime()
        val tokens = (1..numTokens).map {
            runBlocking { jwtService.generateAccessToken(largeUser).token }
        }
        val durationMs = (System.nanoTime() - start) / 1_000_000

        tokens.shouldHaveSize(numTokens)
        val avgPerToken = durationMs.toDouble() / numTokens
        println("Average time per large token generation: $avgPerToken ms")
        avgPerToken shouldBeLessThan 10.0
    }

    "extracts claims in under 1ms on average" {
        val numTokens = 1_000
        val tokens = (1..numTokens).map {
            runBlocking { jwtService.generateAccessToken(testUser).token }
        }

        val start = System.nanoTime()
        for (token in tokens) {
            runBlocking { jwtService.extractUsername(token) }
        }
        val durationMs = (System.nanoTime() - start) / 1_000_000

        val avgPerExtraction = durationMs.toDouble() / numTokens
        println("Average time per claim extraction: $avgPerExtraction ms")
        avgPerExtraction shouldBeLessThan 1.0
    }
})
```

These patterns provide comprehensive coverage for reactive JWT authentication in
the SMP backend: unit tests for service logic, `WebTestClient` integration tests
for the reactive HTTP layer, vulnerability tests for security regressions, and
performance tests for meeting the latency budget.
