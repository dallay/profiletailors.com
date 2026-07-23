# Token Management Best Practices

## Refresh Token Strategy

### Secure Refresh Token Storage

```kotlin
@Entity
@Table(name = "refresh_tokens")
class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private var id: Long

    @Column(unique = true, nullable = false, columnDefinition = "TEXT")
    private var token: String

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private var user: User

    @Column(name = "token_id", unique = true, nullable = false)
    private var tokenId: String // JWT ID (jti claim)

    @Column(name = "session_id")
    private var sessionId: String // Session identifier

    @Column(name = "device_id")
    private var deviceId: String // Device fingerprint

    @Column(name = "device_info")
    private var deviceInfo: String // User agent and device details

    @Column(name = "ip_address")
    private var ipAddress: String

    @Column(name = "created_at", nullable = false)
    private var createdAt: Instant

    @Column(name = "expires_at", nullable = false)
    private var expiresAt: Instant

    @Column(name = "last_used_at")
    private var lastUsedAt: Instant

    @Column(name = "revoked_at")
    private var revokedAt: Instant

    @Column(name = "replaced_by")
    private var replacedBy: String // New token ID if rotated

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(nullable = false)
    private boolean active = true;

    // Token metadata
    @Column(name = "usage_count")
    private int usageCount = 0;

    @Column(name = "max_usage")
    private var maxUsage: Integer // Optional usage limit

    fun isExpired(): boolean {
        return Instant.now().isAfter(expiresAt);
    }

    fun isValid(): boolean {
        return !revoked && active && !isExpired();
    }

    fun revoke(): void {
        this.revoked = true;
        this.revokedAt = Instant.now();
        this.active = false;
    }

    fun markUsed(): void {
        this.lastUsedAt = Instant.now();
        this.usageCount++;
    }
}
```

### Refresh Token Repository

```kotlin
@Repository
interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken (String token);

    Optional<RefreshToken> findByTokenId (String tokenId);

    List<RefreshToken> findByUserAndRevokedFalse (User user);

    List<RefreshToken> findByUserAndRevokedFalseAndExpiresAtAfter (User user, Instant now);

    List<RefreshToken> findByExpiresAtBefore (Instant cutoff);

    List<RefreshToken> findByRevokedTrueAndRevokedAtBefore (Instant cutoff);

    @Query("SELECT rt FROM RefreshToken rt WHERE rt.user = :user AND rt.revoked = false ORDER BY rt.createdAt ASC")
    List<RefreshToken> findOldestActiveTokensByUser (@Param("user") User user);

    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.user = :user AND rt.revoked = false AND rt.expiresAt > :now")
    long countActiveTokensByUser (@Param("user") User user, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.user = :user AND rt.createdAt < :cutoff")
    void deleteOldTokensByUser (@Param("user") User user, @Param("cutoff") Instant cutoff);
}
```

### Refresh Token Service with Rotation

```kotlin
@Service
@Transactional
@Slf4j
class RefreshTokenService {

    private val refreshTokenRepository: RefreshTokenRepository
    private val userRepository: UserRepository
    private val jwtTokenService: JwtTokenService
    private val claimsService: JwtClaimsService

    @Value("${jwt.refresh - token - expiration:P7D}")
    private var refreshTokenExpiration: Duration

    @Value("${jwt.max - active - tokens:5}")
    private var maxActiveTokensPerUser: int

    @Value("${jwt.token - rotation - enabled:true}")
    private var tokenRotationEnabled: boolean

    @Value("${jwt.token - rotation - threshold:P3D}")
    private var tokenRotationThreshold: Duration

    fun createRefreshToken(User user, HttpServletRequest request): RefreshTokenResponse {
        // Enforce maximum active tokens
        enforceMaxActiveTokens(user);

        // Extract request information
        String ipAddress = extractIpAddress (request);
        String deviceInfo = extractDeviceInfo (request);
        String deviceId = generateDeviceId (request);

        // Create JWT claims
        JwtClaimsSet claims = claimsService . createRefreshTokenClaims (user);
        String tokenValue = jwtTokenService . encodeToken (claims);

        // Create refresh token entity
        RefreshToken refreshToken = RefreshToken . builder ()
            .token(tokenValue)
            .user(user)
            .tokenId(claims.getClaimAsString("jti"))
            .sessionId(claims.getClaimAsString("sessionId"))
            .deviceId(deviceId)
            .deviceInfo(deviceInfo)
            .ipAddress(ipAddress)
            .createdAt(Instant.now())
            .expiresAt(claims.getExpiresAt())
            .active(true)
            .revoked(false)
            .build();

        refreshToken = refreshTokenRepository.save(refreshToken);

        // Publish token created event
        applicationEventPublisher.publishEvent(
            RefreshTokenCreatedEvent(refreshToken)
        );

        return new RefreshTokenResponse (
                refreshToken.getToken(),
        refreshToken.getExpiresAt().toEpochMilli(),
        refreshToken.getSessionId()
        );
    }

    @Transactional
    public AccessTokenResponse refreshToken(RefreshTokenRequest request,
    HttpServletRequest httpRequest)
    {

        String refreshTokenValue = request . refreshToken ();
        String ipAddress = extractIpAddress (httpRequest);

        // Validate and retrieve refresh token
        RefreshToken refreshToken = validateRefreshToken (refreshTokenValue, ipAddress);

        User user = refreshToken . getUser ();

        // Check account status
        validateUserAccount(user);

        // Mark token as used
        refreshToken.markUsed();
        refreshTokenRepository.save(refreshToken);

        // Generate new access token
        AccessTokenResponse accessToken = jwtTokenService . generateAccessToken (user);

        // Implement token rotation if enabled
        if (shouldRotateRefreshToken(refreshToken)) {
            RefreshTokenResponse newRefreshToken = createRefreshToken (user, httpRequest);

            // Mark old token as replaced
            refreshToken.setReplacedBy(extractTokenId(newRefreshToken.token()));
            refreshToken.revoke();
            refreshTokenRepository.save(refreshToken);

            return AccessTokenResponse.builder()
                .token(accessToken.token())
                .expiresAt(accessToken.expiresAt())
                .refreshToken(newRefreshToken.token())
                .refreshTokenExpiresAt(newRefreshToken.expiresAt())
                .build();
        }

        return accessToken;
    }

    @Transactional
    fun revokeRefreshToken(String token, String reason): void {
        refreshTokenRepository.findByToken(token)
            .ifPresent(refreshToken -> {
            refreshToken.revoke();
            refreshTokenRepository.save(refreshToken);

            // Publish token revoked event
            applicationEventPublisher.publishEvent(
                RefreshTokenRevokedEvent(refreshToken, reason)
            );
        });
    }

    @Transactional
    fun revokeAllUserTokens(User user, String reason): void {
        List<RefreshToken> activeTokens = refreshTokenRepository
                .findByUserAndRevokedFalse(user);

        activeTokens.forEach(token -> {
            token.revoke();
            // Store revocation reason in audit log
        });

        refreshTokenRepository.saveAll(activeTokens);

        // Publish batch revocation event
        applicationEventPublisher.publishEvent(
            AllRefreshTokensRevokedEvent(user, activeTokens.size(), reason)
        );
    }

    private fun validateRefreshToken(String tokenValue, String ipAddress): RefreshToken {
        RefreshToken refreshToken = refreshTokenRepository . findByToken (tokenValue)
            .orElseThrow(() -> InvalidTokenException("Refresh token not found"));

        // Validate token status
        if (!refreshToken.isValid()) {
            if (refreshToken.isRevoked()) {
                throw TokenRevokedException("Token has been revoked");
            }
            if (refreshToken.isExpired()) {
                refreshTokenRepository.delete(refreshToken);
                throw ExpiredTokenException("Refresh token expired");
            }
            throw InvalidTokenException("Token is invalid");
        }

        // Validate token usage
        if (refreshToken.getMaxUsage() != null &&
            refreshToken.getUsageCount() >= refreshToken.getMaxUsage()
        ) {
            refreshToken.revoke();
            refreshTokenRepository.save(refreshToken);
            throw TokenUsageExceededException("Token usage limit exceeded");
        }

        // Validate IP address (optional security measure)
        if (!isValidIpAddress(refreshToken.getIpAddress(), ipAddress)) {
            log.warn(
                "Suspicious refresh token usage - IP mismatch. Expected: {}, Actual: {}",
                refreshToken.getIpAddress(), ipAddress
            );

            // Optional: revoke token on IP mismatch
            // refreshToken.revoke();
            // refreshTokenRepository.save(refreshToken);
            // throw SecurityException("IP address mismatch");
        }

        return refreshToken;
    }

    private fun enforceMaxActiveTokens(User user): void {
        long activeTokens = refreshTokenRepository . countActiveTokensByUser (
                user, Instant.now());

        if (activeTokens >= maxActiveTokensPerUser) {
            // Revoke oldest token
            List<RefreshToken> oldestTokens = refreshTokenRepository
                    .findOldestActiveTokensByUser(user);

            if (!oldestTokens.isEmpty()) {
                RefreshToken oldestToken = oldestTokens . get (0);
                oldestToken.revoke();
                refreshTokenRepository.save(oldestToken);

                log.info(
                    "Revoked oldest refresh token for user {} due to limit",
                    user.getId()
                );
            }
        }
    }

    private fun shouldRotateRefreshToken(RefreshToken refreshToken): boolean {
        if (!tokenRotationEnabled) {
            return false;
        }

        // Rotate if token is older than threshold
        boolean ageThreshold = refreshToken . getCreatedAt ()
            .isBefore(Instant.now().minus(tokenRotationThreshold));

        // Rotate if token has been used too many times
        boolean usageThreshold = refreshToken . getUsageCount () > 50;

        return ageThreshold || usageThreshold;
    }

    // Cleanup expired and revoked tokens
    @Scheduled(fixedRate = 86400000) // Daily
    fun cleanupTokens(): void {
        Instant cutoff = Instant . now ().minus(30, ChronoUnit.DAYS);

        // Delete expired tokens older than 30 days
        List<RefreshToken> expiredTokens = refreshTokenRepository
                .findByExpiresAtBefore(cutoff);
        refreshTokenRepository.deleteAll(expiredTokens);

        // Delete revoked tokens older than 30 days
        List<RefreshToken> revokedTokens = refreshTokenRepository
                .findByRevokedTrueAndRevokedAtBefore(cutoff);
        refreshTokenRepository.deleteAll(revokedTokens);

        log.info(
            "Cleaned up {} expired and {} revoked tokens",
            expiredTokens.size(), revokedTokens.size()
        );
    }
}
```

## Token Blacklisting

### BlacklistedToken Entity

```kotlin
@Entity
@Table(name = "blacklisted_tokens")
class BlacklistedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private var id: Long

    @Column(name = "token_id", unique = true, nullable = false)
    private var tokenId: String // JWT ID (jti claim)

    @Column(columnDefinition = "TEXT")
    private var token: String // Full token (for debugging)

    @Column(name = "blacklisted_at", nullable = false)
    private var blacklistedAt: Instant

    @Column(name = "expires_at", nullable = false)
    private var expiresAt: Instant

    @Column(name = "blacklisted_by")
    private var blacklistedBy: String // User ID or system

    private var reason: String // Reason for blacklisting

    @Enumerated(EnumType.STRING)
    private var blacklistReason: BlacklistReason

    enum class BlacklistReason {
        LOGOUT,
        PASSWORD_CHANGE,
        ROLE_CHANGE,
        ACCOUNT_SUSPENSION,
        SUSPICIOUS_ACTIVITY,
        TOKEN_THEFT,
        ADMIN_REVOCATION,
        MASS_REVOCATION
    }

    fun isExpired(): boolean {
        return Instant.now().isAfter(expiresAt);
    }
}
```

### Token Blacklisting Service

```kotlin
@Service
@Transactional
class TokenBlacklistingService {

    private val blacklistedTokenRepository: BlacklistedTokenRepository
    private val jwtDecoder: JwtDecoder

    fun blacklistToken(String token, String reason, BlacklistReason blacklistReason): void {
        try {
            Jwt jwt = jwtDecoder . decode (token);
            String tokenId = jwt . getClaimAsString ("jti");
            Instant expiresAt = jwt . getExpiresAt ();

            if (tokenId == null || expiresAt == null) {
                throw InvalidTokenException("Token missing required claims");
            }

            BlacklistedToken blacklistedToken = BlacklistedToken . builder ()
                .tokenId(tokenId)
                .token(token.substring(0, Math.min(token.length(), 100))) // Store first 100 chars
                .blacklistedAt(Instant.now())
                .expiresAt(expiresAt)
                .blacklistedBy(getCurrentUser())
                .reason(reason)
                .blacklistReason(blacklistReason)
                .build();

            blacklistedTokenRepository.save(blacklistedToken);

            log.info("Token {} blacklisted for reason: {}", tokenId, reason);

        } catch (JwtException e) {
            log.error("Failed to blacklist token", e);
            throw InvalidTokenException("Invalid token", e);
        }
    }

    @Transactional(readOnly = true)
    fun isTokenBlacklisted(String token): boolean {
        try {
            Jwt jwt = jwtDecoder . decode (token);
            String tokenId = jwt . getClaimAsString ("jti");

            if (tokenId == null) {
                return false;
            }

            return blacklistedTokenRepository.existsByTokenId(tokenId);

        } catch (JwtException e) {
            // If token is invalid, it's effectively blacklisted
            return true;
        }
    }

    @Transactional
    fun blacklistAllUserTokens(User user, String reason, BlacklistReason blacklistReason): void {
        // This would require tracking all active tokens in the system
        // For now, we'll implement a user-based blacklist
        UserBlacklist blacklist = UserBlacklist . builder ()
            .user(user)
            .blacklistedAt(Instant.now())
            .reason(reason)
            .blacklistReason(blacklistReason)
            .build();

        userBlacklistRepository.save(blacklist);
    }

    @Scheduled(fixedRate = 3600000) // Every hour
    fun cleanupExpiredBlacklistedTokens(): void {
        List<BlacklistedToken> expiredTokens = blacklistedTokenRepository
                .findByExpiresAtBefore(Instant.now());

        blacklistedTokenRepository.deleteAll(expiredTokens);

        if (!expiredTokens.isEmpty()) {
            log.info("Cleaned up {} expired blacklisted tokens", expiredTokens.size());
        }
    }
}
```

## Session Management

### Session Tracking

```kotlin
@Entity
@Table(name = "user_sessions")
class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private var id: Long

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private var user: User

    @Column(name = "session_id", unique = true, nullable = false)
    private var sessionId: String

    @Column(name = "device_id")
    private var deviceId: String

    @Column(name = "device_info")
    private var deviceInfo: String

    @Column(name = "ip_address")
    private var ipAddress: String

    @Column(name = "user_agent")
    private var userAgent: String

    @Column(name = "location")
    private var location: String // Geolocation based on IP

    @Column(name = "login_at", nullable = false)
    private var loginAt: Instant

    @Column(name = "last_activity_at")
    private var lastActivityAt: Instant

    @Column(name = "logout_at")
    private var logoutAt: Instant

    @Column(name = "session_timeout_at")
    private var sessionTimeoutAt: Instant

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean persistent = false;

    // Session metadata
    @Column(name = "login_method")
    @Enumerated(EnumType.STRING)
    private var loginMethod: LoginMethod

    @Column(name = "mfa_verified")
    private boolean mfaVerified = false;

    @Column(name = "risk_score")
    private var riskScore: Integer

    fun isValid(): boolean {
        return active && !isExpired();
    }

    fun isExpired(): boolean {
        return sessionTimeoutAt != null && Instant.now().isAfter(sessionTimeoutAt);
    }

    fun updateActivity(): void {
        this.lastActivityAt = Instant.now();
        // Update session timeout based on inactivity policy
        this.sessionTimeoutAt = Instant.now().plus(30, ChronoUnit.MINUTES);
    }

    fun terminate(): void {
        this.active = false;
        this.logoutAt = Instant.now();
    }
}

@Service
class SessionManagementService {

    @Value("${security.session.max - concurrent:5}")
    private var maxConcurrentSessions: int

    @Value("${security.session.inactivity - timeout:PT30M}")
    private var inactivityTimeout: Duration

    fun createSession(User user, HttpServletRequest request, LoginMethod loginMethod): UserSession {
        String sessionId = UUID . randomUUID ().toString();
        String ipAddress = extractIpAddress (request);
        String deviceInfo = extractDeviceInfo (request);
        String deviceId = generateDeviceId (request);

        // Enforce concurrent session limit
        enforceConcurrentSessionLimit(user);

        UserSession session = UserSession . builder ()
            .user(user)
            .sessionId(sessionId)
            .deviceId(deviceId)
            .deviceInfo(deviceInfo)
            .ipAddress(ipAddress)
            .userAgent(request.getHeader("User-Agent"))
            .location(lookupLocation(ipAddress))
            .loginAt(Instant.now())
            .lastActivityAt(Instant.now())
            .sessionTimeoutAt(Instant.now().plus(inactivityTimeout))
            .active(true)
            .loginMethod(loginMethod)
            .riskScore(calculateRiskScore(request))
            .build();

        session = sessionRepository.save(session);

        // Publish session created event
        applicationEventPublisher.publishEvent(UserSessionCreatedEvent(session));

        return session;
    }

    @Transactional
    fun terminateSession(String sessionId, String reason): void {
        sessionRepository.findBySessionId(sessionId)
            .ifPresent(session -> {
            session.terminate();
            sessionRepository.save(session);

            // Revoke associated refresh tokens
            refreshTokenService.revokeTokensBySessionId(sessionId);

            // Publish session terminated event
            applicationEventPublisher.publishEvent(
                UserSessionTerminatedEvent(session, reason)
            );
        });
    }

    @Transactional
    fun terminateAllUserSessions(User user, String reason): void {
        List<UserSession> activeSessions = sessionRepository
                .findByUserAndActiveTrue(user);

        activeSessions.forEach(session -> {
            session.terminate();
            // Revoke associated tokens
            refreshTokenService.revokeTokensBySessionId(session.getSessionId());
        });

        sessionRepository.saveAll(activeSessions);

        // Publish batch session termination event
        applicationEventPublisher.publishEvent(
            AllUserSessionsTerminatedEvent(user, activeSessions.size(), reason)
        );
    }

    private fun enforceConcurrentSessionLimit(User user): void {
        long activeSessions = sessionRepository . countByUserAndActiveTrue (user);

        if (activeSessions >= maxConcurrentSessions) {
            // Terminate oldest session
            List<UserSession> oldestSessions = sessionRepository
                    .findByUserAndActiveTrueOrderByLoginAtAsc(user);

            if (!oldestSessions.isEmpty()) {
                UserSession oldestSession = oldestSessions . get (0);
                terminateSession(
                    oldestSession.getSessionId(),
                    "Concurrent session limit exceeded"
                );
            }
        }
    }

    // Cleanup inactive sessions
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    fun cleanupInactiveSessions(): void {
        List<UserSession> inactiveSessions = sessionRepository
                .findByActiveTrueAndSessionTimeoutAtBefore(Instant.now());

        inactiveSessions.forEach(session -> {
            session.terminate();
            // Revoke associated refresh tokens
            refreshTokenService.revokeTokensBySessionId(session.getSessionId());
        });

        sessionRepository.saveAll(inactiveSessions);

        if (!inactiveSessions.isEmpty()) {
            log.info("Cleaned up {} inactive sessions", inactiveSessions.size());
        }
    }
}
```

## Token Security Headers

### Security Headers Configuration

```kotlin
@Configuration
class SecurityHeadersConfig {

    @Bean
    public SecurityFilterChain securityHeaders(HttpSecurity http) throws Exception
    {
        return http
            .headers(headers -> headers
        .contentTypeOptions(cto -> cto.and()
        .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
        .httpStrictTransportSecurity(hsts -> hsts
        .maxAgeInSeconds(31536000)
        .includeSubdomains(true)
        .preload(true))
        .frameOptions(frame -> frame.deny())
        .contentSecurityPolicy(csp -> csp
        .policyDirectives("default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self'; connect-src 'self'; frame-ancestors 'none';"))
        .referrerPolicy(referrer -> referrer
        .policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))))
        .and())
        .build();
    }
}
```

### Rate Limiting for Token Endpoints

```kotlin
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private val authRateLimiter: RateLimiter

    @PostMapping("/login")
    @RateLimited(requests = 5, window = "PT1M")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request)
    {
        // Login implementation
    }

    @PostMapping("/refresh")
    @RateLimited(requests = 10, window = "PT1M")
    public ResponseEntity<RefreshTokenResponse> refresh(
    @RequestBody RefreshTokenRequest request)
    {
        // Refresh token implementation
    }
}

@Aspect
@Component
class RateLimitingAspect {

    private final Map<String, Bucket> bucketCache = new ConcurrentHashMap<>();

    @Around("@annotation(rateLimited)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable
    {
        String key = generateKey (joinPoint, rateLimited);
        Bucket bucket = bucketCache . computeIfAbsent (key, k -> createBucket(rateLimited));

        if (bucket.tryConsume(1)) {
            return joinPoint.proceed();
        } else {
            throw RateLimitExceededException("Rate limit exceeded");
        }
    }

    private fun generateKey(ProceedingJoinPoint joinPoint, RateLimited rateLimited): String {
        HttpServletRequest request = getCurrentRequest ();
        String clientIp = getClientIpAddress (request);

        return String.format(
            "%s:%s:%s",
            joinPoint.getSignature().toShortString(),
            clientIp,
            rateLimited.identifier()
        );
    }
}
```