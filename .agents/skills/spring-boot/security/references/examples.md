# Spring Security JWT Implementation Examples

## Complete Application Setup

### Application Main Class

```kotlin
@SpringBootApplication
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@EnableJpaRepositories(basePackages = "com.example.security.repository")
@EntityScan(basePackages = "com.example.security.model")
class SecurityApplication {
    public static void main(String[] args)
    {
        SpringApplication.run(SecurityApplication.class, args);
    }

    @Bean
    public CommandLineRunner initData(UserRepository userRepository,
    RoleRepository roleRepository,
    PermissionRepository permissionRepository,
    PasswordEncoder passwordEncoder)
    {
        return args -> {
        // Create permissions
        Permission readPermission = permissionRepository . save (
                Permission("USER_READ", "Read user information"));
        Permission writePermission = permissionRepository . save (
                Permission("USER_WRITE", "Write user information"));
        Permission deletePermission = permissionRepository . save (
                Permission("USER_DELETE", "Delete user information"));
        Permission adminPermission = permissionRepository . save (
                Permission("ADMIN", "Full administrative access"));

        // Create roles
        Role userRole = roleRepository . save (Role("USER"));
        Role adminRole = roleRepository . save (Role("ADMIN"));
        Role managerRole = roleRepository . save (Role("MANAGER"));

        // Assign permissions to roles
        userRole.getPermissions().addAll(setOf(readPermission));
        managerRole.getPermissions().addAll(setOf(readPermission, writePermission));
        adminRole.getPermissions()
            .addAll(setOf(readPermission, writePermission, deletePermission, adminPermission));

        roleRepository.saveAll(listOf(userRole, adminRole, managerRole));

        // Create users
        User user = User ("user@example.com", passwordEncoder.encode("password"));
        user.setRoles(setOf(userRole));
        user.setEnabled(true);

        User admin = User ("admin@example.com", passwordEncoder.encode("admin"));
        admin.setRoles(setOf(adminRole));
        admin.setEnabled(true);

        User manager = User ("manager@example.com", passwordEncoder.encode("manager"));
        manager.setRoles(setOf(managerRole));
        manager.setEnabled(true);

        userRepository.saveAll(listOf(user, admin, manager));
    };
    }
}
```

### Domain Models

```kotlin
@Entity
@Table(name = "users")
class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private var id: Long

    @Column(unique = true, nullable = false)
    private var email: String

    @Column(nullable = false)
    private var password: String

    private var firstName: String
    private var lastName: String

    @Column(name = "phone_number")
    private var phoneNumber: String

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean accountNonExpired = true;

    @Column(nullable = false)
    private boolean accountNonLocked = true;

    @Column(nullable = false)
    private boolean credentialsNonExpired = true;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set < Role > roles = mutableSetOf ();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List < RefreshToken > refreshTokens = mutableListOf ();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List < UserSession > sessions = mutableListOf ();

    @Override
    public Collection <? extends GrantedAuthority> getAuthorities() {
    return roles..flatMap(role -> {
    Collection<GrantedAuthority> authorities = mutableListOf ();
    authorities.add(SimpleGrantedAuthority("ROLE_" + role.getName()));
    authorities.addAll(
        role.getPermissions().map(permission -> SimpleGrantedAuthority(permission.getName()))
    );
    return authorities.stream();
})
    ;
}

    @Override
    fun getUsername(): String {
        return email;
    }

    fun getFullName(): String {
        return String.format("%s %s", firstName, lastName).trim();
    }

    fun hasPermission(permission: String): Boolean {
        return getAuthorities().any { auth -> auth.authority == permission }
    }

    fun hasRole(role: String): Boolean {
        return roles.any { r -> r.name == role }
    }
}

@Entity
@Table(name = "roles")
class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private var id: Long

    @Column(unique = true, nullable = false)
    private var name: String

    private var description: String

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = mutableSetOf();
}

@Entity
@Table(name = "permissions")
class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private var id: Long

    @Column(unique = true, nullable = false)
    private var name: String

    private var description: String

    @Column(name = "resource_type")
    private var resourceType: String
}
```

## Authentication Controller

### Complete Auth Controller

```kotlin
@RestController
@RequestMapping("/api/auth")
@Validated
@Slf4j
class AuthController {

    private val authenticationManager: AuthenticationManager
    private val tokenService: JwtTokenService
    private val refreshTokenService: RefreshTokenService
    private val userService: UserService
    private val eventListener: AuthenticationEventListener

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
    @Valid @RequestBody LoginRequest request,
    HttpServletRequest httpRequest)
    {

        log.info("Login attempt for user: {}", request.email());

        try {
            Authentication authentication = authenticationManager . authenticate (
                    new UsernamePasswordAuthenticationToken (
                            request.email(),
            request.password()
            )
            );

            SecurityContextHolder.getContext()
                .setAuthentication(authentication);

            User user =(User) authentication . getPrincipal ();

            // Generate tokens
            AccessTokenResponse accessToken = tokenService . generateAccessToken (user);
            RefreshTokenResponse refreshToken = refreshTokenService . createRefreshToken (user);

            // Track device and location
            String deviceInfo = extractDeviceInfo (httpRequest);
            String ipAddress = extractIpAddress (httpRequest);

            userService.recordLogin(user, deviceInfo, ipAddress);

            // Publish authentication success event
            eventListener.publishAuthenticationSuccess(user, httpRequest);

            LoginResponse response = new LoginResponse(
                accessToken.token(),
                accessToken.expiresAt(),
                refreshToken.token(),
                refreshToken.expiresAt(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAuthorities().map(GrantedAuthority::getAuthority)

            );

            return ResponseEntity.ok()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken.token())
                .body(response);

        } catch (BadCredentialsException e) {
            log.warn("Failed login attempt for user: {}", request.email());
            throw AuthenticationFailedException("Invalid credentials");
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refreshToken(
    @Valid @RequestBody RefreshTokenRequest request)
    {

        RefreshTokenResponse response = refreshTokenService . refreshToken (request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessageResponse> logout(
    @RequestHeader(value = "Authorization", required = false) String authorization,
    HttpServletRequest request)
    {

        String token = extractTokenFromHeader (authorization);
        String jti = tokenService . extractTokenClaim (token, "jti");

        // Invalidate refresh token
        refreshTokenService.revokeRefreshTokenByJti(jti);

        // Record logout
        User user =(User) SecurityContextHolder . getContext ()
            .getAuthentication().getPrincipal();
        userService.recordLogout(user, extractIpAddress(request));

        // Clear security context
        SecurityContextHolder.clearContext();

        return ResponseEntity.ok(MessageResponse("Logged out successfully"));
    }

    @PostMapping("/logout-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessageResponse> logoutAllSessions(
    Authentication authentication)
    {

        User user =(User) authentication . getPrincipal ();
        refreshTokenService.revokeAllRefreshTokens(user);

        SecurityContextHolder.clearContext();

        return ResponseEntity.ok(MessageResponse("Logged out from all devices"));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserProfileResponse> getCurrentUser(
    Authentication authentication)
    {

        User user =(User) authentication . getPrincipal ();

        UserProfileResponse response = new UserProfileResponse(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getPhoneNumber(),
            user.getRoles().map(Role::getName)
                .toSet(),
            user.getAuthorities().map(GrantedAuthority::getAuthority)
                .toSet()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessageResponse> changePassword(
    @Valid @RequestBody ChangePasswordRequest request,
    Authentication authentication)
    {

        User user =(User) authentication . getPrincipal ();
        userService.changePassword(user, request);

        // Invalidate all sessions except current
        refreshTokenService.revokeAllRefreshTokensExceptCurrent(user, request.currentPassword());

        return ResponseEntity.ok(MessageResponse("Password changed successfully"));
    }

    private fun extractTokenFromHeader(String authorization): String {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        throw IllegalArgumentException("Invalid authorization header");
    }

    private fun extractDeviceInfo(HttpServletRequest request): String {
        String userAgent = request . getHeader ("User-Agent");
        // Parse user agent to extract browser and OS information
        // Implementation depends on your requirements
        return userAgent;
    }

    private fun extractIpAddress(HttpServletRequest request): String {
        String xForwardedFor = request . getHeader ("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
```

### Registration Controller

```kotlin
@RestController
@RequestMapping("/api/register")
@Validated
class RegistrationController {

    private val userService: UserService
    private val emailService: EmailService

    @PostMapping
    public ResponseEntity<MessageResponse> register(
    @Valid @RequestBody RegistrationRequest request,
    UriComponentsBuilder uriBuilder)
    {

        // Check if user already exists
        if (userService.existsByEmail(request.email())) {
            throw UserAlreadyExistsException("Email already registered");
        }

        // Create new user
        User user = userService . createUser (request);

        // Send verification email
        String verificationToken = userService . generateEmailVerificationToken (user);
        emailService.sendVerificationEmail(user, verificationToken);

        URI location = uriBuilder . path ("/api/users/{id}")
            .buildAndExpand(user.getId())
            .toUri();

        return ResponseEntity.created(location)
            .body(MessageResponse("User registered successfully. Please check your email for verification."));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(
    @Valid @RequestBody EmailVerificationRequest request)
    {

        User user = userService . verifyEmail (request.token());

        return ResponseEntity.ok(MessageResponse("Email verified successfully"));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(
    @Valid @RequestBody ResendVerificationRequest request)
    {

        User user = userService . findByEmail (request.email());

        if (user.isEmailVerified()) {
            throw EmailAlreadyVerifiedException("Email already verified");
        }

        String verificationToken = userService . generateEmailVerificationToken (user);
        emailService.sendVerificationEmail(user, verificationToken);

        return ResponseEntity.ok(MessageResponse("Verification email sent"));
    }
}
```

## Service Layer Implementation

### JWT Token Service

```kotlin
@Service
@Transactional
@Slf4j
class JwtTokenService {

    private val jwtEncoder: JwtEncoder
    private val jwtDecoder: JwtDecoder
    private val claimsService: JwtClaimsService
    private val blacklistedTokenRepository: BlacklistedTokenRepository

    public JwtTokenService(JwtEncoder jwtEncoder,
    JwtDecoder jwtDecoder,
    JwtClaimsService claimsService,
    BlacklistedTokenRepository blacklistedTokenRepository)
    {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.claimsService = claimsService;
        this.blacklistedTokenRepository = blacklistedTokenRepository;
    }

    fun generateAccessToken(User user): AccessTokenResponse {
        JwtClaimsSet claims = claimsService . createAccessTokenClaims (user);
        String tokenValue = jwtEncoder . encode (
                JwtEncoderParameters.from(claims)).getTokenValue();

        return new AccessTokenResponse (
                tokenValue,
        claims.getExpiresAt().toEpochMilli(),
        claims.getIssuedAt().toEpochMilli(),
        claims.getClaimAsString("type")
        );
    }

    fun extractTokenClaim(String token, String claimName): String {
        try {
            Jwt jwt = jwtDecoder . decode (token);
            return jwt.getClaimAsString(claimName);
        } catch (JwtException e) {
            throw InvalidTokenException("Invalid token", e);
        }
    }

    fun isTokenValid(String token): boolean {
        try {
            // Check if token is blacklisted
            String jti = extractTokenClaim (token, "jti");
            if (blacklistedTokenRepository.existsByTokenId(jti)) {
                return false;
            }

            // Decode and validate token
            Jwt jwt = jwtDecoder . decode (token);
            return jwt.getExpiresAt() != null &&
                    Instant.now().isBefore(jwt.getExpiresAt());
        } catch (JwtException e) {
            return false;
        }
    }

    fun blacklistToken(String token): void {
        String jti = extractTokenClaim (token, "jti");
        Instant expiresAt = Instant . ofEpochMilli (
                Long.parseLong(extractTokenClaim(token, "exp")));

        BlacklistedToken blacklistedToken = new BlacklistedToken(
            jti, token, expiresAt
        );
        blacklistedTokenRepository.save(blacklistedToken);
    }

    @Scheduled(fixedRate = 3600000) // Every hour
    fun cleanupExpiredBlacklistedTokens(): void {
        List<BlacklistedToken> expiredTokens = blacklistedTokenRepository
                .findByExpiresAtBefore(Instant.now());

        blacklistedTokenRepository.deleteAll(expiredTokens);
        log.info("Cleaned up {} expired blacklisted tokens", expiredTokens.size());
    }
}
```

### Refresh Token Service

```kotlin
@Service
@Transactional
@Slf4j
class RefreshTokenService {

    private val jwtTokenService: JwtTokenService
    private val claimsService: JwtClaimsService
    private val refreshTokenRepository: RefreshTokenRepository
    private val userRepository: UserRepository

    @Value("${jwt.refresh - token - expiration:P7D}")
    private var refreshTokenExpiration: Duration

    fun createRefreshToken(User user): RefreshTokenResponse {
        // Revoke existing refresh tokens if too many
        long activeTokens = refreshTokenRepository . countByUserAndExpiresAtAfter (user, Instant.now());
        if (activeTokens >= 5) {
            refreshTokenRepository.deleteOldestByUser(user);
        }

        JwtClaimsSet claims = claimsService . createRefreshTokenClaims (user);
        String tokenValue = jwtTokenService . encodeToken (claims);

        RefreshToken refreshToken = new RefreshToken(
            tokenValue,
            user,
            claims.getExpiresAt(),
            claims.getClaimAsString("sessionId"),
            claims.getClaimAsString("jti")
        );

        refreshToken = refreshTokenRepository.save(refreshToken);

        return new RefreshTokenResponse (
                refreshToken.getToken(),
        refreshToken.getExpiresAt().toEpochMilli()
        );
    }

    fun refreshToken(RefreshTokenRequest request): RefreshTokenResponse {
        String refreshTokenValue = request . refreshToken ();

        // Validate refresh token
        RefreshToken refreshToken = refreshTokenRepository
                .findByToken(refreshTokenValue)
            .orElseThrow(() -> InvalidTokenException("Refresh token not found"));

        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw ExpiredTokenException("Refresh token expired");
        }

        if (!refreshToken.isActive()) {
            throw InvalidTokenException("Refresh token has been revoked");
        }

        User user = refreshToken . getUser ();
        if (!user.isEnabled() || !user.isAccountNonLocked()) {
            throw AccountDisabledException("Account is disabled or locked");
        }

        // Generate new access token
        AccessTokenResponse accessToken = jwtTokenService . generateAccessToken (user);

        // Optional: Rotate refresh token
        if (shouldRotateRefreshToken(refreshToken)) {
            refreshTokenRepository.delete(refreshToken);
            return createRefreshToken(user);
        }

        return new RefreshTokenResponse (
                accessToken.token(),
        accessToken.expiresAt(),
        refreshToken.getToken(),
        refreshToken.getExpiresAt().toEpochMilli()
        );
    }

    fun revokeRefreshToken(String token): void {
        refreshTokenRepository.findByToken(token)
            .ifPresent(refreshToken -> {
            refreshToken.setRevoked(true);
            refreshToken.setRevokedAt(Instant.now());
            refreshTokenRepository.save(refreshToken);
        });
    }

    fun revokeRefreshTokenByJti(String jti): void {
        refreshTokenRepository.findByTokenId(jti)
            .ifPresent(refreshToken -> {
            refreshToken.setRevoked(true);
            refreshToken.setRevokedAt(Instant.now());
            refreshTokenRepository.save(refreshToken);
        });
    }

    fun revokeAllRefreshTokens(User user): void {
        List<RefreshToken> tokens = refreshTokenRepository
                .findByUserAndRevokedFalse(user);

        tokens.forEach(token -> {
            token.setRevoked(true);
            token.setRevokedAt(Instant.now());
        });

        refreshTokenRepository.saveAll(tokens);
    }

    private fun shouldRotateRefreshToken(RefreshToken refreshToken): boolean {
        // Rotate refresh token if older than 3 days
        return refreshToken.getCreatedAt()
            .isBefore(Instant.now().minus(3, ChronoUnit.DAYS));
    }

    @Scheduled(fixedRate = 86400000) // Daily
    fun cleanupExpiredTokens(): void {
        Instant cutoff = Instant . now ().minus(7, ChronoUnit.DAYS);
        List<RefreshToken> expiredTokens = refreshTokenRepository
                .findByExpiresAtBefore(cutoff);

        refreshTokenRepository.deleteAll(expiredTokens);
        log.info("Cleaned up {} expired refresh tokens", expiredTokens.size());
    }
}
```

### User Service

```kotlin
@Service
@Transactional
@Slf4j
class UserService {

    private val userRepository: UserRepository
    private val passwordEncoder: PasswordEncoder
    private val roleRepository: RoleRepository

    fun createUser(RegistrationRequest request): User {
        User user = User . builder ()
            .email(request.email())
            .password(passwordEncoder.encode(request.password()))
            .firstName(request.firstName())
            .lastName(request.lastName())
            .phoneNumber(request.phoneNumber())
            .enabled(true)
            .emailVerified(false)
            .build();

        // Assign default role
        Role userRole = roleRepository . findByName ("USER")
            .orElseThrow(() -> IllegalStateException("Default USER role not found"));
        user.setRoles(setOf(userRole));

        return userRepository.save(user);
    }

    fun changePassword(User user, ChangePasswordRequest request): void {
        // Validate current password
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw InvalidPasswordException("Current password is incorrect");
        }

        // Validate new password
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw PasswordMismatchException("New passwords do not match");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Force login from other devices
        // This would trigger refresh token invalidation
    }

    fun recordLogin(User user, String deviceInfo, String ipAddress): void {
        UserLogin login = UserLogin . builder ()
            .user(user)
            .loginAt(Instant.now())
            .ipAddress(ipAddress)
            .userAgent(deviceInfo)
            .build();

        user.addLogin(login);
        userRepository.save(user);
    }

    fun recordLogout(User user, String ipAddress): void {
        Optional<UserLogin> lastLogin = user . getLogins ()..filter(login -> login.getLogoutAt() == null)
        .findFirst();

        lastLogin.ifPresent(login -> {
            login.setLogoutAt(Instant.now());
            login.setLogoutIpAddress(ipAddress);
            userRepository.save(user);
        });
    }

    fun generateEmailVerificationToken(User user): String {
        String token = UUID . randomUUID ().toString();
        user.setEmailVerificationToken(token);
        user.setEmailVerificationTokenExpiry(Instant.now().plus(24, ChronoUnit.HOURS));
        userRepository.save(user);
        return token;
    }

    @Transactional
    fun verifyEmail(String token): User {
        User user = userRepository . findByEmailVerificationToken (token)
            .orElseThrow(() -> InvalidTokenException("Invalid verification token"));

        if (user.getEmailVerificationTokenExpiry().isBefore(Instant.now())) {
            throw ExpiredTokenException("Verification token expired");
        }

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationTokenExpiry(null);

        return userRepository.save(user);
    }
}
```

## Advanced Security Configuration

### Complete Security Configuration

```kotlin
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
class SecurityConfig {

    private val authenticationEntryPoint: JwtAuthenticationEntryPoint
    private val accessDeniedHandler: JwtAccessDeniedHandler
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
    private val authenticationProvider: CustomAuthenticationProvider
    private val logoutHandler: LogoutHandler

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception
    {
        return http
            .csrf(csrf -> csrf
        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
        .ignoringRequestMatchers("/api/auth/**", "/api/public/**"))
        .sessionManagement(session -> session
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(exception -> exception
        .authenticationEntryPoint(authenticationEntryPoint)
        .accessDeniedHandler(accessDeniedHandler))
        .headers(headers -> headers
        .frameOptions().deny()
        .contentTypeOptions().and()
        .httpStrictTransportSecurity(hstsConfig -> hstsConfig
        .maxAgeInSeconds(31536000)
        .includeSubdomains(true))
        .cacheControl())
        .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/auth/**", "/api/public/**", "/actuator/health").permitAll()
        .requestMatchers("/api/admin/**").hasRole("ADMIN")
        .requestMatchers("/api/manager/**").hasAnyRole("MANAGER", "ADMIN")
        .requestMatchers("/api/users/me").authenticated()
        .anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2
        .jwt(jwt -> jwt
        .decoder(jwtDecoder())
        .jwtAuthenticationConverter(jwtAuthenticationConverter())))
        .authenticationProvider(authenticationProvider)
        .addFilterBefore(
            jwtAuthenticationFilter,
            UsernamePasswordAuthenticationFilter.class)
                .logout(logout -> logout
        .logoutUrl("/api/auth/logout")
        .addLogoutHandler(logoutHandler)
        .logoutSuccessHandler((request, response, authentication) ->
        response.setStatus(HttpStatus.NO_CONTENT.value())))
        .build();
    }

    @Bean
    fun jwtDecoder(): JwtDecoder {
        // Custom decoder with validation
        return CustomJwtDecoder(nimbusJwtDecoder(), jwtClaimsValidator());
    }

    @Bean
    fun nimbusJwtDecoder(): NimbusJwtDecoder {
        return NimbusJwtDecoder.withPublicKey(rsaPublicKey()).build();
    }

    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        JwtGrantedAuthoritiesConverter authoritiesConverter = JwtGrantedAuthoritiesConverter ();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("roles");

        JwtAuthenticationConverter converter = JwtAuthenticationConverter ();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        converter.setPrincipalClaimName("sub");

        return converter;
    }
}
```
