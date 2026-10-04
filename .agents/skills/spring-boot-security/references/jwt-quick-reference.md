# JWT Quick Reference Card

Quick reference for common JWT patterns in the reactive SMP backend. All examples
use the WebFlux security stack (`ServerHttpSecurity`, `SecurityWebFilterChain`,
`WebFilter`, `suspend fun`). Versions for the JJWT artifact live in
`gradle/libs.versions.toml`; the snippet below uses the placeholder `X.Y.Z` for
illustration.

## Dependencies

```xml
<!-- Maven -->
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-api</artifactId>
  <version>${jjwt.version}</version>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-impl</artifactId>
  <version>${jjwt.version}</version>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-jackson</artifactId>
  <version>${jjwt.version}</version>
  <scope>runtime</scope>
</dependency>
```

```kotlin
// Gradle
implementation("io.jsonwebtoken:jjwt-api")
implementation("io.jsonwebtoken:jjwt-impl")
implementation("io.jsonwebtoken:jjwt-jackson")
```

The exact version is declared in `gradle/libs.versions.toml` under `jjwt`; this
reference intentionally pins no literal version.

## Basic JWT Service

```kotlin
@com.profiletailors.common.domain.Service
class JwtService(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.access-token-expiration}") private val expiration: Long,
) {
    suspend fun generateToken(user: UserAccount): String = Jwts.builder()
        .subject(user.email)
        .issuedAt(Date())
        .expiration(Date(System.currentTimeMillis() + expiration))
        .signWith(Keys.hmacShaKeyFor(secret.toByteArray()), Jwts.SIG.HS256)
        .compact()

    fun extractUsername(token: String): String = extractClaim(token) { it.subject }

    suspend fun isTokenValid(token: String, user: UserAccount): Boolean {
        val username = extractUsername(token)
        return username == user.email && !isTokenExpired(token)
    }

    private fun isTokenExpired(token: String): Boolean =
        extractExpiration(token).before(Date())

    private fun extractExpiration(token: String): Date =
        extractClaim(token) { it.expiration }

    private fun <T> extractClaim(token: String, resolver: (Claims) -> T): T {
        val claims = Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(secret.toByteArray()))
            .build()
            .parseSignedClaims(token)
            .payload
        return resolver(claims)
    }
}
```

## Security Configuration

```kotlin
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
class SecurityConfig {
    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain = http
        .csrf { it.disable() }
        .anonymous { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeExchange { authz ->
            authz
                .pathMatchers("/api/auth/**").permitAll()
                .pathMatchers("/api/admin/**").hasRole("ADMIN")
                .anyExchange().authenticated()
        }
        .authenticationManager(authenticationManager())
        .addFilterAt(jwtAuthFilter(), SecurityWebFiltersOrder.HTTP_BASIC)
        .build()

    @Bean
    fun authenticationManager(): ReactiveAuthenticationManager {
        val provider = DaoReactiveAuthenticationProvider(userDetailsService)
        provider.setPasswordEncoder(passwordEncoder())
        return provider
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
```

## JWT Reactive Filter

```kotlin
@Component
class JwtAuthenticationWebFilter(
    private val jwtService: JwtService,
    private val userDetailsService: ReactiveUserDetailsService,
) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val authHeader = exchange.request.headers.header("Authorization").firstOrNull()

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange)
        }

        val token = authHeader.substring(7)
        val username = jwtService.extractUsername(token)

        return exchange.exchange.getPrincipal<Authentication>()
            .filter { null == it || it.name == username }
            .switchIfEmpty(
                userDetailsService.findByUsername(username)
                    .filter { jwtService.isTokenValid(token, it) }
                    .flatMap { user ->
                        val authorities = user.authorities().map { SimpleGrantedAuthority(it.authority) }
                        val auth = UsernamePasswordAuthenticationToken(user, token, authorities)
                        exchange.exchange.withPrincipal(auth)
                    },
            )
            .flatMap { chain.filter(exchange) }
    }
}

private suspend fun ServerWebExchange.exchange.getPrincipal(): Mono<Authentication> =
    principal().awaitSingleOrNull()?.let { Mono.just(it) } ?: Mono.empty()
```

The `principal()` accessor on `ServerWebExchange` is implemented per bounded context
through the `ServerSecurityContextRepository` adapter wired in
`configuration.md#security-web-filter-chain-options`.

## Authentication Controller

```kotlin
@RestController
@RequestMapping("/api/auth")
class AuthenticationController(
    private val service: AuthenticationService,
) {
    @PostMapping("/register")
    suspend fun register(@Valid @RequestBody request: RegisterRequest): AuthenticationResponse =
        service.register(request)

    @PostMapping("/authenticate")
    suspend fun authenticate(@Valid @RequestBody request: AuthenticationRequest): AuthenticationResponse =
        service.authenticate(request)

    @PostMapping("/refresh")
    suspend fun refresh(@Valid @RequestBody request: RefreshRequest): AuthenticationResponse =
        service.refresh(request)

    @PostMapping("/logout")
    suspend fun logout(@Valid @RequestBody request: LogoutRequest) {
        service.logout(request)
    }
}
```

## Application Properties

```yaml
jwt:
  secret: ${JWT_SECRET:change-me-please-use-a-strong-secret-with-at-least-32-chars}
  access-token-expiration: 900000
  refresh-token-expiration: 604800000
  issuer: ${JWT_ISSUER:profiletailors-smp}
  cookie:
    name: jwt-token
    secure: ${JWT_COOKIE_SECURE:true}
    http-only: true
    same-site: strict

spring:
  r2dbc:
    url: ${SPRING_R2DBC_URL:r2dbc:postgresql://localhost:5432/profiletailors}
    username: ${SPRING_R2DBC_USERNAME:profiletailors}
    password: ${SPRING_R2DBC_PASSWORD:profiletailors}
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${JWT_ISSUER_URI:https://auth.profiletailors.com}
```

## Common JWT Operations

### Generate Token with Claims

```kotlin
suspend fun generateTokenWithClaims(user: UserAccount, extraClaims: Map<String, Any>): String {
    val authorities = user.authorities().map { it.authority }
    return Jwts.builder()
        .claims(extraClaims)
        .subject(user.email)
        .issuedAt(Date())
        .expiration(Date(System.currentTimeMillis() + expiration))
        .claim("roles", authorities)
        .signWith(Keys.hmacShaKeyFor(secret.toByteArray()), Jwts.SIG.HS256)
        .compact()
}
```

### Validate Token with Clock Skew

```kotlin
suspend fun isTokenValidWithSkew(token: String, user: UserAccount): Boolean = try {
    Jwts.parser()
        .verifyWith(Keys.hmacShaKeyFor(secret.toByteArray()))
        .clockSkewSeconds(60)
        .build()
        .parseSignedClaims(token)
    true
} catch (_: JwtException) {
    false
}
```

### Extract All Claims

```kotlin
fun extractAllClaims(token: String): Claims = Jwts.parser()
    .verifyWith(Keys.hmacShaKeyFor(secret.toByteArray()))
    .build()
    .parseSignedClaims(token)
    .payload
```

## Security Best Practices

### 1. Use Strong Keys

```kotlin
val key: SecretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256)
val base64Key: String = Encoders.BASE64.encode(key.encoded)
```

### 2. Set Appropriate Expiration

```kotlin
val accessTokenExpiration: Long = Duration.ofMinutes(15).toMillis()
val refreshTokenExpiration: Long = Duration.ofDays(7).toMillis()
```

### 3. Validate All Claims

```kotlin
suspend fun validateAllClaims(token: String): Boolean = try {
    Jwts.parser()
        .verifyWith(Keys.hmacShaKeyFor(secret.toByteArray()))
        .requireIssuer("profiletailors-smp")
        .requireAudience("profiletailors-client")
        .build()
        .parseSignedClaims(token)
    true
} catch (_: JwtException) {
    false
}
```

### 4. Implement Token Blacklisting

```kotlin
@com.profiletailors.common.domain.Service
class TokenBlacklistService {
    private val blacklistedTokens: Set<String> = ConcurrentHashMap.newKeySet()

    fun blacklistToken(token: String) {
        blacklistedTokens.add(token)
    }

    fun isBlacklisted(token: String): Boolean = blacklistedTokens.contains(token)
}
```

For production deployments the blacklist is persisted through
`BlacklistedTokenRepository` (R2DBC) instead of the in-memory set shown above.

## Testing JWT

### Unit Test JWT Service (Kotest)

```kotlin
class JwtServiceTest : StringSpec({
    val jwtService = JwtService(secret = TestJwts.TestSecret, expiration = 15 * 60 * 1000)

    "should generate and parse a valid token" {
        val user = TestUsers.default()
        val token = jwtService.runBlocking { generateToken(user) }

        token.shouldNotBeNull()
        jwtService.extractUsername(token) shouldBe user.email
        jwtService.runBlocking { isTokenValid(token, user) } shouldBe true
    }
})
```

### Integration Test Authentication (WebTestClient)

```kotlin
@SpringBootTest
@AutoConfigureWebTestClient
class AuthenticationIntegrationTest {
    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun shouldAuthenticateUser() {
        val request = LoginRequest(email = "user@profiletailors.com", password = "password")

        webTestClient.post()
            .uri("/api/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.accessToken").exists()
            .jsonPath("$.refreshToken").exists()
    }
}
```

## Error Handling

### JWT Reactive Exception Handler

```kotlin
@RestControllerAdvice
class JwtExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(JwtException::class)
    fun handleJwtException(e: JwtException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(
                ErrorResponse(
                    code = "invalid_token",
                    message = "Invalid token",
                    details = mapOf("cause" to (e.message ?: "")),
                ),
            )

    @ExceptionHandler(ExpiredJwtException::class)
    fun handleExpiredJwtException(e: ExpiredJwtException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(
                ErrorResponse(
                    code = "token_expired",
                    message = "The authentication token has expired",
                    details = emptyMap(),
                ),
            )
}
```

## Common Issues

### Issue: Invalid Key Length

**Error**: The signing key's size is 184 bits which is not secure enough for the HS256 algorithm.
**Solution**: Use a key of at least 256 bits (32 characters).

### Issue: Clock Skew

**Error**: JWT is expired or not yet valid **Solution**: Add clock skew tolerance.

```kotlin
Jwts.parser()
    .clockSkewSeconds(60)
    .build()
    .parseSignedClaims(token)
```

### Issue: CORS Issues

**Solution**: Configure CORS through the reactive `CorsConfigurationSource` bean.

```kotlin
@Bean
fun corsConfigurationSource(): CorsConfigurationSource {
    val configuration = CorsConfiguration().apply {
        allowedOriginPatterns = listOf("http://localhost:*")
        allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        allowedHeaders = listOf("*")
        allowCredentials = true
    }

    return UrlBasedCorsConfigurationSource().apply {
        registerCorsConfiguration("/**", configuration)
    }
}
```

## References

- [JJWT Documentation](https://github.com/jwtk/jjwt)
- [Spring Security WebFlux OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/reactive/oauth2/resource-server/index.html)
- [RFC 7519 - JSON Web Token (JWT)](https://tools.ietf.org/html/rfc7519)
- [RFC 8725 - JWT Best Practices](https://tools.ietf.org/html/rfc8725)
