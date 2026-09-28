# JWT Security Configuration Reference

This document describes the JWT security configuration used in the reactive Spring Boot 4
backend (`server/smp/`). All examples below show WebFlux (`ServerHttpSecurity`,
`SecurityWebFilterChain`, `WebFilter`, `R2dbcRepository`, `suspend fun`) — the
servlet-stack configuration is described only in
`migration-spring-security-6x.md` for historic reference and is not used in production
code.

## Table of Contents

1. [Application Properties](#application-properties)
2. [JWT Configuration Beans](#jwt-configuration-beans)
3. [Security Web Filter Chain Options](#security-web-filter-chain-options)
4. [Token Validation Configuration](#token-validation-configuration)
5. [Key Management](#key-management)
6. [CORS and CSRF Configuration](#cors-and-csrf-configuration)

## Application Properties

### Complete JWT Configuration (application.yml)

```yaml
jwt:
  secret: ${JWT_SECRET:change-me-please-use-a-strong-secret-with-at-least-32-chars}
  access-token-expiration: 900000
  refresh-token-expiration: 604800000
  issuer: ${JWT_ISSUER:profiletailors-smp}
  audience: ${JWT_AUDIENCE:profiletailors-client}
  cookie-name: jwt-token
  cookie-secure: ${JWT_COOKIE_SECURE:false}
  cookie-http-only: true
  cookie-same-site: lax
  cookie-domain: ${JWT_COOKIE_DOMAIN:}
  cookie-path: /
  cookie-max-age: 86400
  validate-issuer: true
  validate-audience: false
  validate-expiration: true
  clock-skew-seconds: 60
  refresh-token-limit: 5
  refresh-token-rotation-enabled: true
  refresh-token-cleanup-enabled: true
  refresh-token-cleanup-cron: "0 0 2 * * ?"
  blacklist-enabled: true
  blacklist-cleanup-enabled: true
  blacklist-cleanup-cron: "0 0 3 * * ?"

spring:
  r2dbc:
    url: ${SPRING_R2DBC_URL:r2dbc:postgresql://localhost:5432/profiletailors}
    username: ${SPRING_R2DBC_USERNAME:profiletailors}
    password: ${SPRING_R2DBC_PASSWORD:profiletailors}
    pool:
      max-size: 20
      initial-size: 5
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: openid, profile, email
            redirect-uri: "{baseUrl}/login/oauth2/code/google"
            client-name: Google
          github:
            client-id: ${GITHUB_CLIENT_ID}
            client-secret: ${GITHUB_CLIENT_SECRET}
            scope: user:email
            redirect-uri: "{baseUrl}/login/oauth2/code/github"
            client-name: GitHub
        provider:
          google:
            authorization-uri: https://accounts.google.com/o/oauth2/v2/auth
            token-uri: https://oauth2.googleapis.com/token
            user-info-uri: https://www.googleapis.com/oauth2/v2/userinfo
          github:
            authorization-uri: https://github.com/login/oauth/authorize
            token-uri: https://github.com/login/oauth/access_token
            user-info-uri: https://api.github.com/user

  webflux:
    cors:
      allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:8080}
      allowed-methods: GET,POST,PUT,DELETE,OPTIONS
      allowed-headers: "*"
      allow-credentials: true
      max-age: 3600

logging:
  level:
    org.springframework.security: DEBUG
    io.jsonwebtoken: DEBUG
    com.profiletailors: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when_authorized
  security:
    enabled: true
```

## JWT Configuration Beans

### JWT Service Configuration

```kotlin
@Configuration
class JwtConfig(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.access-token-expiration}") private val accessTokenExpiration: Long,
    @Value("\${jwt.refresh-token-expiration}") private val refreshTokenExpiration: Long,
    @Value("\${jwt.issuer}") private val issuer: String,
    @Value("\${jwt.audience:}") private val audience: String,
    @Value("\${jwt.validate-issuer:true}") private val validateIssuer: Boolean,
    @Value("\${jwt.validate-audience:false}") private val validateAudience: Boolean,
    @Value("\${jwt.clock-skew-seconds:60}") private val clockSkewSeconds: Int,
) {
    @Bean
    fun jwtService(refreshTokenService: RefreshTokenService): JwtService = JwtService(
        secret,
        accessTokenExpiration,
        refreshTokenExpiration,
        issuer,
        audience,
        validateIssuer,
        validateAudience,
        clockSkewSeconds,
        refreshTokenService,
    )

    @Bean
    fun jwtParser(): JwtParser = Jwts.parser()
        .verifyWith(getSigningKey())
        .requireIssuer(issuer)
        .clockSkewSeconds(clockSkewSeconds.toLong())
        .build()

    @Bean
    fun getSigningKey(): SecretKey {
        val keyBytes = Decoders.BASE64.decode(
            Base64.getEncoder().encodeToString(secret.toByteArray()),
        )
        return Keys.hmacShaKeyFor(keyBytes)
    }

    @Bean
    fun claimsSetExtractor(): ClaimsSetExtractor = DefaultClaimsSetExtractor(
        issuer,
        audience,
        Duration.ofMillis(accessTokenExpiration),
    )
}
```

### Custom JWT Parser with Validation

```kotlin
@Configuration
class JwtParserConfig {
    @Bean
    fun jwtParser(signingKey: SecretKey, jwtProperties: JwtProperties): JwtParser {
        val parser = Jwts.parser()
            .verifyWith(signingKey)
            .clockSkewSeconds(jwtProperties.clockSkewSeconds.toLong())

        if (jwtProperties.validateIssuer) {
            parser.requireIssuer(jwtProperties.issuer)
        }

        if (jwtProperties.validateAudience && jwtProperties.audience.isNotBlank()) {
            parser.requireAudience(jwtProperties.audience)
        }

        return parser.build()
    }

    @Bean
    fun jwtValidator(jwtParser: JwtParser): JwtValidator = DefaultJwtValidator(jwtParser)
}
```

### Configuration Properties Class

```kotlin
@ConfigurationProperties(prefix = "jwt")
@Validated
data class JwtProperties(
    @field:NotBlank
    @field:Size(min = 32, message = "JWT secret must be at least 32 characters")
    var secret: String = "",

    @field:Min(60000)
    var accessTokenExpiration: Long = 900000,

    @field:Min(3600000)
    var refreshTokenExpiration: Long = 604800000,

    @field:NotBlank
    var issuer: String = "",

    var audience: String = "",

    var validateIssuer: Boolean = true,

    var validateAudience: Boolean = false,

    @field:Min(0)
    var clockSkewSeconds: Int = 60,

    var cookie: CookieProperties = CookieProperties(),
    var refreshToken: RefreshTokenProperties = RefreshTokenProperties(),
    var blacklist: BlacklistProperties = BlacklistProperties(),
) {
    data class CookieProperties(
        var name: String = "jwt-token",
        var secure: Boolean = false,
        var httpOnly: Boolean = true,
        var sameSite: String = "lax",
        var domain: String? = null,
        var path: String = "/",
        var maxAge: Int = 86400,
    )

    data class RefreshTokenProperties(
        var limit: Int = 5,
        var rotationEnabled: Boolean = true,
        var cleanupEnabled: Boolean = true,
        var cleanupCron: String = "0 0 2 * * ?",
    )

    data class BlacklistProperties(
        var enabled: Boolean = true,
        var cleanupEnabled: Boolean = true,
        var cleanupCron: String = "0 0 3 * * ?",
    )
}
```

## Security Web Filter Chain Options

### Advanced Reactive Security Configuration

```kotlin
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
class AdvancedSecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationWebFilter,
    private val authenticationManager: ReactiveAuthenticationManager,
    private val authenticationEntryPoint: JwtAuthenticationEntryPoint,
    private val accessDeniedHandler: CustomAccessDeniedHandler,
    private val corsConfigurationSource: SecurityCorsConfigurationSource,
    private val logoutHandler: ServerLogoutHandler,
    private val securityContextRepository: ServerSecurityContextRepository,
) {
    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain = http
        .cors { it.configurationSource(corsConfigurationSource) }
        .csrf { csrf ->
            csrf
                .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers("/api/auth/**", "/api/public/**")
                .sessionAuthenticationStrategy(NullServerSessionAuthenticationStrategy())
        }
        .headers { headers ->
            headers
                .contentSecurityPolicy { csp ->
                    csp.policyDirectives("default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'")
                }
                .frameOptions { it.deny() }
                .hsts { hsts ->
                    hsts
                        .maxAge(Duration.ofDays(365))
                        .includeSubdomains(true)
                        .preload(true)
                }
                .permissionsPolicy { permissions ->
                    permissions.policy("camera=(), microphone=(), geolocation=()")
                }
                .referrerPolicy { it.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN) }
        }
        .anonymous { it.disable() }
        .exceptionHandling { exceptions ->
            exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
        }
        .authorizeExchange { auth ->
            auth
                .pathMatchers(
                    "/api/auth/**",
                    "/api/public/**",
                    "/health",
                    "/actuator/health",
                ).permitAll()
                .pathMatchers("/api/admin/**").hasRole("ADMIN")
                .pathMatchers(HttpMethod.GET, "/api/users/**").hasAuthority("USER_READ")
                .pathMatchers(HttpMethod.POST, "/api/users/**").hasAuthority("USER_WRITE")
                .pathMatchers(HttpMethod.PUT, "/api/users/**").hasAuthority("USER_WRITE")
                .pathMatchers(HttpMethod.DELETE, "/api/users/**").hasAuthority("USER_DELETE")
                .pathMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                .pathMatchers("/actuator/**").hasRole("ADMIN")
                .anyExchange().authenticated()
        }
        .oauth2ResourceServer { oauth2 ->
            oauth2.jwt { jwt ->
                jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
            }
                .accessDeniedHandler(accessDeniedHandler)
                .authenticationEntryPoint(authenticationEntryPoint)
        }
        .oauth2Login { oauth2 ->
            oauth2
                .authorizationEndpoint { it.baseUri("/oauth2/authorization") }
                .redirectionEndpoint { it.baseUri("/login/oauth2/code/*") }
                .userInfoEndpoint { it.userService(oAuth2UserService()) }
                .successHandler(oAuth2AuthenticationSuccessHandler())
                .failureHandler(oAuth2AuthenticationFailureHandler())
        }
        .authenticationManager(authenticationManager)
        .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.HTTP_BASIC)
        .addFilterAt(auditLoggingFilter(), SecurityWebFiltersOrder.LOGOUT)
        .logout { logout ->
            logout
                .logoutUrl("/api/auth/logout")
                .logoutHandler(logoutHandler)
                .logoutHandler(cookieClearingLogoutHandler())
                .logoutSuccessHandler { _, response ->
                    response.setStatusCode(HttpStatus.NO_CONTENT)
                }
        }
        .securityContextRepository(securityContextRepository)
        .build()

    @Bean
    fun jwtDecoder(): JwtDecoder = NimbusReactiveJwtDecoder.withSecretKey(getSigningKey())
        .macAlgorithm(MacAlgorithm.HS256)
        .build()

    @Bean
    fun jwtAuthenticationConverter(): Converter<Jwt, Mono<AbstractAuthenticationToken>> {
        val authoritiesConverter = JwtGrantedAuthoritiesConverter().apply {
            setAuthorityPrefix("ROLE_")
            setAuthoritiesClaimName("authorities")
        }
        return JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter(authoritiesConverter)
            setPrincipalClaimName("sub")
        }
    }

    @Bean
    fun oAuth2UserService(): ReactiveOAuth2UserService<OAuth2UserRequest, OAuth2User> {
        val delegate = DefaultReactiveOAuth2UserService()
        return CustomReactiveOAuth2UserService(delegate)
    }

    @Bean
    fun oAuth2AuthenticationSuccessHandler(): ServerAuthenticationSuccessHandler =
        OAuth2AuthenticationSuccessHandler(jwtService)

    @Bean
    fun oAuth2AuthenticationFailureHandler(): ServerAuthenticationFailureHandler =
        OAuth2AuthenticationFailureHandler()

    @Bean
    fun securityContextRepository(): ServerSecurityContextRepository =
        JwtSecurityContextRepository(jwtService, userDetailsService)

    @Bean
    fun auditLoggingFilter(): WebFilter = AuditLoggingFilter()

    @Bean
    fun cookieClearingLogoutHandler(): ServerLogoutHandler =
        CookieClearingLogoutHandler("JSESSIONID", "jwt-token")
}
```

## Token Validation Configuration

### Custom Reactive JWT Validator

```kotlin
@com.profiletailors.common.domain.Service
class CustomJwtValidator(
    private val jwtParser: JwtParser,
    private val blacklistedTokenService: BlacklistedTokenService,
    private val jwtProperties: JwtProperties,
    @Value("\${jwt.secret}") private val secret: String,
) : JwtValidator {
    override fun validate(token: String): ValidationResult = try {
        if (jwtProperties.blacklist.enabled) {
            val jti = extractClaim(token, "jti")
            if (jti != null && blacklistedTokenService.isBlacklisted(jti)) {
                return ValidationResult.error("Token is blacklisted")
            }
        }

        val claims = jwtParser.parseSignedClaims(token).payload
        validateCustomClaims(claims)
    } catch (e: ExpiredJwtException) {
        ValidationResult.error("Token has expired")
    } catch (e: UnsupportedJwtException) {
        ValidationResult.error("Token is unsupported")
    } catch (e: MalformedJwtException) {
        ValidationResult.error("Token is malformed")
    } catch (e: SecurityException) {
        ValidationResult.error("Token signature validation failed")
    } catch (e: IllegalArgumentException) {
        ValidationResult.error("Token is invalid")
    } catch (e: JwtException) {
        ValidationResult.error("JWT processing failed: ${e.message}")
    }

    private fun validateCustomClaims(claims: Claims): ValidationResult {
        if (jwtProperties.validateIssuer && claims.issuer != jwtProperties.issuer) {
            return ValidationResult.error("Invalid issuer")
        }

        if (jwtProperties.validateAudience) {
            val audiences = claims.audience
            if (audiences.isNullOrEmpty() || !audiences.contains(jwtProperties.audience)) {
                return ValidationResult.error("Invalid audience")
            }
        }

        val tokenType = claims.get("type", String::class.java)
        if (tokenType == null || tokenType != "access") {
            return ValidationResult.error("Invalid token type")
        }

        return ValidationResult.success()
    }

    private fun extractClaim(token: String, claimName: String): String? = try {
        val claims = Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .payload
        claims.get(claimName, String::class.java)
    } catch (_: JwtException) {
        null
    }

    private fun getSigningKey(): SecretKey {
        val keyBytes = Decoders.BASE64.decode(
            Base64.getEncoder().encodeToString(secret.toByteArray()),
        )
        return Keys.hmacShaKeyFor(keyBytes)
    }
}

data class ValidationResult(
    val valid: Boolean,
    val errorMessage: String?,
) {
    companion object {
        fun success() = ValidationResult(true, null)
        fun error(message: String) = ValidationResult(false, message)
    }
}
```

## Key Management

### Asymmetric Key Configuration

```kotlin
@Configuration
@ConditionalOnProperty(name = "jwt.algorithm", havingValue = "RSA")
class AsymmetricJwtConfig(
    @Value("\${jwt.public-key}") private val publicKeyString: String,
    @Value("\${jwt.private-key}") private val privateKeyString: String,
) {
    @Bean
    fun publicKey(): RSAPublicKey = KeyFactory.getInstance("RSA")
        .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyString))) as RSAPublicKey

    @Bean
    fun privateKey(): RSAPrivateKey = KeyFactory.getInstance("RSA")
        .generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyString))) as RSAPrivateKey

    @Bean
    fun jwtDecoder(publicKey: RSAPublicKey): JwtDecoder = NimbusReactiveJwtDecoder
        .withPublicKey(publicKey)
        .signatureAlgorithm(SignatureAlgorithm.RS256)
        .build()

    @Bean
    fun jwtEncoder(privateKey: RSAPrivateKey): JwtEncoder {
        val rsaSigner = RSASSASigner(privateKey)
        return NimbusJwtEncoder(ImmutableJWEHeader(JWSAlgorithm.RS256), rsaSigner)
    }
}
```

### Coroutine-Based Key Rotation

```kotlin
@com.profiletailors.common.domain.Service
class KeyRotationService(
    private val keyRepository: KeyRepository,
) {
    private val activeKeys: MutableMap<String, KeyPair> = ConcurrentHashMap()

    @PostConstruct
    fun initialize() {
        runBlocking {
            loadActiveKeys()
            scheduleKeyRotation()
        }
    }

    @Scheduled(cron = "\${jwt.key-rotation.cron:0 0 0 1 * ?}")
    suspend fun rotateKeys() {
        try {
            val newKeyPair = generateKeyPair()

            val newKey = JwtKey(
                keyId = UUID.randomUUID().toString(),
                publicKey = Base64.getEncoder().encodeToString(newKeyPair.public.encoded),
                privateKey = Base64.getEncoder().encodeToString(newKeyPair.private.encoded),
                algorithm = "RS256",
                createdAt = Instant.now(),
                isActive = true,
            )

            keyRepository.deactivateAllKeys()
            keyRepository.save(newKey)
            loadActiveKeys()
        } catch (e: Exception) {
            logger.error("JWT key rotation failed", e)
        }
    }

    fun getCurrentKeyPair(): KeyPair = activeKeys.values.first()

    fun getKeyPair(keyId: String): KeyPair? = activeKeys[keyId]

    private suspend fun loadActiveKeys() {
        val activeJwtKeys = keyRepository.findByIsActiveTrue().toList()

        activeKeys.clear()

        activeJwtKeys.forEach { key ->
            try {
                val keyPair = restoreKeyPair(key)
                activeKeys[key.keyId] = keyPair
            } catch (e: Exception) {
                logger.error("Failed to restore key pair for keyId: ${key.keyId}", e)
            }
        }

        if (activeKeys.isEmpty()) {
            logger.warn("No active keys found, generating new key pair")
            rotateKeys()
        }
    }

    private fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        return keyPairGenerator.generateKeyPair()
    }

    private fun restoreKeyPair(jwtKey: JwtKey): KeyPair {
        val keyFactory = KeyFactory.getInstance("RSA")

        val publicKeyBytes = Base64.getDecoder().decode(jwtKey.publicKey)
        val publicKeySpec = X509EncodedKeySpec(publicKeyBytes)
        val publicKey = keyFactory.generatePublic(publicKeySpec) as RSAPublicKey

        val privateKeyBytes = Base64.getDecoder().decode(jwtKey.privateKey)
        val privateKeySpec = PKCS8EncodedKeySpec(privateKeyBytes)
        val privateKey = keyFactory.generatePrivate(privateKeySpec) as RSAPrivateKey

        return KeyPair(publicKey, privateKey)
    }

    private fun scheduleKeyRotation() {
        scheduler.scheduleAtFixedRate(::rotateKeys, 1, 30, TimeUnit.DAYS)
    }

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(KeyRotationService::class.java)
        private val scheduler = Executors.newScheduledThreadPool(1)
    }
}
```

## CORS and CSRF Configuration

### Reactive CORS Configuration

```kotlin
@Configuration
class CorsConfig {
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOriginPatterns = listOf(
                "http://localhost:*",
                "https://*.profiletailors.com",
            )

            allowedMethods = listOf(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD",
            )

            allowedHeaders = listOf(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers",
            )

            exposedHeaders = listOf(
                "X-Total-Count",
                "X-Page-Count",
                "X-Current-Page",
            )

            allowCredentials = true
            maxAge = 3600L
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/api/**", configuration)
            registerCorsConfiguration("/oauth2/**", configuration)
        }
    }
}
```

### Reactive CSRF Configuration

```kotlin
@Configuration
class CsrfConfig(
    private val environment: Environment,
) {
    @Bean
    fun csrfTokenRepository(): ServerCsrfTokenRepository =
        CookieServerCsrfTokenRepository.withHttpOnlyFalse().apply {
            setCookieName("XSRF-TOKEN")
            setHeaderName("X-XSRF-TOKEN")
            setCookieHttpOnly(false)
            setCookiePath("/")

            if (isProductionEnvironment()) {
                setCookieSecure(true)
            }
        }

    @Bean
    fun csrfTokenRequestHandler(): ServerCsrfTokenRequestAttributeHandler =
        ServerCsrfTokenRequestAttributeHandler()

    @Bean
    fun spaCsrfTokenRequestHandler(): SpaCsrfTokenRequestHandler = SpaCsrfTokenRequestHandler()

    private fun isProductionEnvironment(): Boolean =
        environment.activeProfiles.contains("prod")
}

class SpaCsrfTokenRequestHandler : ServerCsrfTokenRequestAttributeHandler() {
    override fun handle(
        exchange: ServerWebExchange,
        csrfToken: DeferredCsrfToken,
    ): Mono<CsrfToken> = csrfToken.get().doOnNext { token ->
        val response = exchange.response
        response.headers.add("X-CSRF-TOKEN", token.token)
        response.headers.add("Access-Control-Expose-Headers", "X-CSRF-TOKEN")
    }
}
```

This configuration reference covers the reactive JWT security wiring used by the SMP
backend. The servlet-stack variants shown in the wider Spring Security 6.x documentation
are no longer applicable; the historic migration notes in `migration-spring-security-6x.md`
list the servlet patterns only as anti-patterns for reference.
