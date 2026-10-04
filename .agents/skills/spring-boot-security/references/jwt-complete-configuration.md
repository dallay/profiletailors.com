# JWT Complete Configuration Guide

This guide consolidates JWT configuration patterns for the reactive SMP backend. It
covers JJWT integration, the WebFlux OAuth2 resource server configuration, and
production-ready security settings. All examples use the reactive stack (`ServerHttpSecurity`,
`SecurityWebFilterChain`, `WebFilter`, `R2dbcRepository`,
`suspend fun`). Versions for the JJWT artifact live in `gradle/libs.versions.toml`.

## Table of Contents

1. [Application Properties](#application-properties)
2. [Security Configuration](#security-configuration)
3. [JWT Service Configuration](#jwt-service-configuration)
4. [OAuth2 Resource Server](#oauth2-resource-server)
5. [Advanced Configuration](#advanced-configuration)
6. [Performance Optimization](#performance-optimization)
7. [Troubleshooting](#troubleshooting)

## Application Properties

### Basic JWT Configuration

```yaml
jwt:
  secret: ${JWT_SECRET:change-me-please-use-a-strong-secret-with-at-least-32-chars}

  access-token-expiration: 900000
  refresh-token-expiration: 604800000

  issuer: ${JWT_ISSUER:profiletailors-smp}

  cookie:
    name: ${JWT_COOKIE_NAME:jwt-token}
    secure: ${JWT_COOKIE_SECURE:true}
    http-only: true
    same-site: ${JWT_COOKIE_SAME_SITE:strict}
    max-age: ${JWT_COOKIE_MAX_AGE:86400}
    domain: ${JWT_COOKIE_DOMAIN:profiletailors.com}
    path: /

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
          jwk-set-uri: ${JWT_JWK_SET_URI:https://auth.profiletailors.com/.well-known/jwks.json}
          public-key-location: ${JWT_PUBLIC_KEY_LOCATION:classpath:public.pem}
```

### Environment-Specific Configuration

```yaml
---
spring:
  config:
    activate:
      on-profile: dev

jwt:
  cookie:
    secure: false
    same-site: lax

logging:
  level:
    io.jsonwebtoken: DEBUG
    org.springframework.security: DEBUG

---
spring:
  config:
    activate:
      on-profile: prod

jwt:
  cookie:
    secure: true
    same-site: strict
    domain: api.profiletailors.com
  secret: ${JWT_SECRET}
```

## Security Configuration

### Reactive WebFlux Security 6.x Configuration

```kotlin
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthenticationWebFilter,
    private val authenticationManager: ReactiveAuthenticationManager,
    private val logoutHandler: ServerLogoutHandler,
    private val jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint,
) {
    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain = http
        .csrf { it.disable() }
        .cors { it.configurationSource(corsConfigurationSource()) }
        .anonymous { it.disable() }
        .sessionManagement { session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        }
        .exceptionHandling { ex ->
            ex.authenticationEntryPoint(jwtAuthenticationEntryPoint)
        }
        .authorizeExchange { authz ->
            authz
                .pathMatchers("/api/auth/**").permitAll()
                .pathMatchers("/api/public/**").permitAll()
                .pathMatchers("/actuator/health").permitAll()
                .pathMatchers(HttpMethod.GET, "/api-docs/**").permitAll()
                .pathMatchers(HttpMethod.GET, "/swagger-ui/**").permitAll()
                .pathMatchers(HttpMethod.GET, "/swagger-ui.html").permitAll()
                .pathMatchers("/api/admin/**").hasRole("ADMIN")
                .anyExchange().authenticated()
        }
        .authenticationManager(authenticationManager)
        .addFilterAt(jwtAuthFilter, SecurityWebFiltersOrder.HTTP_BASIC)
        .logout { logout ->
            logout
                .logoutUrl("/api/auth/logout")
                .logoutHandler(logoutHandler)
                .logoutSuccessHandler { _, _, _ -> }
        }
        .build()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOriginPatterns = getAllowedOrigins()
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = 3600L
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }

    private fun getAllowedOrigins(): List<String> = listOf(
        "http://localhost:3000",
        "http://localhost:4200",
        "https://profiletailors.com",
    )
}
```

### Reactive JWT Authentication Filter

```kotlin
@Component
class JwtAuthenticationWebFilter(
    private val jwtService: JwtService,
    private val userDetailsService: ReactiveUserDetailsService,
    private val blacklistService: TokenBlacklistService,
) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val authHeader = exchange.request.headers.header("Authorization").firstOrNull()

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange)
        }

        val token = authHeader.substring(7)

        if (blacklistService.isBlacklisted(token)) {
            logger.warn("Blacklisted JWT token detected")
            return chain.filter(exchange)
        }

        val username = try {
            jwtService.extractUsername(token)
        } catch (_: JwtException) {
            logger.error("Invalid JWT token")
            return chain.filter(exchange)
        }

        return Mono.justOrEmpty(username)
            .flatMap { email ->
                userDetailsService.findByUsername(email)
                    .filter { jwtService.isTokenValid(token, it) }
                    .map { user ->
                        UsernamePasswordAuthenticationToken(
                            user,
                            token,
                            user.authorities().map { SimpleGrantedAuthority(it.authority) },
                        )
                    }
                    .switchIfEmpty(Mono.empty())
            }
            .flatMap { auth -> chain.filter(exchange.mutate().principal(auth).build()) }
            .switchIfEmpty(chain.filter(exchange))
    }

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(JwtAuthenticationWebFilter::class.java)
    }
}
```

## JWT Service Configuration

### JWT Application Service

```kotlin
@com.profiletailors.common.domain.Service
class JwtService(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.access-token-expiration}") private val accessTokenExpiration: Long,
    @Value("\${jwt.refresh-token-expiration}") private val refreshTokenExpiration: Long,
    @Value("\${jwt.issuer}") private val issuer: String,
    private val secretKeyRepository: SecretKeyRepository,
    private val cacheManager: CacheManager,
) {
    suspend fun generateToken(user: UserAccount): String =
        generateToken(emptyMap(), user)

    suspend fun generateToken(extraClaims: Map<String, Any>, user: UserAccount): String =
        buildToken(extraClaims, user, accessTokenExpiration)

    suspend fun generateRefreshToken(user: UserAccount): String =
        buildToken(emptyMap(), user, refreshTokenExpiration)

    private fun buildToken(extraClaims: Map<String, Any>, user: UserAccount, expiration: Long): String {
        val signingKey = getCurrentSigningKey()
        return Jwts.builder()
            .claims(extraClaims)
            .subject(user.email)
            .issuedAt(Date(System.currentTimeMillis()))
            .expiration(Date(System.currentTimeMillis() + expiration))
            .issuer(issuer)
            .id(UUID.randomUUID().toString())
            .claim("authorities", user.authorities().map { it.authority })
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact()
    }

    fun extractUsername(token: String): String = extractClaim(token) { it.subject }

    fun <T> extractClaim(token: String, claimsResolver: (Claims) -> T): T {
        val claims = extractAllClaims(token)
        return claimsResolver(claims)
    }

    suspend fun isTokenValid(token: String, user: UserAccount): Boolean {
        val username = extractUsername(token)
        return username == user.email && !isTokenExpired(token)
    }

    private fun isTokenExpired(token: String): Boolean =
        extractExpiration(token).before(Date())

    private fun extractExpiration(token: String): Date =
        extractClaim(token) { it.expiration }

    private fun extractAllClaims(token: String): Claims {
        val signingKey = getCurrentSigningKey()
        return Jwts.parser()
            .verifyWith(signingKey)
            .requireIssuer(issuer)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    private fun getCurrentSigningKey(): SecretKey =
        secretKeyRepository.findCurrentKey()?.key ?: run {
            val newKey = Keys.secretKeyFor(SignatureAlgorithm.HS256)
            secretKeyRepository.save(SecretKeyEntity(newKey, LocalDateTime.now()))
            newKey
        }
}
```

### Key Rotation Service

```kotlin
@com.profiletailors.common.domain.Service
class JwtKeyRotationService(
    private val keyRepository: SecretKeyRepository,
    private val cacheManager: CacheManager,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Value("\${jwt.key-rotation.enabled:true}")
    private val keyRotationEnabled: Boolean = true

    @Value("\${jwt.key-rotation.cron:0 0 0 * * ?}")
    private val rotationCron: String = "0 0 0 * * ?"

    @Scheduled(cron = "\${jwt.key-rotation.cron}")
    suspend fun rotateKeys() {
        if (!keyRotationEnabled) {
            logger.info("JWT key rotation is disabled")
            return
        }

        try {
            val newKey = Keys.secretKeyFor(SignatureAlgorithm.HS256)
            val keyEntity = SecretKeyEntity(newKey, LocalDateTime.now())

            keyRepository.save(keyEntity)
            cacheManager.getCache("jwt-keys")?.clear()
            eventPublisher.publishEvent(KeyRotatedEvent(this, keyEntity.id))
            logger.info("JWT signing key rotated successfully")
        } catch (e: Exception) {
            logger.error("Failed to rotate JWT signing key", e)
        }
    }

    fun getCurrentSigningKey(): SecretKey = keyRepository.findCurrentKey()?.key
        ?: throw IllegalStateException("No signing key available")

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(JwtKeyRotationService::class.java)
    }
}
```

## OAuth2 Resource Server

### Reactive Resource Server Configuration

```kotlin
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
class ResourceServerConfig(
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private val issuerUri: String,
) {
    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain = http
        .authorizeExchange { authz ->
            authz
                .pathMatchers("/api/public/**").permitAll()
                .pathMatchers("/actuator/health").permitAll()
                .anyExchange().authenticated()
        }
        .oauth2ResourceServer { oauth2 ->
            oauth2.jwt { jwt ->
                jwt.jwtDecoder(jwtDecoder())
            }
        }
        .build()

    @Bean
    fun jwtDecoder(): ReactiveJwtDecoder = JwtDecoders.fromIssuerLocation(issuerUri)

    @Bean
    fun jwtAuthenticationConverter(): Converter<Jwt, Mono<AbstractAuthenticationToken>> {
        val authoritiesConverter = JwtGrantedAuthoritiesConverter().apply {
            setAuthorityPrefix("ROLE_")
            setAuthoritiesClaimName("roles")
        }
        return JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter(authoritiesConverter)
            setPrincipalClaimName("sub")
        }
    }
}
```

### Custom Reactive JWT Decoder

```kotlin
@Component
class CustomReactiveJwtDecoder(
    private val nimbusJwtDecoder: NimbusReactiveJwtDecoder,
    private val blacklistService: TokenBlacklistService,
) : ReactiveJwtDecoder {
    override fun decode(token: String): Mono<Jwt> {
        if (blacklistService.isBlacklisted(token)) {
            return Mono.error(BadJwtException("Token has been blacklisted"))
        }
        return nimbusJwtDecoder.decode(token).doOnNext { validateCustomClaims(it) }
    }

    private fun validateCustomClaims(jwt: Jwt) {
        if (!jwt.claims.containsKey("tenant_id")) {
            throw BadJwtException("Missing tenant_id claim")
        }
        val tokenIp = jwt.getClaimAsString("ip_address")
        if (tokenIp != null && tokenIp != currentIpAddress()) {
            throw BadJwtException("Token IP mismatch")
        }
    }

    private fun currentIpAddress(): String = ""
}
```

## Advanced Configuration

### Token Blacklisting (R2DBC)

```kotlin
@com.profiletailors.common.domain.Service
class TokenBlacklistService(
    private val blacklistedTokenRepository: BlacklistedTokenRepository,
) {
    @Value("\${jwt.blacklist.enabled:true}")
    private val blacklistEnabled: Boolean = true

    suspend fun blacklistToken(token: String) {
        if (!blacklistEnabled) return

        val tokenId = extractTokenId(token)
        val remainingMillis = calculateRemainingTime(token)

        blacklistedTokenRepository.save(
            BlacklistedToken(
                tokenId = tokenId,
                token = token,
                expiresAt = Instant.now().plusMillis(remainingMillis),
            ),
        )
    }

    suspend fun isBlacklisted(token: String): Boolean {
        if (!blacklistEnabled) return false
        val tokenId = extractTokenId(token)
        return blacklistedTokenRepository.existsByTokenId(tokenId)
    }

    private fun extractTokenId(token: String): String =
        MessageDigest.getInstance("MD5").digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun calculateRemainingTime(token: String): Long = try {
        val claims = Jwts.parser()
            .build()
            .parseSignedClaims(token)
            .payload
        val exp = claims.get("exp", Long::class.java)
        (exp?.times(1000) ?: 0L) - System.currentTimeMillis()
    } catch (_: Exception) {
        0L
    }
}
```

### Rate Limiting

```kotlin
@Configuration
@EnableCaching
class RateLimitConfig {
    @Bean
    fun cacheManager(): CacheManager =
        ConcurrentMapCacheManager("login-attempts", "jwt-requests")
}

@com.profiletailors.common.domain.Service
class JwtRateLimitService(
    private val cacheManager: CacheManager,
) {
    @Value("\${jwt.rate-limit.enabled:true}")
    private val rateLimitEnabled: Boolean = true

    @Value("\${jwt.rate-limit.max-attempts:5}")
    private val maxAttempts: Int = 5

    @Value("\${jwt.rate-limit.time-window:300000}")
    private val timeWindow: Long = 5 * 60 * 1000

    fun isRateLimited(identifier: String): Boolean {
        if (!rateLimitEnabled) return false

        val cache = cacheManager.getCache("jwt-requests") ?: return false
        val key = "rate-limit:$identifier"

        val attempts = cache.get(key, AtomicInteger::class.java) ?: AtomicInteger(0)
        val currentAttempts = attempts.incrementAndGet()
        cache.put(key, attempts)

        if (currentAttempts >= maxAttempts) {
            logger.warn("Rate limit exceeded for identifier: {}", identifier)
            return true
        }

        return false
    }

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(JwtRateLimitService::class.java)
    }
}
```

## Performance Optimization

### JWT Parsing Optimization

```kotlin
@com.profiletailors.common.domain.Service
class OptimizedJwtService(
    private val cacheManager: CacheManager,
    private val keyRepository: SecretKeyRepository,
) {
    @Cacheable(value = ["jwt-parsing"], key = "#token")
    fun parseToken(token: String): Claims {
        val key = getCurrentSigningKey()
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    @Cacheable(value = ["signing-keys"], key = "'current'")
    fun getCurrentSigningKey(): SecretKey = keyRepository.findCurrentKey()?.key
        ?: throw IllegalStateException("No signing key available")

    suspend fun generateTokenOptimized(user: UserAccount): String {
        val extraClaims = mutableMapOf<String, Any>()
        extraClaims["authorities"] = user.authorities().map { it.authority }
        extraClaims["user_id"] = user.id.value
        extraClaims["email"] = user.email

        return buildToken(extraClaims, user, accessTokenExpiration)
    }
}
```

### R2DBC Connection Pool Configuration

```yaml
spring:
  r2dbc:
    url: ${SPRING_R2DBC_URL:r2dbc:postgresql://localhost:5432/profiletailors}
    username: ${SPRING_R2DBC_USERNAME:profiletailors}
    password: ${SPRING_R2DBC_PASSWORD:profiletailors}
    pool:
      max-size: 20
      initial-size: 5
      max-idle-time: 300000
      max-acquire-time: 1200000
      max-create-connection-time: 20000
      max-validation-time: 5000
      leak-detection-threshold: 60000

  data:
    redis:
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5
          max-wait: 5000ms
```

## Troubleshooting

### Common Configuration Issues

1. **Invalid Key Length**

   ```
   Error: The signing key's size is 184 bits which is not secure enough for the HS256 algorithm.
   Solution: Use a key of at least 256 bits (32 characters) for HS256.
   ```

2. **Clock Skew Issues**

   ```kotlin
   Jwts.parser()
       .clockSkewSeconds(60)
       .build()
       .parseSignedClaims(token)
   ```

3. **Issuer Mismatch**

   ```kotlin
   Jwts.parser()
       .requireIssuer("profiletailors-smp")
       .build()
       .parseSignedClaims(token)
   ```

### Debug Configuration

```yaml
logging:
  level:
    io.jsonwebtoken: DEBUG
    org.springframework.security: DEBUG
    org.springframework.security.oauth2: DEBUG
    com.profiletailors: DEBUG
```

### Health Check Endpoint

```kotlin
@Component
class JwtHealthIndicator(
    private val jwtService: JwtService,
) : ReactiveHealthIndicator {
    override fun health(): Mono<Health> = Mono.fromCallable {
        val testToken = jwtService.generateTestToken()
        val isValid = jwtService.validateToken(testToken)
        if (isValid) {
            Health.up().withDetail("jwt", "Service is working").build()
        } else {
            Health.down().withDetail("jwt", "Token validation failed").build()
        }
    }.onErrorResume { error ->
        Mono.just(Health.down().withDetail("jwt", "Service error: ${error.message}").build())
    }
}
```

## References

- [JJWT Documentation](https://github.com/jwtk/jjwt)
- [Spring Security WebFlux OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/reactive/oauth2/resource-server/index.html)
- [RFC 7519 - JSON Web Token (JWT)](https://tools.ietf.org/html/rfc7519)
- [RFC 8725 - JWT Best Practices](https://tools.ietf.org/html/rfc8725)
