# JWT Security Configuration Reference

This document provides comprehensive configuration options for JWT security in Spring Boot
applications using JJWT library and Spring Security 6.x.

## Table of Contents

1. [Application Properties](#application-properties)
2. [JWT Configuration Beans](#jwt-configuration-beans)
3. [Security Filter Chain Options](#security-filter-chain-options)
4. [Token Validation Configuration](#token-validation-configuration)
5. [Key Management](#key-management)
6. [CORS and CSRF Configuration](#cors-and-csrf-configuration)
7. [Session Management](#session-management)
8. [Error Handling Configuration](#error-handling-configuration)
9. [Performance Configuration](#performance-configuration)
10. [Monitoring and Audit Configuration](#monitoring-and-audit-configuration)

## Application Properties

### Complete JWT Configuration (application.yml)

```yaml
# JWT Configuration
jwt:
  # Token settings
  secret: ${JWT_SECRET:my-very-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256}
  access-token-expiration: 900000          # 15 minutes in milliseconds
  refresh-token-expiration: 604800000     # 7 days in milliseconds
  issuer: ${JWT_ISSUER:spring-boot-jwt-app}
  audience: ${JWT_AUDIENCE:spring-boot-client}

  # Cookie settings
  cookie-name: jwt-token
  cookie-secure: ${JWT_COOKIE_SECURE:false} # Set to true in production with HTTPS
  cookie-http-only: true
  cookie-same-site: lax                   # strict, lax, or none
  cookie-domain: ${JWT_COOKIE_DOMAIN:}    # Optional domain
  cookie-path: /
  cookie-max-age: 86400                   # 24 hours

  # Token validation
  validate-issuer: true
  validate-audience: false
  validate-expiration: true
  clock-skew-seconds: 60                  # Allow 60 seconds clock skew

  # Refresh token settings
  refresh-token-limit: 5                  # Max active refresh tokens per user
  refresh-token-rotation-enabled: true
  refresh-token-cleanup-enabled: true
  refresh-token-cleanup-cron: "0 0 2 * * ?" # Daily at 2 AM

  # Security settings
  blacklist-enabled: true
  blacklist-cleanup-enabled: true
  blacklist-cleanup-cron: "0 0 3 * * ?"    # Daily at 3 AM

# Spring Security Configuration
spring:
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

  # Session configuration (if needed)
  session:
    store-type: none                      # Use stateless sessions
    timeout: 30m                          # Session timeout
    jdbc:
      initialize-schema: always

  # CORS configuration
  web:
    cors:
      allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:8080}
      allowed-methods: GET,POST,PUT,DELETE,OPTIONS
      allowed-headers: "*"
      allow-credentials: true
      max-age: 3600

# Logging configuration
logging:
  level:
    org.springframework.security: DEBUG
    io.jsonwebtoken: DEBUG
    com.example.security: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

# Management endpoints for monitoring
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
    @Value("\${jwt.secret}")
    private val secret: String,

    @Value("\${jwt.access-token-expiration}")
    private val accessTokenExpiration: Long,

    @Value("\${jwt.refresh-token-expiration}")
    private val refreshTokenExpiration: Long,

    @Value("\${jwt.issuer}")
    private val issuer: String,

    @Value("\${jwt.audience:}")
    private val audience: String,

    @Value("\${jwt.validate-issuer:true}")
    private val validateIssuer: Boolean,

    @Value("\${jwt.validate-audience:false}")
    private val validateAudience: Boolean,

    @Value("\${jwt.clock-skew-seconds:60}")
    private val clockSkewSeconds: Int
) {

    @Bean
    fun jwtService(refreshTokenService: RefreshTokenService): JwtService {
        return JwtService(
            secret,
            accessTokenExpiration,
            refreshTokenExpiration,
            issuer,
            audience,
            validateIssuer,
            validateAudience,
            clockSkewSeconds,
            refreshTokenService
        )
    }

    @Bean
    fun jwtParser(): JwtParser {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .requireIssuer(issuer)
            .setAllowedClockSkewSeconds(clockSkewSeconds)
            .build()
    }

    @Bean
    fun getSigningKey(): SecretKey {
        val keyBytes = Decoders.BASE64.decode(
            Base64.getEncoder().encodeToString(secret.toByteArray())
        )
        return Keys.hmacShaKeyFor(keyBytes)
    }

    @Bean
    fun claimsSetExtractor(): ClaimsSetExtractor {
        return DefaultClaimsSetExtractor(
            issuer,
            audience,
            Duration.ofMillis(accessTokenExpiration)
        )
    }
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
            .setAllowedClockSkewSeconds(jwtProperties.clockSkewSeconds)

        if (jwtProperties.validateIssuer) {
            parser.requireIssuer(jwtProperties.issuer)
        }

        if (jwtProperties.validateAudience && jwtProperties.audience.isNotBlank()) {
            parser.requireAudience(jwtProperties.audience)
        }

        return parser.build()
    }

    @Bean
    fun jwtValidator(jwtParser: JwtParser): JwtValidator {
        return DefaultJwtValidator(jwtParser)
    }
}
```

### Configuration Properties Class

```kotlin
@ConfigurationProperties(prefix = "jwt")
@Validated
data class JwtProperties(
    /**
     * JWT secret key for HMAC signing
     */
    @field:NotBlank
    @field:Size(min = 32, message = "JWT secret must be at least 32 characters")
    var secret: String = "",

    /**
     * Access token expiration in milliseconds
     */
    @field:Min(60000) // Minimum 1 minute
    var accessTokenExpiration: Long = 900000, // 15 minutes

    /**
     * Refresh token expiration in milliseconds
     */
    @field:Min(3600000) // Minimum 1 hour
    var refreshTokenExpiration: Long = 604800000, // 7 days

    /**
     * JWT issuer
     */
    @field:NotBlank
    var issuer: String = "",

    /**
     * JWT audience
     */
    var audience: String = "",

    /**
     * Validate issuer claim
     */
    var validateIssuer: Boolean = true,

    /**
     * Validate audience claim
     */
    var validateAudience: Boolean = false,

    /**
     * Clock skew in seconds for token validation
     */
    @field:Min(0)
    var clockSkewSeconds: Int = 60,

    /**
     * Cookie configuration
     */
    var cookie: CookieProperties = CookieProperties(),

    /**
     * Refresh token configuration
     */
    var refreshToken: RefreshTokenProperties = RefreshTokenProperties(),

    /**
     * Blacklist configuration
     */
    var blacklist: BlacklistProperties = BlacklistProperties()
) {

    data class CookieProperties(
        var name: String = "jwt-token",
        var secure: Boolean = false,
        var httpOnly: Boolean = true,
        var sameSite: String = "lax",
        var domain: String? = null,
        var path: String = "/",
        var maxAge: Int = 86400
    )

    data class RefreshTokenProperties(
        var limit: Int = 5,
        var rotationEnabled: Boolean = true,
        var cleanupEnabled: Boolean = true,
        var cleanupCron: String = "0 0 2 * * ?"
    )

    data class BlacklistProperties(
        var enabled: Boolean = true,
        var cleanupEnabled: Boolean = true,
        var cleanupCron: String = "0 0 3 * * ?"
    )
}
```

## Security Filter Chain Options

### Advanced Security Configuration

```kotlin
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class AdvancedSecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val authenticationProvider: AuthenticationProvider,
    private val authenticationEntryPoint: JwtAuthenticationEntryPoint,
    private val accessDeniedHandler: CustomAccessDeniedHandler,
    private val corsConfigurationSource: SecurityCorsConfigurationSource,
    private val logoutHandler: LogoutHandler,
    private val securityContextRepository: SecurityContextRepository
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .cors { it.configurationSource(corsConfigurationSource) }
            .csrf { csrf ->
                csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .ignoringRequestMatchers("/api/auth/**", "/api/public/**")
                    .sessionAuthenticationStrategy(NullSessionAuthenticationStrategy())
            }
            .headers { headers ->
                headers
                    .contentSecurityPolicy { csp ->
                        csp.policyDirectives("default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'")
                    }
                    .frameOptions { it.deny() }
                    .httpStrictTransportSecurity { hsts ->
                        hsts
                            .maxAgeInSeconds(31536000)
                            .includeSubdomains(true)
                            .preload(true)
                    }
                    .permissionsPolicy { permissions ->
                        permissions.policy("camera=(), microphone=(), geolocation=()")
                    }
                    .referrerPolicy { it.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN) }
            }
            .sessionManagement { session ->
                session
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .sessionAuthenticationStrategy(sessionAuthenticationStrategy())
                    .maximumSessions(10)
                    .maxSessionsPreventsLogin(false)
                    .sessionRegistry(sessionRegistry())
            }
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
            }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/api/auth/**", "/api/public/**", "/health", "/actuator/health").permitAll()
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/users/**").hasAuthority("USER_READ")
                    .requestMatchers(HttpMethod.POST, "/api/users/**").hasAuthority("USER_WRITE")
                    .requestMatchers(HttpMethod.PUT, "/api/users/**").hasAuthority("USER_WRITE")
                    .requestMatchers(HttpMethod.DELETE, "/api/users/**").hasAuthority("USER_DELETE")
                    .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                    .requestMatchers("/actuator/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
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
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(securityContextFilter(), UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(auditLoggingFilter(), UsernamePasswordAuthenticationFilter::class.java)
            .logout { logout ->
                logout
                    .logoutUrl("/api/auth/logout")
                    .addLogoutHandler(securityContextLogoutHandler())
                    .addLogoutHandler(logoutHandler)
                    .addLogoutHandler(cookieClearingLogoutHandler())
                    .logoutSuccessHandler { _, response, _ ->
                        response.status = HttpStatus.NO_CONTENT.value()
                    }
                    .deleteCookies("JSESSIONID", "jwt-token")
                    .clearAuthentication(true)
                    .invalidateHttpSession(true)
            }
            .securityContext { it.securityContextRepository(securityContextRepository) }
            .build()
    }

    @Bean
    fun jwtDecoder(): JwtDecoder {
        return NimbusJwtDecoder.withSecretKey(getSigningKey())
            .signatureAlgorithm(SignatureAlgorithm.HS256)
            .build()
    }

    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        val authoritiesConverter = JwtGrantedAuthoritiesConverter().apply {
            setAuthorityPrefix("ROLE_")
            setAuthoritiesClaimName("authorities")
        }

        return JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter(authoritiesConverter)
            setPrincipalClaimName("sub")
            setPrincipalAttributeName("sub")
        }
    }

    @Bean
    fun oAuth2UserService(): OAuth2UserService<OAuth2UserRequest, OAuth2User> {
        val delegate = DefaultOAuth2UserService()
        return CustomOAuth2UserService(delegate)
    }

    @Bean
    fun oAuth2AuthenticationSuccessHandler(): AuthenticationSuccessHandler {
        return OAuth2AuthenticationSuccessHandler(jwtService)
    }

    @Bean
    fun oAuth2AuthenticationFailureHandler(): AuthenticationFailureHandler {
        return OAuth2AuthenticationFailureHandler()
    }

    @Bean
    fun sessionAuthenticationStrategy(): SessionAuthenticationStrategy {
        return CompositeSessionAuthenticationStrategy(
            listOf(
                RegisterSessionAuthenticationStrategy(sessionRegistry()),
                CsrfAuthenticationStrategy()
            )
        )
    }

    @Bean
    fun sessionRegistry(): SessionRegistry = SessionRegistryImpl()

    @Bean
    fun securityContextRepository(): SecurityContextRepository {
        return JwtSecurityContextRepository(jwtService, userDetailsService)
    }

    @Bean
    fun securityContextFilter(): Filter {
        return SecurityContextPersistenceFilter(securityContextRepository())
    }

    @Bean
    fun auditLoggingFilter(): Filter = AuditLoggingFilter()

    @Bean
    fun securityContextLogoutHandler(): LogoutHandler = SecurityContextLogoutHandler()

    @Bean
    fun cookieClearingLogoutHandler(): LogoutHandler {
        return CookieClearingLogoutHandler("JSESSIONID", "jwt-token")
    }
}
```

## Token Validation Configuration

### Custom JWT Validator

```kotlin
@Component
class CustomJwtValidator(
    private val jwtParser: JwtParser,
    private val blacklistedTokenService: BlacklistedTokenService,
    private val jwtProperties: JwtProperties,
    @Value("\${jwt.secret}")
    private val secret: String
) : JwtValidator {

    override fun validate(token: String): ValidationResult {
        return try {
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

    private fun extractClaim(token: String, claimName: String): String? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .payload
            claims.get(claimName, String::class.java)
        } catch (e: JwtException) {
            null
        }
    }

    private fun getSigningKey(): SecretKey {
        val keyBytes = Decoders.BASE64.decode(
            Base64.getEncoder().encodeToString(secret.toByteArray())
        )
        return Keys.hmacShaKeyFor(keyBytes)
    }
}

data class ValidationResult(
    val valid: Boolean,
    val errorMessage: String?
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
    @Value("\${jwt.public-key}")
    private val publicKeyString: String,

    @Value("\${jwt.private-key}")
    private val privateKeyString: String
) {

    @Bean
    fun publicKey(): RSAPublicKey {
        return KeyFactory.getInstance("RSA")
            .generatePublic(
                X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyString))
            ) as RSAPublicKey
    }

    @Bean
    fun privateKey(): RSAPrivateKey {
        return KeyFactory.getInstance("RSA")
            .generatePrivate(
                PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyString))
            ) as RSAPrivateKey
    }

    @Bean
    fun jwtDecoder(publicKey: RSAPublicKey): JwtDecoder {
        return NimbusJwtDecoder.withPublicKey(publicKey)
            .signatureAlgorithm(SignatureAlgorithm.RS256)
            .build()
    }

    @Bean
    fun jwtEncoder(privateKey: RSAPrivateKey): JwtEncoder {
        val rsaSigner = RSASSASigner(privateKey)
        return NimbusJwtEncoder(
            ImmutableJWEHeader(JWSAlgorithm.RS256),
            rsaSigner
        )
    }
}
```

### Key Rotation Support

```kotlin
@Service
@Slf4j
class KeyRotationService(
    private val keyRepository: KeyRepository
) {

    private val activeKeys: MutableMap<String, KeyPair> = ConcurrentHashMap()
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)

    @PostConstruct
    fun initialize() {
        loadActiveKeys()
        scheduleKeyRotation()
    }

    @Scheduled(cron = "\${jwt.key-rotation.cron:0 0 0 1 * ?}")
    fun rotateKeys() {
        try {
            log.info("Starting JWT key rotation")

            val newKeyPair = generateKeyPair()

            val newKey = JwtKey(
                keyId = UUID.randomUUID().toString(),
                publicKey = Base64.getEncoder().encodeToString(newKeyPair.public.encoded),
                privateKey = Base64.getEncoder().encodeToString(newKeyPair.private.encoded),
                algorithm = "RS256",
                createdAt = Instant.now(),
                isActive = true
            )

            keyRepository.deactivateAllKeys()
            keyRepository.save(newKey)
            loadActiveKeys()

            log.info("JWT key rotation completed successfully")

        } catch (e: Exception) {
            log.error("JWT key rotation failed", e)
        }
    }

    fun getCurrentKeyPair(): KeyPair {
        return activeKeys.values.first()
    }

    fun getKeyPair(keyId: String): KeyPair? {
        return activeKeys[keyId]
    }

    private fun loadActiveKeys() {
        val activeJwtKeys = keyRepository.findByIsActiveTrue()

        activeKeys.clear()

        activeJwtKeys.forEach { key ->
            try {
                val keyPair = restoreKeyPair(key)
                activeKeys[key.keyId] = keyPair
            } catch (e: Exception) {
                log.error("Failed to restore key pair for keyId: ${key.keyId}", e)
            }
        }

        if (activeKeys.isEmpty()) {
            log.warn("No active keys found, generating new key pair")
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
        scheduler.scheduleAtFixedRate(
            ::rotateKeys,
            1,
            30,
            TimeUnit.DAYS
        )
    }
}
```

## CORS and CSRF Configuration

### Advanced CORS Configuration

```kotlin
@Configuration
class CorsConfig {

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOriginPatterns = listOf(
                "http://localhost:*",
                "https://*.yourdomain.com"
            )

            allowedMethods = listOf(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"
            )

            allowedHeaders = listOf(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers"
            )

            exposedHeaders = listOf(
                "X-Total-Count",
                "X-Page-Count",
                "X-Current-Page"
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

### Custom CSRF Configuration

```kotlin
@Configuration
class CsrfConfig(
    private val environment: Environment
) {

    @Bean
    fun csrfTokenRepository(): CsrfTokenRepository {
        return CookieCsrfTokenRepository.withHttpOnlyFalse().apply {
            setCookieName("XSRF-TOKEN")
            setHeaderName("X-XSRF-TOKEN")
            setCookieHttpOnly(false)
            setCookiePath("/")

            if (isProductionEnvironment()) {
                setCookieSecure(true)
            }
        }
    }

    @Bean
    fun csrfTokenRequestHandler(): CsrfTokenRequestHandler {
        return CsrfTokenRequestAttributeHandler()
    }

    @Bean
    fun spaCsrfTokenRequestHandler(): CsrfTokenRequestHandler {
        return SpaCsrfTokenRequestHandler()
    }

    private fun isProductionEnvironment(): Boolean {
        return environment.activeProfiles.contains("prod")
    }
}

class SpaCsrfTokenRequestHandler : CsrfTokenRequestAttributeHandler() {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        csrfToken: Supplier<CsrfToken>
    ) {
        val csrfTokenValue = csrfToken.get().token
        response.setHeader("X-CSRF-TOKEN", csrfTokenValue)
        response.setHeader("Access-Control-Expose-Headers", "X-CSRF-TOKEN")
    }
}
```

This configuration reference provides comprehensive options for setting up JWT security in Spring
Boot applications with various security features, key management strategies, and advanced
configurations for production environments.