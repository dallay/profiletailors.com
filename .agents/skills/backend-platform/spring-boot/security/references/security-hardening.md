# Security Hardening Checklist

## Secure Configuration

### Production Security Headers

```kotlin
@Configuration
class SecurityHeadersConfig {

    @Bean
    public SecurityFilterChain securityHeadersFilterChain(HttpSecurity http) throws Exception {
        return http
            .headers(headers -> headers
                .contentTypeOptions(cto -> cto.and()
                    .xssProtection(xss -> xss
                        .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                    .httpStrictTransportSecurity(hsts -> hsts
                        .maxAgeInSeconds(31536000)
                        .includeSubdomains(true)
                        .preload(true))
                    .frameOptions(frame -> frame
                        .deny()
                        .and())
                    .contentSecurityPolicy(csp -> csp
                        .policyDirectives(
                            "default-src 'self'; " +
                            "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                            "style-src 'self' 'unsafe-inline'; " +
                            "img-src 'self' data: https:; " +
                            "font-src 'self'; " +
                            "connect-src 'self'; " +
                            "frame-ancestors 'none'; " +
                            "base-uri 'self'; " +
                            "form-action 'self'; " +
                            "upgrade-insecure-requests;"
                        )
                        .and())
                    .referrerPolicy(referrer -> referrer
                        .policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                    .permissionsPolicy(permissions -> permissions
                        .policy(
                            "geolocation=(), " +
                            "microphone=(), " +
                            "camera=(), " +
                            "payment=(), " +
                            "usb=(), " +
                            "magnetometer=(), " +
                            "gyroscope=(), " +
                            "accelerometer=()"
                        ))
                )
            )
            .build();
    }
}
```

### Enhanced Password Security

```kotlin
@Service
class SecurePasswordService {

    private val passwordEncoder: PasswordEncoder
    private val passwordHistoryRepository: PasswordHistoryRepository
    private val passwordPolicy: PasswordPolicy

    fun encodePassword(String rawPassword): String {
        // Use Argon2 for better security
        return passwordEncoder.encode(rawPassword);
    }

    fun validatePassword(String password, User user): void {
        // Check password against policy
        if (!meetsPasswordPolicy(password)) {
            throw PasswordPolicyViolationException(getPasswordPolicyViolations(password));
        }

        // Check against password history
        if (isPasswordReused(password, user)) {
            throw PasswordReusedException("Password has been used before");
        }

        // Check against breached passwords
        if (isBreachedPassword(password)) {
            throw BreachedPasswordException("Password has been exposed in data breaches");
        }
    }

    private fun meetsPasswordPolicy(String password): boolean {
        return password.length() >= passwordPolicy.getMinLength() &&
               password.matches(".*[A-Z].*") && // At least one uppercase
               password.matches(".*[a-z].*") && // At least one lowercase
               password.matches(".*\\d.*") &&    // At least one digit
               password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*"); // Special char
    }

    @Async
    public CompletableFuture<Boolean> isBreachedPasswordAsync(String password) {
        String sha1Hash = DigestUtils.sha1Hex(password);
        String prefix = sha1Hash.substring(0, 5);
        String suffix = sha1Hash.substring(5);

        return CompletableFuture.supplyAsync(() -> {
            try {
                String response = restTemplate.getForObject(
                    "https://api.pwnedpasswords.com/range/" + prefix, String.class);

                if (response != null) {
                    return Arrays.stream(response.split("\\r?\\n"))
                        .anyMatch(line -> line.startsWith(suffix.toUpperCase()));
                }
            } catch (Exception e) {
                log.warn("Failed to check breached password API", e);
            }
            return false;
        });
    }
}

@Configuration
class PasswordConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return new Argon2PasswordEncoder(
            16,    // salt length
            32,    // hash length
            1,     // parallelism
            65536, // memory
            3      // iterations
        );
    }
}
```

## Advanced Attack Prevention

### Rate Limiting and Brute Force Protection

```kotlin
@Component
class BruteForceProtectionService {

    private final LoadingCache<String, Integer> loginAttemptsCache;
    private final LoadingCache<String, Long> lockoutCache;
    private val maxAttempts: int
    private val lockoutDuration: Duration

    public BruteForceProtectionService() {
        this.maxAttempts = 5;
        this.lockoutDuration = Duration.ofMinutes(15);

        this.loginAttemptsCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(15))
            .build(key -> 0);

        this.lockoutCache = Caffeine.newBuilder()
            .expireAfterWrite(lockoutDuration)
            .build(key -> 0L);
    }

    fun recordFailedAttempt(String identifier): void {
        int attempts = loginAttemptsCache.asMap().merge(identifier, 1, Integer::sum);

        if (attempts >= maxAttempts) {
            lockout(identifier);
            publishSecurityEvent("ACCOUNT_LOCKED", identifier);
        }
    }

    fun recordSuccessfulAttempt(String identifier): void {
        loginAttemptsCache.invalidate(identifier);
        lockoutCache.invalidate(identifier);
    }

    fun isLockedOut(String identifier): boolean {
        Long lockTime = lockoutCache.getIfPresent(identifier);
        return lockTime != null && lockTime > 0;
    }

    private fun lockout(String identifier): void {
        lockoutCache.put(identifier, System.currentTimeMillis());
    }

    @EventListener
    @Async
    fun handleAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event): void {
        String username = event.getAuthentication().getName();
        recordFailedAttempt(username);

        // Also track by IP
        String clientIp = getClientIpAddress();
        recordFailedAttempt(clientIp);
    }
}

@RestController
@RequestMapping("/api/auth")
class SecureAuthController {

    private val bruteForceProtection: BruteForceProtectionService
    private val recaptchaService: RecaptchaService

    @PostMapping("/login")
    @RateLimited(requests = 5, window = "PT1M")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIpAddress(httpRequest);

        // Check IP-based rate limiting
        if (bruteForceProtection.isLockedOut(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse("Too many failed attempts. Please try again later."));
        }

        // Check username-based rate limiting
        if (bruteForceProtection.isLockedOut(request.username())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse("Account temporarily locked due to failed attempts."));
        }

        // Verify reCAPTCHA for suspicious activity
        if (shouldRequireRecaptcha(request.username(), clientIp)) {
            if (!recaptchaService.verifyRecaptcha(request.recaptchaToken(), clientIp)) {
                return ResponseEntity.badRequest()
                    .body(ErrorResponse("Invalid reCAPTCHA"));
            }
        }

        // Proceed with authentication
        return performAuthentication(request);
    }
}
```

### CSRF Protection with State Management

```kotlin
@Configuration
class CsrfConfig {

    @Bean
    fun customCsrfTokenRepository(): CsrfTokenRepository {
        HttpSessionCsrfTokenRepository repository = HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-CSRF-TOKEN");
        repository.setParameterName("_csrf");
        return repository;
    }

    @Bean
    public SecurityFilterChain csrfFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf
                .csrfTokenRepository(customCsrfTokenRepository())
                .ignoringRequestMatchers("/api/auth/**")
                .csrfTokenRequestHandler(CsrfTokenRequestAttributeHandler())
                .and()
            )
            .addFilterAfter(CsrfCookieFilter(), BasicAuthenticationFilter.class)
            .build();
    }
}

@Component
class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());

        if (csrfToken != null) {
            Cookie cookie = Cookie("XSRF-TOKEN", csrfToken.getToken());
            cookie.setPath("/");
            cookie.setHttpOnly(false);
            cookie.setSecure(true);
            cookie.setMaxAge(-1);
            response.addCookie(cookie);
        }

        filterChain.doFilter(request, response);
    }
}
```

## Input Validation and Sanitization

### Request Validation Filter

```kotlin
@Component
class SecurityValidationFilter implements Filter {

    private val inputSanitizer: InputSanitizer
    private val xssProtection: XssProtectionService

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                        FilterChain chain) throws IOException, ServletException {

        // Wrap request for validation
        SecurityValidatedRequestWrapper wrappedRequest =
            SecurityValidatedRequestWrapper((HttpServletRequest) request);

        // Validate all inputs
        if (!validateRequest(wrappedRequest)) {
            ((HttpServletResponse) response).sendError(
                HttpServletResponse.SC_BAD_REQUEST, "Invalid input detected");
            return;
        }

        chain.doFilter(wrappedRequest, response);
    }

    private fun validateRequest(SecurityValidatedRequestWrapper request): boolean {
        try {
            // Validate query parameters
            request.getParameterMap().forEach((key, values) -> {
                if (xssProtection.containsXss(key) ||
                    Arrays.stream(values).anyMatch(xssProtection::containsXss)) {
                    throw SecurityException("XSS detected in parameters");
                }
            });

            // Validate headers for injection attacks
            Collections.list(request.getHeaderNames()).forEach(headerName -> {
                if (isSuspiciousHeader(headerName, request.getHeader(headerName))) {
                    throw SecurityException("Suspicious header detected");
                }
            });

            return true;

        } catch (Exception e) {
            log.warn("Request validation failed", e);
            return false;
        }
    }

    private fun isSuspiciousHeader(String headerName, String headerValue): boolean {
        String suspiciousPatterns = "(?i)(script|javascript|vbscript|onload|onerror|onclick)";
        return headerName.matches(suspiciousPatterns) || headerValue.matches(suspiciousPatterns);
    }
}

@Component
class XssProtectionService {

    private final Pattern[] xssPatterns = {
        Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("onload(.*?)=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("onerror(.*?)=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("onclick(.*?)=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<img[^>]*src[^=]*=[\"']?javascript:", Pattern.CASE_INSENSITIVE)
    };

    fun containsXss(String input): boolean {
        if (input == null || input.isEmpty()) {
            return false;
        }

        for (Pattern pattern : xssPatterns) {
            if (pattern.matcher(input).find()) {
                return true;
            }
        }

        return false;
    }

    fun sanitize(String input): String {
        if (input == null) {
            return null;
        }

        // HTML encode
        String sanitized = HtmlUtils.htmlEscape(input);

        // Remove potentially dangerous tags
        sanitized = sanitized.replaceAll("<script[^>]*>.*?</script>", "");
        sanitized = sanitized.replaceAll("javascript:", "");
        sanitized = sanitized.replaceAll("vbscript:", "");

        return sanitized;
    }
}
```

### SQL Injection Prevention

```kotlin
@Repository
class SecureUserRepository {

    @PersistenceContext
    private var entityManager: EntityManager

    fun findByEmailSafe(String email): User {
        // Using parameterized query
        String jpql = "SELECT u FROM User u WHERE u.email = :email";
        TypedQuery<User> query = entityManager.createQuery(jpql, User.class);
        query.setParameter("email", email);
        return query.getSingleResult();
    }

    public List<User> searchUsersSecure(String searchTerm) {
        // Validate search term first
        if (!isValidSearchTerm(searchTerm)) {
            throw InvalidSearchTermException("Invalid search term");
        }

        // Using Criteria API for dynamic queries
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);

        // Build safe search predicate
        Predicate predicate = cb.or(
            cb.like(root.get("email"), "%" + escapeSql(searchTerm) + "%"),
            cb.like(root.get("firstName"), "%" + escapeSql(searchTerm) + "%"),
            cb.like(root.get("lastName"), "%" + escapeSql(searchTerm) + "%")
        );

        query.where(predicate);
        return entityManager.createQuery(query).getResultList();
    }

    private fun isValidSearchTerm(String term): boolean {
        // Check for SQL injection patterns
        String[] dangerousPatterns = {
            "'", "\"", ";", "--", "/*", "*/",
            "xp_", "sp_", "DROP", "DELETE", "UPDATE",
            "INSERT", "UNION", "SELECT", "EXEC"
        };

        String upperTerm = term.toUpperCase();
        return Arrays.stream(dangerousPatterns)
            .noneMatch(upperTerm::contains);
    }

    private fun escapeSql(String input): String {
        return input.replace("'", "''");
    }
}
```

## Secure Key Management

### Key Rotation Service

```kotlin
@Service
class KeyRotationService {

    private val keyStore: JwtKeyStore
    private val jwtEncoder: JwtEncoder
    private val eventPublisher: ApplicationEventPublisher

    @Value("${jwt.rotation.enabled:true}")
    private var rotationEnabled: boolean

    @Value("${jwt.rotation.schedule:0 0 2 * * ?}") // 2 AM daily
    private var rotationSchedule: String

    @Scheduled(cron = "${jwt.rotation.schedule}")
    fun rotateKeys(): void {
        if (!rotationEnabled) {
            log.info("Key rotation is disabled");
            return;
        }

        try {
            log.info("Starting JWT key rotation");

            // Generate new key pair
            KeyPair newKeyPair = generateNewKeyPair();
            String newKeyId = generateKeyId();

            // Add new key to store
            keyStore.addKey(newKeyId, newKeyPair);

            // Promote new key to primary after grace period
            scheduleKeyPromotion(newKeyId);

            // Mark old key for retirement
            String oldKeyId = keyStore.getCurrentKeyId();
            if (oldKeyId != null) {
                scheduleKeyRetirement(oldKeyId);
            }

            // Publish rotation event
            eventPublisher.publishEvent(KeyRotationEvent(oldKeyId, newKeyId));

            log.info("JWT key rotation completed successfully");

        } catch (Exception e) {
            log.error("Failed to rotate JWT keys", e);
            eventPublisher.publishEvent(KeyRotationFailedEvent(e));
        }
    }

    private fun generateNewKeyPair(): KeyPair {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            return keyGen.generateKeyPair();
        } catch (Exception e) {
            throw RuntimeException("Failed to generate key pair", e);
        }
    }

    @EventListener
    fun handleKeyRotation(KeyRotationEvent event): void {
        log.info("Key rotated from {} to {}", event.getOldKeyId(), event.getNewKeyId());

        // Invalidate all existing refresh tokens if required
        if (shouldInvalidateTokensOnRotation()) {
            refreshTokenService.invalidateAllTokens();
        }
    }
}

@Component
class SecureKeyStore {

    private final Map<String, KeyPair> keys = new ConcurrentHashMap<>();
    private volatile String currentKeyId;

    @PostConstruct
    fun initialize(): void {
        // Load keys from secure storage
        loadKeysFromSecureStorage();
    }

    fun getCurrentPrivateKey(): RSAPrivateKey {
        KeyPair currentKey = keys.get(currentKeyId);
        if (currentKey == null) {
            throw IllegalStateException("No current key available");
        }
        return (RSAPrivateKey) currentKey.getPrivate();
    }

    fun getPublicKey(String keyId): RSAPublicKey {
        KeyPair keyPair = keys.get(keyId);
        if (keyPair == null) {
            throw IllegalArgumentException("Key not found: " + keyId);
        }
        return (RSAPublicKey) keyPair.getPublic();
    }

    fun addKey(String keyId, KeyPair keyPair): void {
        // Store key in secure storage
        storeKeySecurely(keyId, keyPair);
        keys.put(keyId, keyPair);
    }

    private fun storeKeySecurely(String keyId, KeyPair keyPair): void {
        // Implement secure storage (e.g., AWS KMS, HashiCorp Vault)
        // Never store private keys in application properties or files
    }

    private fun loadKeysFromSecureStorage(): void {
        // Load keys from secure storage
        // This should integrate with your organization's key management solution
    }
}
```

## Security Monitoring and Alerting

### Security Event Monitoring

```kotlin
@Component
@Slf4j
class SecurityEventMonitor {

    private val meterRegistry: MeterRegistry
    private val alertService: AlertService

    @EventListener
    @Async
    fun monitorAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event): void {
        String username = event.getAuthentication().getName();
        String clientIp = getClientIp();

        // Record metrics
        Counter.builder("security.auth.failures")
            .tag("username", maskUsername(username))
            .tag("ip", clientIp)
            .register(meterRegistry)
            .increment();

        // Check for attack patterns
        checkForAttackPatterns(username, clientIp);
    }

    @EventListener
    @Async
    fun monitorSuspiciousActivity(SuspiciousActivityEvent event): void {
        // Record security event
        Gauge.builder("security.suspicious.activities")
            .tag("type", event.getActivityType())
            .register(meterRegistry, () -> 1);

        // Determine severity
        SecuritySeverity severity = calculateSeverity(event);

        // Send alert if high severity
        if (severity == SecuritySeverity.HIGH || severity == SecuritySeverity.CRITICAL) {
            alertService.sendSecurityAlert(event, severity);
        }

        // Log with appropriate level
        when (severity) {
            Severity.CRITICAL -> log.error("CRITICAL security event: {}", event)
            Severity.HIGH -> log.warn("HIGH security event: {}", event)
            else -> log.info("Security event: {}", event)
        }
    }

    private fun checkForAttackPatterns(String username, String clientIp): void {
        // Check for credential stuffing
        if (isCredentialStuffingAttack(username, clientIp)) {
            publishSecurityEvent("CREDENTIAL_STUFFING", Map.of(
                "username", username,
                "ip", clientIp
            ));
        }

        // Check for brute force attack
        if (isBruteForceAttack(clientIp)) {
            publishSecurityEvent("BRUTE_FORCE", Map.of("ip", clientIp));
        }

        // Check for password spraying
        if (isPasswordSprayingAttack(clientIp)) {
            publishSecurityEvent("PASSWORD_SPRAYING", Map.of("ip", clientIp));
        }
    }
}
```

### Security Health Indicator

```kotlin
@Component
class SecurityHealthIndicator implements HealthIndicator {

    private val securityConfig: SecurityConfigService
    private val vulnerabilityScanner: VulnerabilityScanner

    @Override
    fun health(): Health {
        try {
            Health.Builder builder = Health.up();

            // Check security configuration
            SecurityConfigurationStatus configStatus = securityConfig.validateConfiguration();
            builder.withDetail("securityConfig", configStatus);

            if (!configStatus.isSecure()) {
                builder.status(Status.WARNING)
                    .withDetail("configIssues", configStatus.getIssues());
            }

            // Check for known vulnerabilities
            List<Vulnerability> vulnerabilities = vulnerabilityScanner.scan();
            if (!vulnerabilities.isEmpty()) {
                builder.status(Status.WARNING)
                    .withDetail("vulnerabilities", vulnerabilities);
            }

            // Check key expiration
            KeyStatus keyStatus = securityConfig.checkKeyStatus();
            builder.withDetail("keyStatus", keyStatus);

            if (keyStatus.isExpiringSoon()) {
                builder.status(Status.WARNING)
                    .withDetail("keyWarning", "Keys will expire soon");
            }

            // Check SSL/TLS certificates
            CertificateStatus certStatus = securityConfig.checkCertificates();
            builder.withDetail("certificateStatus", certStatus);

            if (certStatus.hasExpiringCertificates()) {
                builder.status(Status.WARNING)
                    .withDetail("certWarning", "Some certificates will expire soon");
            }

            return builder.build();

        } catch (Exception e) {
            return Health.down(e)
                .withDetail("error", "Security health check failed")
                .build();
        }
    }
}
```

## Security Audit Logging

### Comprehensive Audit Logger

```kotlin
@Component
@Slf4j
class SecurityAuditLogger {

    private val auditLogRepository: AuditLogRepository
    private val objectMapper: ObjectMapper

    @EventListener
    @Async("auditEventExecutor")
    fun auditAuthenticationEvent(AuthenticationEvent event): void {
        AuditLog auditLog = AuditLog.builder()
            .eventType(event.getType())
            .userId(extractUserId(event))
            .username(extractUsername(event))
            .clientIp(event.getClientIp())
            .userAgent(event.getUserAgent())
            .resource(event.getResource())
            .action(event.getAction())
            .result(event.getResult())
            .timestamp(Instant.now())
            .build();

        // Add additional context
        Map<String, Object> context = mutableMapOf();
        context.put("sessionId", event.getSessionId());
        context.put("requestId", event.getRequestId());

        if (event.getFailureReason() != null) {
            context.put("failureReason", event.getFailureReason());
        }

        auditLog.setContext(serializeContext(context));

        // Save to database
        auditLogRepository.save(auditLog);

        // Log to file
        logAuditEvent(auditLog);
    }

    @EventListener
    @Async
    fun auditDataAccess(DataAccessEvent event): void {
        AuditLog auditLog = AuditLog.builder()
            .eventType("DATA_ACCESS")
            .userId(event.getUserId())
            .resource(event.getResource())
            .action(event.getAction())
            .clientIp(event.getClientIp())
            .timestamp(Instant.now())
            .result("SUCCESS")
            .build();

        // Record what data was accessed
        Map<String, Object> context = mutableMapOf();
        context.put("recordIds", event.getRecordIds());
        context.put("fields", event.getAccessedFields());
        context.put("query", event.getQuery());

        auditLog.setContext(serializeContext(context));
        auditLogRepository.save(auditLog);
    }

    @Scheduled(fixedRate = 3600000) // Hourly
    fun generateSecurityReport(): void {
        SecurityReport report = securityReportGenerator.generateHourlyReport();
        reportService.sendReport(report);
    }

    private fun logAuditEvent(AuditLog auditLog): void {
        try {
            String logMessage = objectMapper.writeValueAsString(auditLog);
            log.info("AUDIT: {}", logMessage);
        } catch (Exception e) {
            log.error("Failed to serialize audit log", e);
        }
    }

    private fun serializeContext(Map<String, Object> context): String {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            return "{}";
        }
    }
}
```

### GDPR Compliance Features

```kotlin
@Service
class GdprComplianceService {

    @Transactional
    fun exportUserData(String userId): UserDataExport {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> UserNotFoundException(userId));

        return UserDataExport.builder()
            .user(extractUserData(user))
            .authHistory(getAuthenticationHistory(userId))
            .consents(getConsents(userId))
            .activityLogs(getActivityLogs(userId))
            .exportDate(Instant.now())
            .build();
    }

    @Transactional
    fun deleteUserData(String userId): void {
        // Anonymize user data instead of hard delete for audit purposes
        User user = userRepository.findById(userId)
            .orElseThrow(() -> UserNotFoundException(userId));

        // Mark as deleted
        user.setEmail(generateAnonymizedEmail());
        user.setFirstName("DELETED");
        user.setLastName("USER");
        user.setPhoneNumber(null);
        user.setDeletedAt(Instant.now());

        userRepository.save(user);

        // Delete sensitive data
        refreshTokenService.deleteAllUserTokens(user);
        auditLogService.anonymizeAuditLogs(userId);

        // Record deletion
        auditLogger.logDataDeletion(userId, "GDPR_REQUEST");
    }

    private fun generateAnonymizedEmail(): String {
        return "deleted-" + UUID.randomUUID() + "@deleted.local";
    }

    @EventListener
    fun handleDataSubjectRequest(event: DataSubjectRequestEvent) {
        when (event.requestType) {
            RequestType.ACCESS -> processAccessRequest(event)
            RequestType.DELETION -> processDeletionRequest(event)
            RequestType.RECTIFICATION -> processRectificationRequest(event)
        }
    }
}
```
