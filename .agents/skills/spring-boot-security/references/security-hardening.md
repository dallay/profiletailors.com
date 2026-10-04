# Security Hardening Checklist

This document collects the reactive variants of security-hardening patterns used in
the SMP backend. All examples rely on the WebFlux security stack (`ServerHttpSecurity`,
`SecurityWebFilterChain`, `WebFilter`, `Reactive*` types,
`suspend fun`). The servlet-stack patterns appear only in
`migration-spring-security-6x.md` for historical reference.

## Secure Configuration

### Production Security Headers

```kotlin
@Configuration
class SecurityHeadersConfig {
    @Bean
    fun securityHeadersWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain = http
        .headers { headers ->
            headers
                .contentTypeOptions { it.and() }
                .xssProtection { xss ->
                    xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK)
                }
                .hsts { hsts ->
                    hsts
                        .maxAge(Duration.ofDays(365))
                        .includeSubdomains(true)
                        .preload(true)
                }
                .frameOptions { it.deny() }
                .contentSecurityPolicy { csp ->
                    csp.policyDirectives(
                        "default-src 'self'; " +
                            "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                            "style-src 'self' 'unsafe-inline'; " +
                            "img-src 'self' data: https:; " +
                            "font-src 'self'; " +
                            "connect-src 'self'; " +
                            "frame-ancestors 'none'; " +
                            "base-uri 'self'; " +
                            "form-action 'self'; " +
                            "upgrade-insecure-requests;",
                    )
                }
                .referrerPolicy { it.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN) }
                .permissionsPolicy { permissions ->
                    permissions.policy(
                        "geolocation=(), " +
                            "microphone=(), " +
                            "camera=(), " +
                            "payment=(), " +
                            "usb=(), " +
                            "magnetometer=(), " +
                            "gyroscope=(), " +
                            "accelerometer=()",
                    )
                }
        }
        .build()
}
```

### Enhanced Password Security

```kotlin
@com.profiletailors.common.domain.Service
class SecurePasswordService(
    private val passwordEncoder: PasswordEncoder,
    private val passwordHistoryRepository: PasswordHistoryRepository,
    private val passwordPolicy: PasswordPolicy,
) {
    fun encodePassword(rawPassword: String): String = passwordEncoder.encode(rawPassword)

    suspend fun validatePassword(password: String, user: UserAccount) {
        if (!meetsPasswordPolicy(password)) {
            throw PasswordPolicyViolationException(getPasswordPolicyViolations(password))
        }
        if (isPasswordReused(password, user)) {
            throw PasswordReusedException("Password has been used before")
        }
        if (isBreachedPassword(password)) {
            throw BreachedPasswordException("Password has been exposed in data breaches")
        }
    }

    private fun meetsPasswordPolicy(password: String): Boolean =
        password.length >= passwordPolicy.minLength &&
            password.contains(Regex("[A-Z]")) &&
            password.contains(Regex("[a-z]")) &&
            password.contains(Regex("\\d")) &&
            password.contains(Regex("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]"))

    suspend fun isBreachedPasswordAsync(password: String): Boolean {
        val sha1Hash = MessageDigest.getInstance("SHA-1")
            .digest(password.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val prefix = sha1Hash.substring(0, 5)
        val suffix = sha1Hash.substring(5)

        return try {
            val response = webClient
                .get()
                .uri("https://api.pwnedpasswords.com/range/$prefix")
                .retrieve()
                .bodyToMono(String::class.java)
                .awaitSingle()

            response.lineSequence().any { it.startsWith(suffix.uppercase()) }
        } catch (e: Exception) {
            logger.warn("Failed to check breached password API", e)
            false
        }
    }

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(SecurePasswordService::class.java)
    }
}

@Configuration
class PasswordConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = Argon2PasswordEncoder(
        16, 32, 1, 65536, 3,
    )
}
```

## Advanced Attack Prevention

### Rate Limiting and Brute Force Protection

```kotlin
@Component
class BruteForceProtectionService(
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val loginAttemptsCache: Cache<String, Int> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(15))
        .build { 0 }
    private val lockoutCache: Cache<String, Long> = Caffeine.newBuilder()
        .expireAfterWrite(lockoutDuration)
        .build { 0L }

    private val maxAttempts: Int = 5
    private val lockoutDuration: Duration = Duration.ofMinutes(15)

    fun recordFailedAttempt(identifier: String) {
        val attempts = loginAttemptsCache.asMap().merge(identifier, 1, Integer::sum) ?: 0
        if (attempts >= maxAttempts) {
            lockout(identifier)
            publishSecurityEvent("ACCOUNT_LOCKED", identifier)
        }
    }

    fun recordSuccessfulAttempt(identifier: String) {
        loginAttemptsCache.invalidate(identifier)
        lockoutCache.invalidate(identifier)
    }

    fun isLockedOut(identifier: String): Boolean {
        val lockTime: Long? = lockoutCache.getIfPresent(identifier)
        return lockTime != null && lockTime > 0
    }

    private fun lockout(identifier: String) {
        lockoutCache.put(identifier, System.currentTimeMillis())
    }

    @EventListener
    fun handleAuthenticationFailure(event: AuthenticationFailureBadCredentialsEvent) {
        val username = event.authentication.name
        recordFailedAttempt(username)
        val clientIp = getClientIpAddress()
        recordFailedAttempt(clientIp)
    }
}

@RestController
@RequestMapping("/api/auth")
class SecureAuthController(
    private val bruteForceProtection: BruteForceProtectionService,
    private val recaptchaService: RecaptchaService,
) {
    @PostMapping("/login")
    @RateLimited(requests = 5, window = "PT1M")
    suspend fun login(
        @RequestBody request: LoginRequest,
        exchange: ServerWebExchange,
    ): ResponseEntity<Any> {
        val clientIp = exchange.extractClientIp()

        if (bruteForceProtection.isLockedOut(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse("Too many failed attempts. Please try again later."))
        }
        if (bruteForceProtection.isLockedOut(request.username)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse("Account temporarily locked due to failed attempts."))
        }
        if (shouldRequireRecaptcha(request.username, clientIp) &&
            !recaptchaService.verifyRecaptcha(request.recaptchaToken, clientIp)
        ) {
            return ResponseEntity.badRequest().body(ErrorResponse("Invalid reCAPTCHA"))
        }
        return performAuthentication(request)
    }
}
```

### CSRF Protection with State Management

```kotlin
@Configuration
class CsrfConfig {
    @Bean
    fun customCsrfTokenRepository(): ServerCsrfTokenRepository =
        HttpSessionServerCsrfTokenRepository().apply {
            setHeaderName("X-CSRF-TOKEN")
            setParameterName("_csrf")
        }

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
        http.csrf { csrf ->
            csrf
                .csrfTokenRepository(customCsrfTokenRepository())
                .ignoringRequestMatchers("/api/auth/**")
                .csrfTokenRequestHandler(ServerCsrfTokenRequestAttributeHandler())
        }
            .addFilterAfter(CsrfCookieFilter(), SecurityWebFiltersOrder.HTTP_BASIC)
            .build()
}

@Component
class CsrfCookieFilter : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val csrfToken = exchange.attributes[CsrfToken::class.java.name] as? CsrfToken ?: return chain.filter(exchange)

        val response = exchange.response
        response.cookies.add(
            "XSRF-TOKEN",
            ResponseCookie.from("XSRF-TOKEN", csrfToken.token)
                .path("/")
                .httpOnly(false)
                .secure(true)
                .maxAge(-1)
                .build(),
        )
        return chain.filter(exchange)
    }
}
```

## Input Validation and Sanitization

### Reactive Request Validation Filter

```kotlin
@Component
class SecurityValidationWebFilter(
    private val inputSanitizer: InputSanitizer,
    private val xssProtection: XssProtectionService,
) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        if (!validateRequest(exchange)) {
            exchange.response.statusCode = HttpStatus.BAD_REQUEST
            exchange.response.setComplete()
            return Mono.empty()
        }
        return chain.filter(exchange)
    }

    private fun validateRequest(exchange: ServerWebExchange): Boolean = try {
        exchange.request.queryParams.forEach { (key, values) ->
            if (xssProtection.containsXss(key) || values.any(xssProtection::containsXss)) {
                throw SecurityException("XSS detected in parameters")
            }
        }

        exchange.request.headers.headers.forEach { header ->
            if (isSuspiciousHeader(header.key, header.value.firstOrNull().orEmpty())) {
                throw SecurityException("Suspicious header detected")
            }
        }

        true
    } catch (e: Exception) {
        logger.warn("Request validation failed", e)
        false
    }

    private fun isSuspiciousHeader(headerName: String, headerValue: String): Boolean {
        val suspiciousPatterns = Regex("(?i)(script|javascript|vbscript|onload|onerror|onclick)")
        return suspiciousPatterns.containsMatchIn(headerName) ||
            suspiciousPatterns.containsMatchIn(headerValue)
    }

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(SecurityValidationWebFilter::class.java)
    }
}

@Component
class XssProtectionService {
    private val xssPatterns = listOf(
        Regex("<script[^>]*>.*?</script>", RegexOption.IGNORE_CASE),
        Regex("javascript:", RegexOption.IGNORE_CASE),
        Regex("vbscript:", RegexOption.IGNORE_CASE),
        Regex("onload(.*?)=", RegexOption.IGNORE_CASE),
        Regex("onerror(.*?)=", RegexOption.IGNORE_CASE),
        Regex("onclick(.*?)=", RegexOption.IGNORE_CASE),
        Regex("<img[^>]*src[^=]*=[\"']?javascript:", RegexOption.IGNORE_CASE),
    )

    fun containsXss(input: String?): Boolean {
        if (input.isNullOrEmpty()) return false
        return xssPatterns.any { it.containsMatchIn(input) }
    }

    fun sanitize(input: String?): String? {
        if (input == null) return null
        var sanitized = HtmlUtils.htmlEscape(input)
        sanitized = sanitized.replace(Regex("<script[^>]*>.*?</script>"), "")
        sanitized = sanitized.replace("javascript:", "")
        sanitized = sanitized.replace("vbscript:", "")
        return sanitized
    }
}
```

### Parameterized R2DBC Queries (SQL Injection Prevention)

```kotlin
@Component
class SecureUserRepository(
    private val databaseClient: DatabaseClient,
) {
    suspend fun findByEmailSafe(email: String): UserAccount? =
        databaseClient.sql("SELECT * FROM users WHERE email = :email")
            .bind("email", email)
            .map { row -> row.toUserAccount() }
            .one()
            .awaitOneOrNull()

    suspend fun searchUsersSecure(searchTerm: String): List<UserAccount> {
        require(isValidSearchTerm(searchTerm)) { "Invalid search term" }

        val escaped = escapeSql(searchTerm)
        return databaseClient.sql(
            """
            SELECT * FROM users
            WHERE email LIKE :term
               OR first_name LIKE :term
               OR last_name LIKE :term
            """.trimIndent(),
        )
            .bind("term", "%$escaped%")
            .map { row -> row.toUserAccount() }
            .all()
            .toList()
            .awaitSingle()
    }

    private fun isValidSearchTerm(term: String): Boolean {
        val dangerous = listOf("'", "\"", ";", "--", "/*", "*/", "xp_", "sp_")
        val upper = term.uppercase()
        return dangerous.none { upper.contains(it) }
    }

    private fun escapeSql(input: String): String = input.replace("'", "''")
}
```

## Secure Key Management

### Coroutine-Based Key Rotation Service

```kotlin
@com.profiletailors.common.domain.Service
class KeyRotationService(
    private val keyStore: JwtKeyStore,
    private val jwtEncoder: JwtEncoder,
    private val eventPublisher: ApplicationEventPublisher,
    private val refreshTokenService: RefreshTokenService,
) {
    @Value("\${jwt.rotation.enabled:true}")
    private val rotationEnabled: Boolean = true

    @Scheduled(cron = "\${jwt.rotation.schedule:0 0 2 * * ?}")
    suspend fun rotateKeys() {
        if (!rotationEnabled) {
            logger.info("Key rotation is disabled")
            return
        }

        try {
            logger.info("Starting JWT key rotation")
            val newKeyPair = generateNewKeyPair()
            val newKeyId = generateKeyId()
            keyStore.addKey(newKeyId, newKeyPair)
            scheduleKeyPromotion(newKeyId)

            val oldKeyId = keyStore.currentKeyId
            if (oldKeyId != null) scheduleKeyRetirement(oldKeyId)

            eventPublisher.publishEvent(KeyRotationEvent(oldKeyId, newKeyId))
            logger.info("JWT key rotation completed successfully")
        } catch (e: Exception) {
            logger.error("Failed to rotate JWT keys", e)
            eventPublisher.publishEvent(KeyRotationFailedEvent(e))
        }
    }

    private fun generateNewKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        return keyGen.generateKeyPair()
    }

    @EventListener
    suspend fun handleKeyRotation(event: KeyRotationEvent) {
        logger.info("Key rotated from {} to {}", event.oldKeyId, event.newKeyId)
        if (shouldInvalidateTokensOnRotation()) {
            refreshTokenService.invalidateAllTokens()
        }
    }

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(KeyRotationService::class.java)
    }
}

@Component
class SecureKeyStore {
    private val keys: MutableMap<String, KeyPair> = ConcurrentHashMap()
    @Volatile private var currentKeyId: String? = null

    @PostConstruct
    fun initialize() {
        loadKeysFromSecureStorage()
    }

    fun getCurrentPrivateKey(): RSAPrivateKey {
        val currentKey = keys[currentKeyId]
            ?: throw IllegalStateException("No current key available")
        return currentKey.private as RSAPrivateKey
    }

    fun getPublicKey(keyId: String): RSAPublicKey {
        val keyPair = keys[keyId]
            ?: throw IllegalArgumentException("Key not found: $keyId")
        return keyPair.public as RSAPublicKey
    }

    fun addKey(keyId: String, keyPair: KeyPair) {
        storeKeySecurely(keyId, keyPair)
        keys[keyId] = keyPair
        currentKeyId = keyId
    }

    private fun storeKeySecurely(keyId: String, keyPair: KeyPair) {
        logger.info("Storing key $keyId in secure storage (KMS/Vault)")
    }

    private fun loadKeysFromSecureStorage() {
        logger.info("Loading keys from secure storage backend")
    }

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(SecureKeyStore::class.java)
    }
}
```

## Security Monitoring and Alerting

### Reactive Security Event Monitoring

```kotlin
@Component
class SecurityEventMonitor(
    private val meterRegistry: MeterRegistry,
    private val alertService: AlertService,
) {
    @EventListener
    fun monitorAuthenticationFailure(event: AuthenticationFailureBadCredentialsEvent) {
        val username = event.authentication.name
        val clientIp = currentClientIp()
        Counter.builder("security.auth.failures")
            .tag("username", maskUsername(username))
            .tag("ip", clientIp)
            .register(meterRegistry)
            .increment()
        checkForAttackPatterns(username, clientIp)
    }

    @EventListener
    fun monitorSuspiciousActivity(event: SuspiciousActivityEvent) {
        Gauge.builder("security.suspicious.activities") { 1.0 }
            .tag("type", event.activityType)
            .register(meterRegistry)
            .also { /* the gauge is registered for monitoring */ }

        val severity = calculateSeverity(event)
        if (severity == SecuritySeverity.HIGH || severity == SecuritySeverity.CRITICAL) {
            alertService.sendSecurityAlert(event, severity)
        }

        when (severity) {
            SecuritySeverity.CRITICAL -> logger.error("CRITICAL security event: {}", event)
            SecuritySeverity.HIGH -> logger.warn("HIGH security event: {}", event)
            else -> logger.info("Security event: {}", event)
        }
    }

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(SecurityEventMonitor::class.java)
    }
}
```

### Reactive Security Health Indicator

```kotlin
@Component
class SecurityHealthIndicator(
    private val securityConfig: SecurityConfigService,
    private val vulnerabilityScanner: VulnerabilityScanner,
) : ReactiveHealthIndicator {
    override fun health(): Mono<Health> = Mono.fromCallable {
        val builder = Health.up()

        val configStatus = securityConfig.validateConfiguration()
        builder.withDetail("securityConfig", configStatus)

        if (!configStatus.isSecure) {
            builder.status(Status.WARNING).withDetail("configIssues", configStatus.issues)
        }

        val vulnerabilities = vulnerabilityScanner.scan()
        if (vulnerabilities.isNotEmpty()) {
            builder.status(Status.WARNING).withDetail("vulnerabilities", vulnerabilities)
        }

        val keyStatus = securityConfig.checkKeyStatus()
        builder.withDetail("keyStatus", keyStatus)
        if (keyStatus.isExpiringSoon) {
            builder.status(Status.WARNING).withDetail("keyWarning", "Keys will expire soon")
        }

        val certStatus = securityConfig.checkCertificates()
        builder.withDetail("certificateStatus", certStatus)
        if (certStatus.hasExpiringCertificates) {
            builder.status(Status.WARNING).withDetail("certWarning", "Some certificates will expire soon")
        }

        builder.build()
    }.onErrorResume { error ->
        Mono.just(Health.down(error).withDetail("error", "Security health check failed").build())
    }
}
```

## Security Audit Logging

### Reactive Audit Logger

```kotlin
@Component
class SecurityAuditLogger(
    private val auditLogRepository: AuditLogRepository,
    private val objectMapper: ObjectMapper,
) {
    @EventListener
    suspend fun auditAuthenticationEvent(event: AuthenticationEvent) {
        val context = mutableMapOf<String, Any>()
        context["sessionId"] = event.sessionId
        context["requestId"] = event.requestId
        event.failureReason?.let { context["failureReason"] = it }

        val auditLog = AuditLog(
            id = AuditLogId(0L),
            eventType = event.type,
            userId = extractUserId(event),
            username = extractUsername(event),
            clientIp = event.clientIp,
            userAgent = event.userAgent,
            resource = event.resource,
            action = event.action,
            result = event.result,
            timestamp = Instant.now(),
            context = objectMapper.writeValueAsString(context),
        )

        auditLogRepository.save(auditLog)
        logAuditEvent(auditLog)
    }

    @EventListener
    suspend fun auditDataAccess(event: DataAccessEvent) {
        val context = mutableMapOf<String, Any>()
        context["recordIds"] = event.recordIds
        context["fields"] = event.accessedFields
        context["query"] = event.query

        val auditLog = AuditLog(
            id = AuditLogId(0L),
            eventType = "DATA_ACCESS",
            userId = event.userId,
            resource = event.resource,
            action = event.action,
            clientIp = event.clientIp,
            timestamp = Instant.now(),
            context = objectMapper.writeValueAsString(context),
        )

        auditLogRepository.save(auditLog)
    }

    @Scheduled(fixedRate = 3600000)
    suspend fun generateSecurityReport() {
        val report = securityReportGenerator.generateHourlyReport()
        reportService.sendReport(report)
    }

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(SecurityAuditLogger::class.java)
    }
}
```

### GDPR Compliance Application Service

```kotlin
@com.profiletailors.common.domain.Service
class GdprComplianceService(
    private val userRepository: UserRepository,
    private val refreshTokenService: RefreshTokenService,
    private val auditLogService: AuditLogService,
    private val auditLogger: SecurityAuditLogger,
) {
    suspend fun exportUserData(userId: UserId): UserDataExport {
        val user = userRepository.findById(userId)
            ?: throw UserNotFoundException(userId.toString())
        return UserDataExport(
            user = extractUserData(user),
            authHistory = getAuthenticationHistory(userId),
            consents = getConsents(userId),
            activityLogs = getActivityLogs(userId),
            exportDate = Instant.now(),
        )
    }

    suspend fun deleteUserData(userId: UserId) {
        val user = userRepository.findById(userId)
            ?: throw UserNotFoundException(userId.toString())

        val anonymized = user.copy(
            email = generateAnonymizedEmail(),
            firstName = "DELETED",
            lastName = "USER",
            phoneNumber = null,
            deletedAt = Instant.now(),
        )
        userRepository.save(anonymized)

        refreshTokenService.deleteAllUserTokens(user)
        auditLogService.anonymizeAuditLogs(userId)
        auditLogger.logDataDeletion(userId, "GDPR_REQUEST")
    }

    private fun generateAnonymizedEmail(): String =
        "deleted-${UUID.randomUUID()}@deleted.local"

    @EventListener
    suspend fun handleDataSubjectRequest(event: DataSubjectRequestEvent) {
        when (event.requestType) {
            RequestType.ACCESS -> processAccessRequest(event)
            RequestType.DELETION -> processDeletionRequest(event)
            RequestType.RECTIFICATION -> processRectificationRequest(event)
        }
    }
}
```
