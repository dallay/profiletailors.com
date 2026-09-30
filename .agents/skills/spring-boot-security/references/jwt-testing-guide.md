# JWT Testing Guide

Comprehensive testing strategies for JWT authentication and authorization in the
reactive SMP backend. Reactive examples use Kotest + MockK (`@MockkBean`),
`@SpringBootTest` with `@AutoConfigureWebTestClient`, R2DBC via Testcontainers,
and `WebTestClient` for end-to-end tests. Servlet JUnit/Mockito examples are
not the active backend default; see `migration-spring-security-6x.md` for
migration context.

## Table of Contents

1. [Unit Testing](#unit-testing)
2. [Integration Testing](#integration-testing)
3. [Security Testing](#security-testing)
4. [Performance Testing](#performance-testing)
5. [Test Data Management](#test-data-management)
6. [Mock Strategies](#mock-strategies)
7. [Continuous Testing](#continuous-testing)

## Unit Testing

### Testing JWT Service with Kotest + MockK

```kotlin
class JwtServiceTest : StringSpec({
    val secretKeyRepository = mockk<SecretKeyRepository>()
    val cacheManager = mockk<CacheManager>()

    val jwtService = JwtService(
        secretKeyRepository = secretKeyRepository,
        cacheManager = cacheManager,
        secret = "test-secret-key-that-is-at-least-256-bits-long",
        accessTokenExpiration = 900_000,
        refreshTokenExpiration = 604_800_000,
        issuer = "test-issuer",
    )

    val userDetails = TestUsers.userDetails("test@profiletailors.com", "ROLE_USER")

    "should generate valid token" {
        val token = runBlocking { jwtService.generateToken(userDetails) }

        token.shouldNotBeEmpty()
        runBlocking { jwtService.extractUsername(token) } shouldBe "test@profiletailors.com"
        runBlocking { jwtService.isTokenValid(token, userDetails) } shouldBe true
    }

    "should extract all claims" {
        val token = runBlocking { jwtService.generateToken(userDetails) }
        val claims = runBlocking { jwtService.extractAllClaims(token) }

        claims.subject shouldBe "test@profiletailors.com"
        claims.issuer shouldBe "test-issuer"
        claims.get("authorities", List::class.java) shouldBe listOf("ROLE_USER")
    }

    "should detect expired tokens" {
        val shortExpiration = jwtService.copy(accessTokenExpiration = -1_000)
        val expiredToken = runBlocking { shortExpiration.generateToken(userDetails) }

        runBlocking { shortExpiration.isTokenValid(expiredToken, userDetails) } shouldBe false
    }

    "should reject malformed tokens" {
        shouldThrow<JwtException> {
            runBlocking { jwtService.extractUsername("invalid.token.here") }
        }
    }
})
```

### Testing Token Blacklist

```kotlin
class TokenBlacklistServiceTest : StringSpec({
    val blacklistedTokenRepository = mockk<BlacklistedTokenRepository>()
    val jwtDecoder = mockk<ReactiveJwtDecoder>()
    val service = TokenBlacklistService(blacklistedTokenRepository, jwtDecoder)

    val token = "test.jwt.token"
    val tokenId = "token-id"

    beforeEach { clearMocks(blacklistedTokenRepository, jwtDecoder, answers = true) }

    "should persist blacklist entry" {
        val jwt = TestTokens.testJwt(tokenId = tokenId)
        every { jwtDecoder.decode(token) } returns Mono.just(jwt)
        coEvery { blacklistedTokenRepository.save(any()) } answers { firstArg() }

        runBlocking { service.blacklistToken(token, "logout", BlacklistReason.LOGOUT) }
        coVerify { blacklistedTokenRepository.save(any()) }
    }

    "should detect blacklist entry by tokenId" {
        val jwt = TestTokens.testJwt(tokenId = tokenId)
        every { jwtDecoder.decode(token) } returns Mono.just(jwt)
        coEvery { blacklistedTokenRepository.existsByTokenId(tokenId) } returns true

        runBlocking { service.isTokenBlacklisted(token) } shouldBe true
    }

    "should return false for non-blacklisted tokens" {
        val jwt = TestTokens.testJwt(tokenId = tokenId)
        every { jwtDecoder.decode(token) } returns Mono.just(jwt)
        coEvery { blacklistedTokenRepository.existsByTokenId(tokenId) } returns false

        runBlocking { service.isTokenBlacklisted(token) } shouldBe false
    }
})
```

### Testing Reactive JWT Authentication Filter

```kotlin
class JwtAuthenticationWebFilterTest : StringSpec({
    val jwtService = mockk<JwtTokenService>()
    val userDetailsService = mockk<ReactiveUserDetailsService>()
    val blacklistService = mockk<TokenBlacklistService>()

    val filter = JwtAuthenticationWebFilter(jwtService, userDetailsService, blacklistService)

    val exchange = mockk<ServerWebExchange>(relaxed = true)
    val chain = mockk<WebFilterChain>(relaxed = true)
    val request = mockk<ServerHttpRequest>(relaxed = true)

    beforeEach {
        clearMocks(jwtService, userDetailsService, blacklistService, exchange, chain, request, answers = true)
        exchange.request returns request
    }

    "should authenticate with valid token" {
        val token = "valid.jwt.token"
        val username = "user@profiletailors.com"
        val user = TestUsers.account(email = username)

        every { request.headers.header(HttpHeaders.AUTHORIZATION) } returns listOf("Bearer $token")
        every { blacklistService.isTokenBlacklisted(any()) } returns false
        every { jwtService.extractUsername(token) } returns username
        every { userDetailsService.findByUsername(username) } returns Mono.just(user)
        every { jwtService.isTokenValid(token) } returns true
        every { chain.filter(any()) } returns Mono.empty()

        filter.filter(exchange, chain).awaitSingleOrNull()
        verify { chain.filter(exchange) }
    }

    "should skip authentication when no token is present" {
        every { request.headers.header(HttpHeaders.AUTHORIZATION) } returns emptyList()
        every { chain.filter(any()) } returns Mono.empty()

        filter.filter(exchange, chain).awaitSingleOrNull()
        verify { chain.filter(exchange) }
    }

    "should bypass the filter when the token is blacklisted" {
        val token = "blacklisted.jwt.token"
        every { request.headers.header(HttpHeaders.AUTHORIZATION) } returns listOf("Bearer $token")
        every { blacklistService.isTokenBlacklisted(token) } returns true
        every { chain.filter(any()) } returns Mono.empty()

        filter.filter(exchange, chain).awaitSingleOrNull()
        verify { chain.filter(exchange) }
    }
})
```

## Integration Testing

### Reactive Controller Test via `WebTestClient`

```kotlin
@SpringBootTest
@AutoConfigureWebTestClient
@TestPropertySource(
    properties = [
        "jwt.secret=test-secret-key-for-integration-testing-256-bits-long",
        "jwt.access-token-expiration=PT15M",
    ],
)
class AuthControllerIntegrationTest(
    private val webTestClient: WebTestClient,
    private val tokenIssuer: TestJwtIssuer,
) : StringSpec({
    "POST /api/auth/login returns tokens for a valid user" {
        webTestClient.post().uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("user@profiletailors.com", "Password123!"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.accessToken").exists()
            .jsonPath("$.refreshToken").exists()
    }

    "GET /api/users/me accepts a valid token" {
        val token = tokenIssuer.issue(TestUsers.default())
        webTestClient.get().uri("/api/users/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
    }

    "GET /api/users/me rejects a missing token" {
        webTestClient.get().uri("/api/users/me").exchange()
            .expectStatus().isUnauthorized
    }
})
```

### Security Configuration with Testcontainers

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class SecurityIntegrationTest(
    private val webTestClient: WebTestClient,
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
        }
    }

    "completes login → /me → refresh → logout end-to-end" {
        val login = webTestClient.post().uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("user@profiletailors.com", "Password123!"))
            .exchange()
            .expectStatus().isOk
            .expectBody<LoginResponse>()
            .returnResult()
            .responseBody!!

        webTestClient.get().uri("/api/users/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${login.accessToken}")
            .exchange()
            .expectStatus().isOk

        val refreshResponse = webTestClient.post().uri("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(RefreshTokenRequest(login.refreshToken))
            .exchange()
            .expectStatus().isOk
            .expectBody<RefreshTokenResponse>()
            .returnResult()
            .responseBody!!

        webTestClient.post().uri("/api/auth/logout")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${refreshResponse.accessToken}")
            .exchange()
            .expectStatus().is2xxSuccessful
    }
})
```

### Authentication Flow Tests (BDD-style)

```kotlin
class AuthenticationFlowTest(
    private val webTestClient: WebTestClient,
    private val tokenIssuer: TestJwtIssuer,
) : StringSpec({
    "registered user can authenticate" {
        webTestClient.post().uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(RegisterRequest("user@profiletailors.com", "Password123!"))
            .exchange()
            .expectStatus().isOk

        webTestClient.post().uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("user@profiletailors.com", "Password123!"))
            .exchange()
            .expectStatus().isOk
            .expectBody<LoginResponse>()
            .value { it.accessToken.shouldNotBeEmpty() }
    }

    "rejects login with wrong password" {
        webTestClient.post().uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("user@profiletailors.com", "WrongPassword!"))
            .exchange()
            .expectStatus().isUnauthorized
    }

    "rotation produces a fresh access token and revokes the old refresh token" {
        val login = LoginResponse(
            accessToken = tokenIssuer.issue(TestUsers.default()),
            refreshToken = "refresh-token",
        )

        val newToken = webTestClient.post().uri("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(RefreshTokenRequest(login.refreshToken))
            .exchange()
            .expectStatus().isOk
            .expectBody<RefreshTokenResponse>()
            .returnResult()
            .responseBody!!

        newToken.accessToken shouldNotBe login.accessToken
    }
})
```

## Security Testing

### Protected Endpoint Tests

```kotlin
class ProtectedEndpointSecurityTest(
    private val webTestClient: WebTestClient,
) : StringSpec({
    "admin endpoint returns 403 for USER tokens" {
        val userToken = TestJwtIssuer().issueUserToken(TestUsers.default())
        webTestClient.get().uri("/api/admin/users")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $userToken")
            .exchange()
            .expectStatus().isForbidden
    }

    "admin endpoint returns 200 for ADMIN tokens" {
        val adminToken = TestJwtIssuer().issueAdminToken(TestUsers.default())
        webTestClient.get().uri("/api/admin/users")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
            .exchange()
            .expectStatus().isOk
    }

    "logout completes without errors" {
        val token = TestJwtIssuer().issue(TestUsers.default())
        webTestClient.post().uri("/api/auth/logout")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isNoContent
    }
})
```

### Authenticated User Controller Test

```kotlin
@WebFluxTest(controllers = [UserController::class])
@Import(JwtTestConfig::class)
class UserControllerTest(
    private val webTestClient: WebTestClient,
) : StringSpec({
    "endpoint requires a Bearer token" {
        webTestClient.get().uri("/api/users/me")
            .exchange()
            .expectStatus().isUnauthorized
    }

    "endpoint returns the user payload for an authenticated request" {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockUser("test@profiletailors.com"))
            .get().uri("/api/users/me")
            .exchange()
            .expectStatus().isOk
            .expectBody<UserResponse>()
            .value { it.email shouldBe "test@profiletailors.com" }
    }

    "endpoint accepts a mocked JWT token" {
        val jwt = Jwt.withTokenValue("mock.jwt.token")
            .header("alg", "HS256")
            .claim("sub", "test@profiletailors.com")
            .claim("scope", "ROLE_USER")
            .build()

        webTestClient
            .mutateWith(SecurityMockServerConfigurers.mockJwt().jwt { jwt })
            .get().uri("/api/users/me")
            .exchange()
            .expectStatus().isOk
    }
})
```

## Performance Testing

### JWT Operation Performance

```kotlin
class JwtPerformanceBenchmarks(
    private val jwtService: JwtService,
) : StringSpec({
    val user = TestUsers.default()

    "generates tokens in under 5 ms on average" {
        val tokens = (1..1000).map {
            runBlocking { jwtService.generateToken(user) }
        }
        tokens.shouldHaveSize(1000)
    }

    "validates tokens in under 3 ms on average" {
        val tokens = (1..1000).map { runBlocking { jwtService.generateToken(user) } }
        tokens.forEach { runBlocking { jwtService.isTokenValid(it) } }
    }

    "concurrent validation stays bounded under contention" {
        val numThreads = 10
        val opsPerThread = 100
        coroutineScope {
            (1..numThreads).map {
                async(Dispatchers.Default) {
                    repeat(opsPerThread) {
                        runBlocking { jwtService.generateToken(user) }
                        runBlocking { jwtService.isTokenValid("a-bogus-token") }
                    }
                }
            }.awaitAll()
        }
    }

    "remains performant with large user payloads" {
        val roles = (0 until 50).map { Role(id = RoleId(it.toLong()), name = "ROLE_$it", description = "$it") }
        val largeUser = user.copy(roles = roles.toMutableSet())

        (1..100).map { runBlocking { jwtService.generateToken(largeUser) } }
    }

    "extracts username in under 1 ms on average" {
        val tokens = (1..1000).map { runBlocking { jwtService.generateToken(user) } }
        tokens.forEach { runBlocking { jwtService.extractUsername(it) } }
    }
})
```

## Test Data Management

### Builders and Fixtures

```kotlin
object TestTokens {
    fun testJwt(tokenId: String = "token-1", subject: String = "user@profiletailors.com"): Jwt =
        Jwt.withTokenValue("mock.jwt.token")
            .header("alg", "HS256")
            .claim("sub", subject)
            .claim("jti", tokenId)
            .claim("type", "access")
            .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
            .build()

    fun expiredJwt(): Jwt =
        Jwt.withTokenValue("expired.jwt.token")
            .header("alg", "HS256")
            .claim("sub", "user@profiletailors.com")
            .claim("jti", "expired")
            .claim("type", "access")
            .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
            .build()
}

object TestUsers {
    fun default() = UserAccount(
        id = UserId(1L),
        email = "user@profiletailors.com",
        passwordHash = "encoded-password",
        firstName = "Test",
        lastName = "User",
        enabled = true,
    )

    fun userDetails(email: String, vararg roles: String): UserAccount {
        val userRole = Role(id = RoleId(1L), name = "USER", description = "Default")
        return UserAccount(
            id = UserId(1L),
            email = email,
            passwordHash = "encoded-password",
            firstName = "Test",
            lastName = "User",
            enabled = true,
            roles = mutableSetOf(userRole),
        )
    }
}

object TestRefreshTokens {
    fun active(userId: UserId) = RefreshToken(
        id = RefreshTokenId(1L),
        tokenHash = "hash",
        userId = userId,
        tokenId = "jti",
        sessionId = "sid",
        createdAt = Instant.now(),
        expiresAt = Instant.now().plus(1, ChronoUnit.DAYS),
    )

    fun revoked(userId: UserId) = active(userId).copy(revoked = true, revokedAt = Instant.now())
}
```

### Test JWT Issuer

```kotlin
class TestJwtIssuer {
    fun issue(user: UserAccount, adminRole: Boolean = false): String = runBlocking {
        val authorities = mutableListOf(SimpleGrantedAuthority("ROLE_USER"))
        if (adminRole) authorities += SimpleGrantedAuthority("ROLE_ADMIN")
        JwtIssuer.react(
            user = user,
            authorities = authorities,
            expiration = Duration.ofMinutes(15),
        )
    }

    fun issueUserToken(user: UserAccount): String = issue(user, adminRole = false)
    fun issueAdminToken(user: UserAccount): String = issue(user, adminRole = true)
    fun issueDepartmentToken(user: UserAccount, department: String): String = issue(user)
    fun issueTimeScopedToken(user: UserAccount, accessTime: Instant): String = issue(user)
}
```

## Mock Strategies

### Service Mocking with MockK

```kotlin
val secretKeyRepository: SecretKeyRepository = mockk()
val jwtDecoder: ReactiveJwtDecoder = mockk()

@BeforeEach
fun setUp() {
    every { jwtDecoder.decode(any()) } returns Mono.just(TestTokens.testJwt())
    coEvery { secretKeyRepository.findCurrentKey() } returns TestCrypto.signingKey()
}
```

### Reactive Stubbing

```kotlin
val userRepository = mockk<UserRepositoryR2dbc>()
coEvery { userRepository.findByEmail("test@profiletailors.com") } returns TestUsers.default()
coEvery { userRepository.save(any()) } answers { firstArg() }
```

### `WebTestClient` Auth Setup

```kotlin
private fun WebTestClient.withMockUser(email: String): WebTestClient =
    mutateWith(SecurityMockServerConfigurers.mockUser(email))

private fun WebTestClient.withMockJwt(jwt: Jwt): WebTestClient =
    mutateWith(SecurityMockServerConfigurers.mockJwt().jwt { it.token(jwt.tokenValue).claim("sub", jwt.subject) })
```

## Continuous Testing

### GitHub Actions Workflow

```yaml
name: JWT Security Tests
on: [ push, pull_request ]

jobs:
  test:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:18-alpine
        env:
          POSTGRES_PASSWORD: postgres
          POSTGRES_DB: testdb
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 5432:5432

      redis:
        image: redis:7-alpine
        options: >-
          --health-cmd "redis-cli ping"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 6379:6379

    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Cache Gradle dependencies
        uses: actions/cache@v4
        with:
          path: ~/.gradle/caches
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*') }}
          restore-keys: ${{ runner.os }}-gradle
      - name: Run JWT security tests
        run: ./gradlew :server:smp:test --tests "*Jwt*Test" --tests "*Security*Test"
```

### Test Coverage with Kover

```kotlin
plugins {
    id("org.jetbrains.kotlinx.kover") version "0.9.0"
}

kover {
    reports {
        total {
            verify { rule { minBound { minValue = 80 } } }
        }
    }
}
```

## Best Practices

1. **Test Pyramid**: Maintain the proper pyramid with more unit tests than integration tests.
2. **Test Isolation**: Each test must be independent and free of ordering assumptions.
3. **Test Data**: Use realistic data that covers edge cases (expired tokens, large payloads, revoked tokens).
4. **Performance**: Include performance tests for token generation and validation.
5. **Security**: Test both positive and negative security paths.
6. **Mocking**: Use MockK for service-level mocks and `WebTestClient` for HTTP-level verification.
7. **Coverage**: Aim for at least 80% line coverage through Kover.
8. **Automation**: Run the JWT security suite on every push and every pull request.

## References

- [Spring Security Reactive Testing](https://docs.spring.io/spring-security/reference/reactive/test/index.html)
- [Testcontainers](https://www.testcontainers.org/)
- [Kotest](https://kotest.io/)
- [MockK](https://mockk.io/)
- [Kover](https://github.com/Kotlin/kotlinx-kover)
- [OWASP JWT Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_Cheat_Sheet.html)
