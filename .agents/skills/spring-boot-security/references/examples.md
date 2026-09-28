# Spring Security JWT Implementation Examples

This document shows end-to-end reactive JWT wiring for the SMP backend. All examples
use the WebFlux security stack (`ServerHttpSecurity`, `SecurityWebFilterChain`,
`WebFilter`, `R2dbcRepository`, `suspend fun`, `Mono` / `Flux`) and the
`com.profiletailors.common.domain.Service` marker for application services. The
servlet-stack version of these examples lives in
`migration-spring-security-6x.md` for historical reference only and is not used in
production code.

## Complete Application Setup

### Application Main Class

```kotlin
@SpringBootApplication
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@EnableR2dbcRepositories(basePackages = ["com.profiletailors.smp.identity.adapter.out.persistence"])
class SmpSecurityApplication {
    fun main(args: Array<String>) {
        runApplication<SmpSecurityApplication>(*args)
    }

    @Bean
    fun seedData(
        permissionRepository: PermissionR2dbcRepository,
        roleRepository: RoleR2dbcRepository,
        userRepository: UserR2dbcRepository,
        passwordEncoder: PasswordEncoder,
    ): ApplicationRunner = ApplicationRunner {
        runBlocking {
            val readPermission = permissionRepository.save(
                Permission(name = "USER_READ", description = "Read user information"),
            )
            val writePermission = permissionRepository.save(
                Permission(name = "USER_WRITE", description = "Write user information"),
            )
            val deletePermission = permissionRepository.save(
                Permission(name = "USER_DELETE", description = "Delete user information"),
            )
            val adminPermission = permissionRepository.save(
                Permission(name = "ADMIN", description = "Full administrative access"),
            )

            val userRole = roleRepository.save(Role(name = "USER"))
            val adminRole = roleRepository.save(Role(name = "ADMIN"))
            val managerRole = roleRepository.save(Role(name = "MANAGER"))

            userRole.addPermissions(setOf(readPermission))
            managerRole.addPermissions(setOf(readPermission, writePermission))
            adminRole.addPermissions(
                setOf(readPermission, writePermission, deletePermission, adminPermission),
            )

            roleRepository.saveAll(listOf(userRole, adminRole, managerRole)).collectList().awaitSingle()

            val user = UserAccount(
                email = "user@profiletailors.com",
                password = passwordEncoder.encode("password"),
                firstName = "Seed",
                lastName = "User",
                enabled = true,
            )
            user.assignRoles(setOf(userRole))
            userRepository.save(user).awaitSingle()

            val admin = UserAccount(
                email = "admin@profiletailors.com",
                password = passwordEncoder.encode("admin"),
                firstName = "Seed",
                lastName = "Admin",
                enabled = true,
            )
            admin.assignRoles(setOf(adminRole))
            userRepository.save(admin).awaitSingle()

            val manager = UserAccount(
                email = "manager@profiletailors.com",
                password = passwordEncoder.encode("manager"),
                firstName = "Seed",
                lastName = "Manager",
                enabled = true,
            )
            manager.assignRoles(setOf(managerRole))
            userRepository.save(manager).awaitSingle()
        }
    }
}
```

### Domain Models (Identity Bounded Context)

```kotlin
package com.profiletailors.smp.identity.domain

data class UserAccount(
    val id: UserId,
    val email: String,
    val passwordHash: String,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String? = null,
    val enabled: Boolean = true,
    val emailVerified: Boolean = false,
    val roles: MutableSet<Role> = mutableSetOf(),
    val refreshTokens: MutableList<RefreshToken> = mutableListOf(),
    val logins: MutableList<UserLogin> = mutableListOf(),
) {
    fun assignRoles(newRoles: Set<Role>) {
        roles.clear()
        roles.addAll(newRoles)
    }

    fun authorities(): List<SimpleGrantedAuthority> = roles.flatMap { role ->
        listOf(SimpleGrantedAuthority("ROLE_${role.name}")) +
            role.permissions.map { SimpleGrantedAuthority(it.name) }
    }

    fun hasAuthority(authority: String): Boolean =
        authorities().any { it.authority == authority }

    fun hasRole(roleName: String): Boolean = roles.any { it.name == roleName }
}

data class Role(
    val id: RoleId,
    val name: String,
    val description: String,
    val permissions: MutableSet<Permission> = mutableSetOf(),
) {
    fun addPermissions(perms: Set<Permission>) {
        permissions.addAll(perms)
    }
}

data class Permission(
    val id: PermissionId,
    val name: String,
    val description: String,
    val resourceType: String,
)

data class RefreshToken(
    val id: RefreshTokenId,
    val userId: UserId,
    val tokenHash: String,
    val expiresAt: Instant,
    val revoked: Boolean = false,
    val revokedAt: Instant? = null,
) {
    fun isExpired(): Boolean = expiresAt.isBefore(Instant.now())
    fun isActive(): Boolean = !revoked && !isExpired()
}

data class UserLogin(
    val id: UserLoginId,
    val userId: UserId,
    val loginAt: Instant,
    val logoutAt: Instant? = null,
    val ipAddress: String,
    val userAgent: String,
)
```

The repository ports live in `domain`; the R2DBC adapters live in `infrastructure`:

```kotlin
package com.profiletailors.smp.identity.domain

interface UserRepository {
    suspend fun findById(id: UserId): UserAccount?
    suspend fun findByEmail(email: String): UserAccount?
    suspend fun save(user: UserAccount): UserAccount
    suspend fun existsByEmail(email: String): Boolean
}

interface RoleRepository {
    suspend fun findByName(name: String): Role?
    suspend fun save(role: Role): Role
}

interface RefreshTokenRepository {
    suspend fun findByTokenHash(tokenHash: String): RefreshToken?
    suspend fun findByTokenId(tokenId: String): RefreshToken?
    suspend fun save(token: RefreshToken): RefreshToken
    suspend fun delete(token: RefreshToken)
    suspend fun countActiveByUser(userId: UserId): Long
    suspend fun deleteOldestByUser(userId: UserId)
    suspend fun findAllActiveByUser(userId: UserId): List<RefreshToken>
    suspend fun findExpiredBefore(cutoff: Instant): List<RefreshToken>
    suspend fun deleteAll(tokens: Collection<RefreshToken>)
}
```

## Authentication Controller

### Complete Reactive Auth Controller

```kotlin
package com.profiletailors.smp.identity.adapter.in.web

@RestController
@RequestMapping("/api/auth")
@Validated
class AuthController(
    private val authenticationManager: ReactiveAuthenticationManager,
    private val tokenService: JwtTokenService,
    private val refreshTokenService: RefreshTokenService,
    private val userService: UserService,
    private val eventPublisher: AuthenticationEventPublisher,
) {
    @PostMapping("/login")
    suspend fun login(
        @Valid @RequestBody request: LoginRequest,
        exchange: ServerWebExchange,
    ): ResponseEntity<LoginResponse> {
        val authentication = UsernamePasswordAuthenticationToken(request.email, request.password)
        val authenticated = authenticationManager.authenticate(authentication).awaitSingle()

        val user = authenticated.principal as UserAccount

        val accessToken = tokenService.generateAccessToken(user)
        val refreshToken = refreshTokenService.createRefreshToken(user)

        val deviceInfo = extractDeviceInfo(exchange)
        val ipAddress = extractIpAddress(exchange)
        userService.recordLogin(user, deviceInfo, ipAddress)
        eventPublisher.publishAuthenticationSuccess(user, exchange)

        val response = LoginResponse(
            accessToken = accessToken.token,
            expiresAt = accessToken.expiresAt,
            refreshToken = refreshToken.token,
            refreshExpiresAt = refreshToken.expiresAt,
            userId = user.id.value,
            email = user.email,
            fullName = user.fullName(),
            authorities = user.authorities().map { it.authority },
        )

        return ResponseEntity.ok()
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${accessToken.token}")
            .body(response)
    }

    @PostMapping("/refresh")
    suspend fun refreshToken(@Valid @RequestBody request: RefreshTokenRequest): RefreshTokenResponse =
        refreshTokenService.refreshToken(request)

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    suspend fun logout(
        @RequestHeader(value = "Authorization", required = false) authorization: String?,
        exchange: ServerWebExchange,
    ): MessageResponse {
        val token = extractTokenFromHeader(authorization)
        val jti = tokenService.extractTokenClaim(token, "jti")
        refreshTokenService.revokeRefreshTokenByJti(jti)

        val auth = exchange.exchange.getPrincipal<Authentication>().awaitSingle()
        val user = auth.principal as UserAccount
        userService.recordLogout(user, extractIpAddress(exchange))

        return MessageResponse("Logged out successfully")
    }

    @PostMapping("/logout-all")
    @PreAuthorize("isAuthenticated()")
    suspend fun logoutAll(authentication: Authentication): MessageResponse {
        val user = authentication.principal as UserAccount
        refreshTokenService.revokeAllRefreshTokens(user)
        return MessageResponse("Logged out from all devices")
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    suspend fun getCurrentUser(authentication: Authentication): UserProfileResponse {
        val user = authentication.principal as UserAccount
        return UserProfileResponse(
            id = user.id.value,
            email = user.email,
            fullName = user.fullName(),
            phoneNumber = user.phoneNumber,
            roles = user.roles.map { it.name }.toSet(),
            authorities = user.authorities().map { it.authority }.toSet(),
        )
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    suspend fun changePassword(
        @Valid @RequestBody request: ChangePasswordRequest,
        authentication: Authentication,
    ): MessageResponse {
        val user = authentication.principal as UserAccount
        userService.changePassword(user, request)
        refreshTokenService.revokeAllRefreshTokensExceptCurrent(user, request.currentPassword)
        return MessageResponse("Password changed successfully")
    }

    private fun extractTokenFromHeader(authorization: String?): String {
        require(authorization != null && authorization.startsWith("Bearer ")) {
            "Invalid authorization header"
        }
        return authorization.substring(7)
    }

    private fun extractDeviceInfo(exchange: ServerWebExchange): String =
        exchange.request.headers.header("User-Agent").firstOrNull().orEmpty()

    private fun extractIpAddress(exchange: ServerWebExchange): String {
        val forwarded = exchange.request.headers.header("X-Forwarded-For").firstOrNull()
        if (!forwarded.isNullOrBlank()) return forwarded.split(",")[0].trim()
        return exchange.request.remoteAddress?.address?.hostAddress.orEmpty()
    }
}
```

### Reactive Registration Controller

```kotlin
@RestController
@RequestMapping("/api/register")
@Validated
class RegistrationController(
    private val userService: UserService,
    private val emailService: EmailService,
) {
    @PostMapping
    suspend fun register(
        @Valid @RequestBody request: RegistrationRequest,
        uriBuilder: UriComponentsBuilder,
    ): ResponseEntity<MessageResponse> {
        if (userService.existsByEmail(request.email)) {
            throw UserAlreadyExistsException("Email already registered")
        }

        val user = userService.createUser(request)

        val verificationToken = userService.generateEmailVerificationToken(user)
        emailService.sendVerificationEmail(user, verificationToken)

        val location: URI = uriBuilder.path("/api/users/{id}")
            .buildAndExpand(user.id.value)
            .toUri()

        return ResponseEntity.created(location)
            .body(MessageResponse("User registered successfully. Please check your email for verification."))
    }

    @PostMapping("/verify-email")
    suspend fun verifyEmail(@Valid @RequestBody request: EmailVerificationRequest): MessageResponse {
        userService.verifyEmail(request.token)
        return MessageResponse("Email verified successfully")
    }

    @PostMapping("/resend-verification")
    suspend fun resendVerification(@Valid @RequestBody request: ResendVerificationRequest): MessageResponse {
        val user = userService.findByEmail(request.email)
            ?: throw UserNotFoundException("User not found")

        if (user.emailVerified) {
            throw EmailAlreadyVerifiedException("Email already verified")
        }

        val verificationToken = userService.generateEmailVerificationToken(user)
        emailService.sendVerificationEmail(user, verificationToken)

        return MessageResponse("Verification email sent")
    }
}
```

## Application Service Implementation

### JWT Token Application Service

```kotlin
@com.profiletailors.common.domain.Service
class JwtTokenService(
    private val jwtEncoder: JwtEncoder,
    private val jwtDecoder: ReactiveJwtDecoder,
    private val claimsService: JwtClaimsService,
    private val blacklistedTokenRepository: BlacklistedTokenRepository,
) {
    suspend fun generateAccessToken(user: UserAccount): AccessTokenResponse {
        val claims = claimsService.createAccessTokenClaims(user)
        val tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue

        return AccessTokenResponse(
            token = tokenValue,
            expiresAt = claims.expiresAt.toEpochMilli(),
            issuedAt = claims.issuedAt.toEpochMilli(),
            type = claims.getClaimAsString("type"),
        )
    }

    suspend fun extractTokenClaim(token: String, claimName: String): String {
        val jwt = jwtDecoder.decode(token).awaitSingle()
        return jwt.getClaimAsString(claimName)
            ?: throw InvalidTokenException("Missing claim $claimName")
    }

    suspend fun isTokenValid(token: String): Boolean {
        val jti = extractTokenClaim(token, "jti")
        if (blacklistedTokenRepository.existsByTokenId(jti)) return false

        val jwt = jwtDecoder.decode(token).awaitSingle()
        val expiresAt = jwt.expiresAt ?: return false
        return expiresAt.isAfter(Instant.now())
    }

    suspend fun blacklistToken(token: String) {
        val jti = extractTokenClaim(token, "jti")
        val expiresAtMs = extractTokenClaim(token, "exp").toLong()
        val expiresAt = Instant.ofEpochMilli(expiresAtMs)

        blacklistedTokenRepository.save(
            BlacklistedToken(jti = jti, token = token, expiresAt = expiresAt),
        )
    }

    @Scheduled(fixedRate = 3600000)
    suspend fun cleanupExpiredBlacklistedTokens() {
        val expired = blacklistedTokenRepository.findByExpiresAtBefore(Instant.now())
        blacklistedTokenRepository.deleteAll(expired)
    }
}
```

### Refresh Token Service

```kotlin
@com.profiletailors.common.domain.Service
class RefreshTokenService(
    private val jwtTokenService: JwtTokenService,
    private val claimsService: JwtClaimsService,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userRepository: UserRepository,
) {
    @Value("\${jwt.refresh-token-expiration:P7D}")
    private val refreshTokenExpiration: Duration = Duration.ofDays(7)

    suspend fun createRefreshToken(user: UserAccount): RefreshTokenResponse {
        val active = refreshTokenRepository.countActiveByUser(user.id)
        if (active >= MAX_ACTIVE_REFRESH_TOKENS) {
            refreshTokenRepository.deleteOldestByUser(user.id)
        }

        val claims = claimsService.createRefreshTokenClaims(user)
        val tokenValue = jwtTokenService.encodeClaims(claims)

        val saved = refreshTokenRepository.save(
            RefreshToken(
                id = RefreshTokenId(0L),
                userId = user.id,
                tokenHash = hashToken(tokenValue),
                expiresAt = claims.expiresAt,
                sessionId = claims.getClaimAsString("sessionId"),
                tokenId = claims.getClaimAsString("jti"),
            ),
        )

        return RefreshTokenResponse(
            token = tokenValue,
            expiresAt = saved.expiresAt.toEpochMilli(),
        )
    }

    suspend fun refreshToken(request: RefreshTokenRequest): RefreshTokenResponse {
        val refreshToken = refreshTokenRepository.findByTokenHash(hashToken(request.refreshToken))
            ?: throw InvalidTokenException("Refresh token not found")

        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken)
            throw ExpiredTokenException("Refresh token expired")
        }
        if (!refreshToken.isActive()) {
            throw InvalidTokenException("Refresh token has been revoked")
        }

        val user = userRepository.findById(refreshToken.userId)
            ?: throw AccountNotFoundException("User not found")

        if (!user.enabled) {
            throw AccountDisabledException("Account is disabled")
        }

        val accessToken = jwtTokenService.generateAccessToken(user)

        return if (shouldRotate(refreshToken)) {
            refreshTokenRepository.delete(refreshToken)
            createRefreshToken(user)
        } else {
            RefreshTokenResponse(
                token = request.refreshToken,
                expiresAt = refreshToken.expiresAt.toEpochMilli(),
                accessToken = accessToken.token,
                accessExpiresAt = accessToken.expiresAt,
            )
        }
    }

    suspend fun revokeRefreshToken(token: String) {
        val refreshToken = refreshTokenRepository.findByTokenHash(hashToken(token)) ?: return
        refreshTokenRepository.save(refreshToken.revoke(now()))
    }

    suspend fun revokeRefreshTokenByJti(jti: String) {
        val refreshToken = refreshTokenRepository.findByTokenId(jti) ?: return
        refreshTokenRepository.save(refreshToken.revoke(now()))
    }

    suspend fun revokeAllRefreshTokens(user: UserAccount) {
        val tokens = refreshTokenRepository.findAllActiveByUser(user.id)
        tokens.forEach { refreshTokenRepository.save(it.revoke(now())) }
    }

    private fun shouldRotate(token: RefreshToken): Boolean =
        token.createdAt.isBefore(Instant.now().minus(3, ChronoUnit.DAYS))

    @Scheduled(fixedRate = 86400000)
    suspend fun cleanupExpiredTokens() {
        val cutoff = Instant.now().minus(7, ChronoUnit.DAYS)
        val expired = refreshTokenRepository.findExpiredBefore(cutoff)
        refreshTokenRepository.deleteAll(expired)
    }

    companion object {
        private const val MAX_ACTIVE_REFRESH_TOKENS = 5L
    }
}
```

### User Application Service

```kotlin
@com.profiletailors.common.domain.Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val roleRepository: RoleRepository,
) {
    suspend fun createUser(request: RegistrationRequest): UserAccount {
        val userRole = roleRepository.findByName("USER")
            ?: throw IllegalStateException("Default USER role not found")

        val user = UserAccount(
            id = UserId(0L),
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password),
            firstName = request.firstName,
            lastName = request.lastName,
            phoneNumber = request.phoneNumber,
            enabled = true,
        )
        user.assignRoles(setOf(userRole))

        return userRepository.save(user)
    }

    suspend fun changePassword(user: UserAccount, request: ChangePasswordRequest) {
        if (!passwordEncoder.matches(request.currentPassword, user.passwordHash)) {
            throw InvalidPasswordException("Current password is incorrect")
        }
        if (request.newPassword != request.confirmPassword) {
            throw PasswordMismatchException("New passwords do not match")
        }
        val updated = user.copy(passwordHash = passwordEncoder.encode(request.newPassword))
        userRepository.save(updated)
    }

    suspend fun recordLogin(user: UserAccount, deviceInfo: String, ipAddress: String) {
        val login = UserLogin(
            id = UserLoginId(0L),
            userId = user.id,
            loginAt = Instant.now(),
            ipAddress = ipAddress,
            userAgent = deviceInfo,
        )
        user.logins.add(login)
        userRepository.save(user)
    }

    suspend fun recordLogout(user: UserAccount, ipAddress: String) {
        val updated = user.copy(logins = user.logins.toMutableList())
        val lastOpenLogin = updated.logins.lastOrNull { it.logoutAt == null }
        if (lastOpenLogin != null) {
            val closed = lastOpenLogin.copy(
                logoutAt = Instant.now(),
                logoutIpAddress = ipAddress,
            )
            updated.logins.remove(lastOpenLogin)
            updated.logins.add(closed)
            userRepository.save(updated)
        }
    }

    suspend fun generateEmailVerificationToken(user: UserAccount): String {
        val token = UUID.randomUUID().toString()
        val expiry = Instant.now().plus(24, ChronoUnit.HOURS)
        userRepository.save(user.copy(emailVerificationToken = token, emailVerificationTokenExpiry = expiry))
        return token
    }

    suspend fun verifyEmail(token: String): UserAccount {
        val user = userRepository.findByEmailVerificationToken(token)
            ?: throw InvalidTokenException("Invalid verification token")

        val expiry = user.emailVerificationTokenExpiry
            ?: throw ExpiredTokenException("Verification token expired")

        if (expiry.isBefore(Instant.now())) {
            throw ExpiredTokenException("Verification token expired")
        }

        return userRepository.save(
            user.copy(
                emailVerified = true,
                emailVerificationToken = null,
                emailVerificationTokenExpiry = null,
            ),
        )
    }
}
```

## Reactive Security Configuration

### Complete Security Web Filter Chain

```kotlin
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
class SecurityConfig(
    private val authenticationEntryPoint: JwtAuthenticationEntryPoint,
    private val accessDeniedHandler: JwtAccessDeniedHandler,
    private val jwtAuthenticationFilter: JwtAuthenticationWebFilter,
    private val authenticationManager: ReactiveAuthenticationManager,
    private val logoutHandler: ServerLogoutHandler,
) {
    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain = http
        .csrf { csrf ->
            csrf
                .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers("/api/auth/**", "/api/public/**")
        }
        .anonymous { it.disable() }
        .exceptionHandling { exceptions ->
            exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
        }
        .headers { headers ->
            headers
                .frameOptions { it.deny() }
                .contentTypeOptions { it.and() }
                .hsts { hsts ->
                    hsts
                        .maxAge(Duration.ofDays(365))
                        .includeSubdomains(true)
                }
                .cache { it.disable() }
        }
        .authorizeExchange { auth ->
            auth
                .pathMatchers("/api/auth/**", "/api/public/**", "/actuator/health").permitAll()
                .pathMatchers("/api/admin/**").hasRole("ADMIN")
                .pathMatchers("/api/manager/**").hasAnyRole("MANAGER", "ADMIN")
                .pathMatchers("/api/users/me").authenticated()
                .anyExchange().authenticated()
        }
        .oauth2ResourceServer { oauth2 ->
            oauth2.jwt { jwt ->
                jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
            }
        }
        .authenticationManager(authenticationManager)
        .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.HTTP_BASIC)
        .logout { logout ->
            logout
                .logoutUrl("/api/auth/logout")
                .logoutHandler(logoutHandler)
                .logoutSuccessHandler { _, response ->
                    response.setStatusCode(HttpStatus.NO_CONTENT)
                }
        }
        .build()

    @Bean
    fun jwtDecoder(): ReactiveJwtDecoder = CustomReactiveJwtDecoder(
        NimbusReactiveJwtDecoder.withPublicKey(rsaPublicKey()).build(),
        jwtClaimsValidator(),
    )

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
