# Testing JWT Security

This document covers testing patterns for the reactive JWT security stack used in
the SMP backend. All examples use Kotest + MockK (`@MockkBean`),
`@SpringBootTest` with `@AutoConfigureWebTestClient`, R2DBC via Testcontainers,
and `WebTestClient` for end-to-end tests. The Servlet JUnit Jupiter + Mockito +
`WebTestClient` + `@MockkBean` patterns are intentionally not shown — see
`migration-spring-security-6x.md` if a servlet reference is needed.

## Unit Testing Authentication

### Testing JWT Token Service with Kotest + MockK

```kotlin
class JwtTokenServiceTest : StringSpec({
    val jwtEncoder = mockk<JwtEncoder>()
    val jwtDecoder = mockk<ReactiveJwtDecoder>()
    val claimsService = mockk<JwtClaimsService>()
    val refreshTokenService = mockk<RefreshTokenService>()
    val tokenService = JwtTokenService(jwtEncoder, jwtDecoder, claimsService, mockk())

    "should generate access token successfully" {
        val user = TestUsers.default()
        val claims = TestTokens.testClaims()
        val encoded = TestTokens.encodedToken()

        every { claimsService.createAccessTokenClaims(user) } returns claims
        every { jwtEncoder.encode(any<JwtEncoderParameters>()) } returns encoded

        runBlocking {
            val response = tokenService.generateAccessToken(user)
            response.token shouldBe encoded.tokenValue
            response.expiresAt shouldBe claims.expiresAt.toEpochMilli()
            response.type shouldBe "access"
        }
        verify { claimsService.createAccessTokenClaims(user) }
        verify { jwtEncoder.encode(any<JwtEncoderParameters>()) }
    }

    "should validate token successfully" {
        val validToken = "valid.jwt.token"
        val jwt = TestTokens.testJwt()

        every { jwtDecoder.decode(validToken) } returns Mono.just(jwt)

        runBlocking { tokenService.isTokenValid(validToken) shouldBe true }
        verify { jwtDecoder.decode(validToken) }
    }

    "should reject expired token" {
        val expiredToken = "expired.jwt.token"
        val jwt = TestTokens.expiredJwt()

        every { jwtDecoder.decode(expiredToken) } returns Mono.just(jwt)

        runBlocking { tokenService.isTokenValid(expiredToken) shouldBe false }
    }

    "should reject invalid token" {
        val invalidToken = "invalid.token"
        every { jwtDecoder.decode(invalidToken) } throws JwtException("Invalid token")

        runBlocking { tokenService.isTokenValid(invalidToken) shouldBe false }
    }

    "should extract claim from token" {
        val token = "test.jwt.token"
        val claimName = "sub"
        val expectedClaim = "12345"
        val jwt = TestTokens.testJwt()

        every { jwtDecoder.decode(token) } returns Mono.just(jwt)

        runBlocking {
            tokenService.extractTokenClaim(token, claimName) shouldBe expectedClaim
        }
    }
})
```

### Testing Security Configuration

```kotlin
@SpringBootTest
@AutoConfigureWebTestClient
@TestPropertySource(
    properties = [
        "jwt.secret=test-secret-key-for-testing-only-must-be-long-enough-for-hs256",
        "jwt.access-token-expiration=PT5M",
    ],
)
class SecurityConfigTest(
    private val webTestClient: WebTestClient,
    private val userRepository: UserRepositoryR2dbc,
    private val passwordEncoder: PasswordEncoder,
    private val tokenIssuer: TestJwtIssuer,
) : StringSpec({
    beforeSpec {
        runBlocking {
            userRepository.save(TestUsers.default().copy(passwordHash = passwordEncoder.encode("password")))
                .awaitSingle()
        }
    }

    "public health endpoint is reachable without authentication" {
        webTestClient.get().uri("/api/public/health").exchange()
            .expectStatus().isOk
    }

    "protected endpoint returns 401 without authentication" {
        webTestClient.get().uri("/api/users/me").exchange()
            .expectStatus().isUnauthorized
    }

    "protected endpoint returns 200 with valid JWT" {
        val token = tokenIssuer.issueValidToken(TestUsers.default())

        webTestClient.get().uri("/api/users/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody<UserProfileResponse>()
            .isNotEmpty
    }

    "protected endpoint returns 401 with invalid JWT" {
        webTestClient.get().uri("/api/users/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token")
            .exchange()
            .expectStatus().isUnauthorized
    }

    "admin endpoints return 403 for USER role" {
        val token = tokenIssuer.issueUserToken(TestUsers.default())

        webTestClient.get().uri("/api/admin/users")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isForbidden
    }
})
```

## Integration Testing with Testcontainers (PostgreSQL/R2DBC)

### Security Integration Tests via WebTestClient

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SecurityIntegrationTest(
    private val webTestClient: WebTestClient,
) : StringSpec({
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:18")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")

        @DynamicPropertySource
        @JvmStatic
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url") { "r2dbc:postgresql://${postgres.host}:${postgres.firstMappedPort}/${postgres.databaseName}" }
            registry.add("spring.r2dbc.username") { postgres.username }
            registry.add("spring.r2dbc.password") { postgres.password }
        }
    }

    "complete authentication flow" {
        val loginRequest = LoginRequest(email = "user@profiletailors.com", password = "password")

        val accessToken = webTestClient.post().uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(loginRequest)
            .exchange()
            .expectStatus().isOk
            .expectBody<LoginResponse>()
            .returnResult()
            .responseBody!!.accessToken

        webTestClient.get().uri("/api/users/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .exchange()
            .expectStatus().isOk
            .expectBody<UserProfileResponse>()
            .value { it.email shouldBe "user@profiletailors.com" }
    }

    "refresh token flow" {
        val login = LoginRequest("user@profiletailors.com", "password")

        val refreshToken = webTestClient.post().uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(login)
            .exchange()
            .expectStatus().isOk
            .expectBody<LoginResponse>()
            .returnResult()
            .responseBody!!.refreshToken

        webTestClient.post().uri("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(RefreshTokenRequest(refreshToken))
            .exchange()
            .expectStatus().isOk
            .expectBody<RefreshTokenResponse>()
            .value { it.accessToken.isNotBlank() shouldBe true }
    }

    "logout invalidates subsequent requests" {
        val login = LoginRequest("user@profiletailors.com", "password")

        val token = webTestClient.post().uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(login)
            .exchange()
            .expectStatus().isOk
            .expectBody<LoginResponse>()
            .returnResult()
            .responseBody!!.accessToken

        webTestClient.post().uri("/api/auth/logout")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk

        webTestClient.get().uri("/api/users/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isUnauthorized
    }
})
```

### Testing Reactive Custom Filters

```kotlin
class JwtAuthenticationWebFilterTest : StringSpec({
    val tokenService = mockk<JwtTokenService>()
    val userDetailsService = mockk<ReactiveUserDetailsService>()
    val filter = JwtAuthenticationWebFilter(tokenService, userDetailsService)
    val exchange = mockk<ServerWebExchange>(relaxed = true)
    val chain = mockk<WebFilterChain>(relaxed = true)
    val request = mockk<ServerHttpRequest>(relaxed = true)

    beforeEach {
        clearMocks(tokenService, userDetailsService, exchange, chain, request)
        exchange.request returns request
    }

    "should authenticate with valid token" {
        val token = "valid.jwt.token"
        val username = "user@profiletailors.com"
        val userDetails = UserAccount(
            id = UserId(1L),
            email = username,
            passwordHash = "password",
            firstName = "Test",
            lastName = "User",
            enabled = true,
        )

        every { request.headers.header(HttpHeaders.AUTHORIZATION) } returns listOf("Bearer $token")
        every { tokenService.isTokenValid(token) } returns true
        every { tokenService.extractUsername(token) } returns username
        every { userDetailsService.findByUsername(username) } returns Mono.just(userDetails)
        every { chain.filter(any()) } returns Mono.empty()

        filter.filter(exchange, chain).awaitSingleOrNull()
        verify { chain.filter(exchange) }
    }

    "should skip authentication when no token" {
        every { request.headers.header(HttpHeaders.AUTHORIZATION) } returns emptyList()
        every { chain.filter(any()) } returns Mono.empty()

        filter.filter(exchange, chain).awaitSingleOrNull()
        verify { chain.filter(exchange) }
    }

    "should chain through when token is invalid" {
        val token = "invalid.jwt.token"
        every { request.headers.header(HttpHeaders.AUTHORIZATION) } returns listOf("Bearer $token")
        every { tokenService.isTokenValid(token) } returns false
        every { tokenService.extractUsername(token) } returns "user@profiletailors.com"
        every { chain.filter(any()) } returns Mono.empty()

        filter.filter(exchange, chain).awaitSingleOrNull()
        verify { chain.filter(exchange) }
    }
})
```

## Testing Authorization

### Method-Level Security Tests with Reactive @WithMockUser

```kotlin
class DocumentServiceSecurityTest(
    val documentRepository: DocumentRepository = mockk(),
) : StringSpec({
    val documentService = DocumentService(documentRepository)

    "user should access own documents" {
        val documentId = DocumentId(1L)
        val document = TestDocuments.draft()

        every { documentRepository.findById(documentId) } returns Mono.just(document)

        withMockUser(roles = arrayOf("USER")).run {
            runBlocking {
                val result = documentService.getMyDocument(documentId, currentAuthentication())
                result shouldBe document
            }
        }
        verify { documentRepository.findById(documentId) }
    }

    "user cannot delete all documents" {
        withMockUser(roles = arrayOf("USER")).run {
            shouldThrow<AccessDeniedException> {
                runBlocking { documentService.deleteAllDocuments() }
            }
        }
    }

    "admin can delete all documents" {
        withMockUser(roles = arrayOf("ADMIN")).run {
            runBlocking { documentService.deleteAllDocuments() }
        }
        verify { documentRepository.deleteAll() }
    }

    "permission-based authorization works" {
        val documentId = DocumentId(1L)
        val document = TestDocuments.draft().copy(ownerId = UserId(2L))
        val permission = SimpleGrantedAuthority("DOCUMENT_APPROVE")
        val authentication = UsernamePasswordAuthenticationToken(
            "user@profiletailors.com",
            "n/a",
            listOf(permission),
        )

        every { documentRepository.findById(documentId) } returns Mono.just(document)

        runBlocking {
            val result = documentService.approveDocument(documentId, authentication)
            result shouldBe document.copy(status = DocumentStatus.APPROVED)
        }
    }
})
```

### Testing Custom Permission Evaluator

```kotlin
@SpringBootTest
@AutoConfigureWebTestClient
class CustomPermissionEvaluatorTest(
    val webTestClient: WebTestClient,
) : StringSpec({
    "owner can access own document" {
        val document = TestDocuments.ownedBy(TestUsers.default())
        val token = TestJwtIssuer().issueUserToken(TestUsers.default())

        webTestClient.get().uri("/api/documents/${document.id.value}")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
    }

    "department member can read department document" {
        val document = TestDocuments.inDepartment("FINANCE")
        val token = TestJwtIssuer().issueDepartmentToken(TestUsers.default(), "FINANCE")

        webTestClient.get().uri("/api/documents/${document.id.value}")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
    }

    "time-based permission respects business hours" {
        val accessTime = LocalDate.now().atTime(14, 0)
            .atZone(ZoneId.systemDefault()).toInstant()
        val token = TestJwtIssuer().issueTimeScopedToken(TestUsers.default(), accessTime)

        webTestClient.get().uri("/api/documents/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
    }

    "time-based permission denies access outside business hours" {
        val accessTime = LocalDate.now().atTime(2, 0)
            .atZone(ZoneId.systemDefault()).toInstant()
        val token = TestJwtIssuer().issueTimeScopedToken(TestUsers.default(), accessTime)

        webTestClient.get().uri("/api/documents/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isForbidden
    }
})
```

## Performance Testing

### JWT Token Generation Performance Test (Kotest)

```kotlin
class JwtPerformanceTest(
    val tokenService: JwtTokenService,
    val userRepository: UserRepositoryR2dbc,
) : StringSpec({
    "token generation stays under 10ms per token at 1000 iterations" {
        val users = (1..1000).map { TestUsers.indexed(it) }
        val iterations = 1000

        val duration = measureTimeMillis {
            runBlocking {
                users.take(iterations).forEach { user ->
                    tokenService.generateAccessToken(user)
                }
            }
        }

        val avgPerToken = duration.toDouble() / iterations
        println("Average token generation time: %.2f ms".format(avgPerToken))
        avgPerToken shouldBeLessThan 10.0
    }

    "token validation stays under 5ms per token at 1000 iterations" {
        val tokens = (1..1000).map { TestUsers.indexed(it) }
            .map { runBlocking { tokenService.generateAccessToken(it).token } }

        val (duration, validCount) = measureTimedValue {
            runBlocking {
                tokens.count { runBlocking { tokenService.isTokenValid(it) } }
            }
        }

        val avgPerValidation = duration.toDouble() / tokens.size
        println("Average token validation time: %.2f ms".format(avgPerValidation))
        avgPerValidation shouldBeLessThan 5.0
        validCount shouldBe tokens.size
    }
})
```

## Security Scanning Tests via WebTestClient

### Basic Smoke Suite

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityScanTest(
    val webTestClient: WebTestClient,
) : StringSpec({
    "application resists common vulnerability classes" {
        testSqlInjectionProtection()
        testXssProtection()
        testCsrfProtection()
        testAuthenticationBypassProtection()
        testAuthorizationBypassProtection()
    }

    "SQL injection attempts return 401 or 400, never 500" {
        val attempts = listOf(
            "'; DROP TABLE users; --",
            "' OR '1'='1",
            "' UNION SELECT * FROM users --",
        )
        attempts.forEach { maliciousEmail ->
            webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(LoginRequest(email = maliciousEmail, password = "password"))
                .exchange()
                .expectStatus().is4xxClientError
        }
    }

    "XSS payload is escaped in registration responses" {
        val xssPayload = "<script>alert('xss')</script>"

        val response = webTestClient.post().uri("/api/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                RegistrationRequest(
                    email = "test@profiletailors.com",
                    password = "password",
                    firstName = xssPayload,
                    lastName = "Test",
                ),
            )
            .exchange()
            .returnResult<String>()

        if (response.status == HttpStatus.CREATED) {
            response.responseBody?.first()?.data?.let { body ->
                body.contains("<script>") shouldBe false
                body.contains("&lt;script&gt;") shouldBe true
            }
        }
    }

    "state-changing operations require CSRF token" {
        webTestClient.post().uri("/api/users/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().is4xxClientError
    }

    "protected endpoints reject unauthenticated calls" {
        listOf("/api/users/me", "/api/documents", "/api/orders").forEach { endpoint ->
            webTestClient.get().uri(endpoint).exchange()
                .expectStatus().isUnauthorized
        }
    }

    "admin endpoints reject USER tokens" {
        val token = TestJwtIssuer().issueUserToken(TestUsers.default())
        listOf("/api/admin/users", "/api/admin/roles", "/api/admin/permissions").forEach { endpoint ->
            webTestClient.get().uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .exchange()
                .expectStatus().isForbidden
        }
    }
})
```
