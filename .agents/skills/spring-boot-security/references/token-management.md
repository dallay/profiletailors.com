# Token Management Best Practices

This document covers token management patterns for the reactive SMP backend. All
examples use the WebFlux stack (`ServerHttpSecurity`, `ServerWebExchange`,
`R2dbcRepository`, `suspend fun`, `Reactive*` types) and the
`com.profiletailors.common.domain.Service` marker for application services. The
servlet-stack patterns appear only in `migration-spring-security-6x.md` for
historical reference.

## Refresh Token Strategy

### Refresh Token Domain Model

```kotlin
package com.profiletailors.smp.identity.domain

data class RefreshToken(
    val id: RefreshTokenId,
    val tokenHash: String,
    val userId: UserId,
    val tokenId: String,
    val sessionId: String? = null,
    val deviceId: String? = null,
    val deviceInfo: String? = null,
    val ipAddress: String? = null,
    val createdAt: Instant,
    val expiresAt: Instant,
    val lastUsedAt: Instant? = null,
    val revokedAt: Instant? = null,
    val replacedBy: String? = null,
    val revoked: Boolean = false,
    val active: Boolean = true,
    val usageCount: Int = 0,
    val maxUsage: Int? = null,
) {
    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)

    fun isValid(): Boolean = !revoked && active && !isExpired()

    fun revoke(): RefreshToken = copy(
        revoked = true,
        revokedAt = Instant.now(),
        active = false,
    )

    fun markUsed(): RefreshToken = copy(
        lastUsedAt = Instant.now(),
        usageCount = usageCount + 1,
    )
}
```

### Refresh Token Repository (Domain Port)

```kotlin
interface RefreshTokenRepository {
    suspend fun findByTokenHash(tokenHash: String): RefreshToken?
    suspend fun findByTokenId(tokenId: String): RefreshToken?
    suspend fun findAllActiveByUser(userId: UserId): List<RefreshToken>
    suspend fun findActiveByUserAfter(userId: UserId, instant: Instant): List<RefreshToken>
    suspend fun findExpiredBefore(cutoff: Instant): List<RefreshToken>
    suspend fun findRevokedBefore(cutoff: Instant): List<RefreshToken>
    suspend fun findOldestActiveByUser(userId: UserId): RefreshToken?
    suspend fun countActiveByUser(userId: UserId, now: Instant): Long
    suspend fun save(token: RefreshToken): RefreshToken
    suspend fun saveAll(tokens: Collection<RefreshToken>): List<RefreshToken>
    suspend fun delete(token: RefreshToken)
    suspend fun deleteAll(tokens: Collection<RefreshToken>)
    suspend fun deleteOldestByUser(userId: UserId)
}
```

The R2DBC adapter that implements this port lives in
`com.profiletailors.smp.identity.adapter.out.persistence`. The port stays
framework-free.

### Coroutine-Based Refresh Token Service

```kotlin
@com.profiletailors.common.domain.Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userRepository: UserRepository,
    private val jwtTokenService: JwtTokenService,
    private val claimsService: JwtClaimsService,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    @Value("\${jwt.refresh-token-expiration:P7D}")
    private val refreshTokenExpiration: Duration = Duration.ofDays(7)

    @Value("\${jwt.max-active-tokens:5}")
    private val maxActiveTokensPerUser: Int = 5

    @Value("\${jwt.token-rotation-enabled:true}")
    private val tokenRotationEnabled: Boolean = true

    @Value("\${jwt.token-rotation-threshold:P3D}")
    private val tokenRotationThreshold: Duration = Duration.ofDays(3)

    suspend fun createRefreshToken(user: UserAccount, exchange: ServerWebExchange): RefreshTokenResponse {
        enforceMaxActiveTokens(user.id)

        val ipAddress = extractIpAddress(exchange)
        val deviceInfo = extractDeviceInfo(exchange)
        val deviceId = generateDeviceId(exchange)

        val claims = claimsService.createRefreshTokenClaims(user)
        val tokenValue = jwtTokenService.encodeClaims(claims)

        val saved = refreshTokenRepository.save(
            RefreshToken(
                id = RefreshTokenId(0L),
                tokenHash = hashToken(tokenValue),
                userId = user.id,
                tokenId = claims.getClaimAsString("jti"),
                sessionId = claims.getClaimAsString("sessionId"),
                deviceId = deviceId,
                deviceInfo = deviceInfo,
                ipAddress = ipAddress,
                createdAt = Instant.now(),
                expiresAt = claims.expiresAt,
                active = true,
                revoked = false,
            ),
        )

        applicationEventPublisher.publishEvent(RefreshTokenCreatedEvent(saved))

        return RefreshTokenResponse(
            token = tokenValue,
            expiresAt = saved.expiresAt.toEpochMilli(),
            sessionId = saved.sessionId,
        )
    }

    suspend fun refreshToken(
        request: RefreshTokenRequest,
        exchange: ServerWebExchange,
    ): AccessTokenResponse {
        val ipAddress = extractIpAddress(exchange)
        val refreshToken = validateRefreshToken(request.refreshToken, ipAddress)
        val user = userRepository.findById(refreshToken.userId)
            ?: throw AccountNotFoundException("User not found")

        validateUserAccount(user)

        val usedToken = refreshToken.markUsed()
        refreshTokenRepository.save(usedToken)

        val accessToken = jwtTokenService.generateAccessToken(user)

        if (shouldRotateRefreshToken(usedToken)) {
            val newRefreshToken = createRefreshToken(user, exchange)
            val withRotation = usedToken.copy(
                replacedBy = newRefreshToken.sessionId,
                revoked = true,
                revokedAt = Instant.now(),
                active = false,
            )
            refreshTokenRepository.save(withRotation)

            return AccessTokenResponse(
                token = accessToken.token,
                expiresAt = accessToken.expiresAt,
                refreshToken = newRefreshToken.token,
                refreshTokenExpiresAt = newRefreshToken.expiresAt,
            )
        }

        return AccessTokenResponse(
            token = accessToken.token,
            expiresAt = accessToken.expiresAt,
        )
    }

    suspend fun revokeRefreshToken(token: String, reason: String) {
        val stored = refreshTokenRepository.findByTokenHash(hashToken(token))
            ?: return
        val revoked = stored.revoke()
        refreshTokenRepository.save(revoked)
        applicationEventPublisher.publishEvent(RefreshTokenRevokedEvent(revoked, reason))
    }

    suspend fun revokeAllUserTokens(userId: UserId, reason: String) {
        val activeTokens = refreshTokenRepository.findAllActiveByUser(userId)
        val revoked = activeTokens.map { it.revoke() }
        refreshTokenRepository.saveAll(revoked)
        applicationEventPublisher.publishEvent(
            AllRefreshTokensRevokedEvent(userId, revoked.size, reason),
        )
    }

    private suspend fun validateRefreshToken(
        tokenValue: String,
        ipAddress: String,
    ): RefreshToken {
        val refreshToken = refreshTokenRepository.findByTokenHash(hashToken(tokenValue))
            ?: throw InvalidTokenException("Refresh token not found")

        if (!refreshToken.isValid()) {
            if (refreshToken.revoked) {
                throw TokenRevokedException("Token has been revoked")
            }
            if (refreshToken.isExpired()) {
                refreshTokenRepository.delete(refreshToken)
                throw ExpiredTokenException("Refresh token expired")
            }
            throw InvalidTokenException("Token is invalid")
        }

        if (refreshToken.maxUsage != null && refreshToken.usageCount >= refreshToken.maxUsage) {
            refreshTokenRepository.save(refreshToken.revoke())
            throw TokenUsageExceededException("Token usage limit exceeded")
        }

        if (!isValidIpAddress(refreshToken.ipAddress, ipAddress)) {
            logger.warn(
                "Suspicious refresh token usage - IP mismatch. Expected: {}, Actual: {}",
                refreshToken.ipAddress, ipAddress,
            )
        }

        return refreshToken
    }

    private suspend fun enforceMaxActiveTokens(userId: UserId) {
        val active = refreshTokenRepository.countActiveByUser(userId, Instant.now())
        if (active >= maxActiveTokensPerUser) {
            refreshTokenRepository.findOldestActiveByUser(userId)?.let { oldest ->
                refreshTokenRepository.save(oldest.revoke())
                logger.info("Revoked oldest refresh token for user {} due to limit", userId)
            }
        }
    }

    private fun shouldRotateRefreshToken(token: RefreshToken): Boolean {
        if (!tokenRotationEnabled) return false
        val ageThreshold = token.createdAt.isBefore(Instant.now().minus(tokenRotationThreshold))
        val usageThreshold = token.usageCount > 50
        return ageThreshold || usageThreshold
    }

    @Scheduled(fixedRate = 86400000)
    suspend fun cleanupTokens() {
        val cutoff = Instant.now().minus(30, ChronoUnit.DAYS)
        val expired = refreshTokenRepository.findExpiredBefore(cutoff)
        val revoked = refreshTokenRepository.findRevokedBefore(cutoff)
        refreshTokenRepository.deleteAll(expired + revoked)
        logger.info("Cleaned up {} expired and {} revoked tokens", expired.size, revoked.size)
    }

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(RefreshTokenService::class.java)
    }
}
```

## Token Blacklisting

### Blacklisted Token Domain Model

```kotlin
data class BlacklistedToken(
    val id: BlacklistedTokenId,
    val tokenId: String,
    val tokenPreview: String,
    val blacklistedAt: Instant,
    val expiresAt: Instant,
    val blacklistedBy: String,
    val reason: String,
    val blacklistReason: BlacklistReason,
) {
    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)
}

enum class BlacklistReason {
    LOGOUT, PASSWORD_CHANGE, ROLE_CHANGE, ACCOUNT_SUSPENSION,
    SUSPICIOUS_ACTIVITY, TOKEN_THEFT, ADMIN_REVOCATION, MASS_REVOCATION,
}
```

### Blacklist Repository Port

```kotlin
interface BlacklistedTokenRepository {
    suspend fun save(token: BlacklistedToken): BlacklistedToken
    suspend fun existsByTokenId(tokenId: String): Boolean
    suspend fun findByExpiresAtBefore(cutoff: Instant): List<BlacklistedToken>
    suspend fun deleteAll(tokens: Collection<BlacklistedToken>)
}
```

### Coroutine Blacklisting Application Service

```kotlin
@com.profiletailors.common.domain.Service
class TokenBlacklistingService(
    private val blacklistedTokenRepository: BlacklistedTokenRepository,
    private val jwtDecoder: ReactiveJwtDecoder,
) {
    suspend fun blacklistToken(
        token: String,
        reason: String,
        blacklistReason: BlacklistReason,
    ) {
        try {
            val jwt = jwtDecoder.decode(token).awaitSingle()
            val tokenId = jwt.getClaimAsString("jti") ?: throw InvalidTokenException("Missing jti")
            val expiresAt = jwt.expiresAt ?: throw InvalidTokenException("Missing exp claim")

            blacklistedTokenRepository.save(
                BlacklistedToken(
                    id = BlacklistedTokenId(0L),
                    tokenId = tokenId,
                    tokenPreview = token.substring(0, minOf(token.length, 100)),
                    blacklistedAt = Instant.now(),
                    expiresAt = expiresAt,
                    blacklistedBy = currentUser(),
                    reason = reason,
                    blacklistReason = blacklistReason,
                ),
            )
            logger.info("Token {} blacklisted for reason: {}", tokenId, reason)
        } catch (e: JwtException) {
            logger.error("Failed to blacklist token", e)
            throw InvalidTokenException("Invalid token", e)
        }
    }

    suspend fun isTokenBlacklisted(token: String): Boolean = try {
        val jwt = jwtDecoder.decode(token).awaitSingle()
        val tokenId = jwt.getClaimAsString("jti") ?: return false
        blacklistedTokenRepository.existsByTokenId(tokenId)
    } catch (_: JwtException) {
        true
    }

    suspend fun blacklistAllUserTokens(
        userId: UserId,
        reason: String,
        blacklistReason: BlacklistReason,
    ) {
        userBlacklistRepository.save(
            UserBlacklist(
                id = UserBlacklistId(0L),
                userId = userId,
                blacklistedAt = Instant.now(),
                reason = reason,
                blacklistReason = blacklistReason,
            ),
        )
    }

    @Scheduled(fixedRate = 3600000)
    suspend fun cleanupExpiredBlacklistedTokens() {
        val expired = blacklistedTokenRepository.findByExpiresAtBefore(Instant.now())
        if (expired.isNotEmpty()) {
            blacklistedTokenRepository.deleteAll(expired)
            logger.info("Cleaned up {} expired blacklisted tokens", expired.size)
        }
    }

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(TokenBlacklistingService::class.java)
    }
}
```

## Session Management

### User Session Domain Model

```kotlin
data class UserSession(
    val id: UserSessionId,
    val userId: UserId,
    val sessionId: String,
    val deviceId: String? = null,
    val deviceInfo: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val location: String? = null,
    val loginAt: Instant,
    val lastActivityAt: Instant? = null,
    val logoutAt: Instant? = null,
    val sessionTimeoutAt: Instant,
    val active: Boolean = true,
    val loginMethod: LoginMethod,
    val mfaVerified: Boolean = false,
    val riskScore: Int? = null,
) {
    fun isValid(): Boolean = active && !isExpired()
    fun isExpired(): Boolean = sessionTimeoutAt.isBefore(Instant.now())

    fun updateActivity(): UserSession = copy(
        lastActivityAt = Instant.now(),
        sessionTimeoutAt = Instant.now().plus(30, ChronoUnit.MINUTES),
    )

    fun terminate(): UserSession = copy(
        active = false,
        logoutAt = Instant.now(),
    )
}
```

### Coroutine Session Management Service

```kotlin
@com.profiletailors.common.domain.Service
class SessionManagementService(
    private val sessionRepository: SessionRepository,
    private val refreshTokenService: RefreshTokenService,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    @Value("\${security.session.max-concurrent:5}")
    private val maxConcurrentSessions: Int = 5

    @Value("\${security.session.inactivity-timeout:PT30M}")
    private val inactivityTimeout: Duration = Duration.ofMinutes(30)

    suspend fun createSession(
        user: UserAccount,
        exchange: ServerWebExchange,
        loginMethod: LoginMethod,
    ): UserSession {
        val ipAddress = extractIpAddress(exchange)
        val deviceInfo = extractDeviceInfo(exchange)
        val deviceId = generateDeviceId(exchange)

        enforceConcurrentSessionLimit(user.id)

        val session = sessionRepository.save(
            UserSession(
                id = UserSessionId(0L),
                userId = user.id,
                sessionId = UUID.randomUUID().toString(),
                deviceId = deviceId,
                deviceInfo = deviceInfo,
                ipAddress = ipAddress,
                userAgent = exchange.request.headers.header("User-Agent").firstOrNull(),
                location = lookupLocation(ipAddress),
                loginAt = Instant.now(),
                lastActivityAt = Instant.now(),
                sessionTimeoutAt = Instant.now().plus(inactivityTimeout),
                active = true,
                loginMethod = loginMethod,
                riskScore = calculateRiskScore(exchange),
            ),
        )

        applicationEventPublisher.publishEvent(UserSessionCreatedEvent(session))
        return session
    }

    suspend fun terminateSession(sessionId: String, reason: String) {
        val session = sessionRepository.findBySessionId(sessionId) ?: return
        val terminated = session.terminate()
        sessionRepository.save(terminated)
        refreshTokenService.revokeTokensBySessionId(sessionId)
        applicationEventPublisher.publishEvent(UserSessionTerminatedEvent(terminated, reason))
    }

    suspend fun terminateAllUserSessions(userId: UserId, reason: String) {
        val activeSessions = sessionRepository.findActiveByUser(userId)
        val terminated = activeSessions.map { it.terminate() }
        sessionRepository.saveAll(terminated)
        activeSessions.forEach { refreshTokenService.revokeTokensBySessionId(it.sessionId) }
        applicationEventPublisher.publishEvent(
            AllUserSessionsTerminatedEvent(userId, terminated.size, reason),
        )
    }

    private suspend fun enforceConcurrentSessionLimit(userId: UserId) {
        val activeSessions = sessionRepository.countActiveByUser(userId)
        if (activeSessions >= maxConcurrentSessions) {
            sessionRepository.findOldestActiveByUser(userId)?.let { oldest ->
                terminateSession(oldest.sessionId, "Concurrent session limit exceeded")
            }
        }
    }

    @Scheduled(fixedRate = 300000)
    suspend fun cleanupInactiveSessions() {
        val now = Instant.now()
        val inactive = sessionRepository.findActiveAndExpiredBefore(now)
        if (inactive.isEmpty()) return
        val terminated = inactive.map { it.terminate() }
        sessionRepository.saveAll(terminated)
        inactive.forEach { refreshTokenService.revokeTokensBySessionId(it.sessionId) }
        logger.info("Cleaned up {} inactive sessions", inactive.size)
    }

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(SessionManagementService::class.java)
    }
}
```

## Token Security Headers

### Reactive Security Headers Configuration

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
                        "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self'; connect-src 'self'; frame-ancestors 'none';",
                    )
                }
                .referrerPolicy { it.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN) }
        }
        .build()
}
```

### Rate Limiting for Token Endpoints

```kotlin
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authRateLimiter: RateLimiter,
) {
    @PostMapping("/login")
    @RateLimited(requests = 5, window = "PT1M")
    suspend fun login(@RequestBody request: LoginRequest): LoginResponse =
        authRateLimiter.execute { loginService.login(request) }

    @PostMapping("/refresh")
    @RateLimited(requests = 10, window = "PT1M")
    suspend fun refresh(@RequestBody request: RefreshTokenRequest): RefreshTokenResponse =
        authRateLimiter.execute { refreshTokenService.refresh(request, exchange) }
}

@Aspect
@Component
class RateLimitingAspect {
    private val bucketCache: ConcurrentMap<String, Bucket> = ConcurrentHashMap()

    @Around("@annotation(rateLimited)")
    fun around(joinPoint: ProceedingJoinPoint, rateLimited: RateLimited): Any {
        val key = generateKey(joinPoint, rateLimited)
        val bucket = bucketCache.computeIfAbsent(key) { createBucket(rateLimited) }

        return if (bucket.tryConsume(1)) {
            joinPoint.proceed()
        } else {
            throw RateLimitExceededException("Rate limit exceeded")
        }
    }

    private fun generateKey(joinPoint: ProceedingJoinPoint, rateLimited: RateLimited): String {
        val request = currentRequest()
        val clientIp = clientIpAddress(request)
        return "${joinPoint.signature.toShortString()}:$clientIp:${rateLimited.identifier}"
    }
}
```
