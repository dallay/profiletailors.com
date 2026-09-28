# OAuth2 Integration with JWT

This document covers OAuth2 client and resource server wiring in the reactive SMP
backend. All examples use the WebFlux security stack (`ServerHttpSecurity`,
`ServerOAuth2LoginSpec`, `ReactiveOAuth2UserService`) plus R2DBC repositories
through the domain/application/infrastructure split.

## OAuth2 Client Configuration

### Reactive OAuth2 Client Registration

```kotlin
@Configuration
@EnableWebFluxSecurity
class OAuth2ClientConfig {
    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain = http
        .authorizeExchange { auth ->
            auth
                .pathMatchers("/", "/login", "/error", "/webjars/**").permitAll()
                .anyExchange().authenticated()
        }
        .oauth2Login { oauth2 ->
            oauth2
                .authorizationEndpoint { authorization ->
                    authorization
                        .baseUri("/oauth2/authorization")
                        .authorizationRequestRepository(cookieAuthorizationRequestRepository())
                }
                .redirectionEndpoint { redirection ->
                    redirection.baseUri("/login/oauth2/code/*")
                }
                .userInfoEndpoint { userInfo ->
                    userInfo.userService(reactiveOAuth2UserService())
                }
                .successHandler(oAuth2AuthenticationSuccessHandler())
                .failureHandler(oAuth2AuthenticationFailureHandler())
        }
        .build()

    @Bean
    fun cookieAuthorizationRequestRepository(): ServerAuthorizationRequestRepository<OAuth2AuthorizationRequest> =
        HttpSessionServerOAuth2AuthorizationRequestRepository()

    @Bean
    fun oAuth2AuthenticationSuccessHandler(): ServerAuthenticationSuccessHandler =
        OAuth2AuthenticationSuccessHandler(jwtTokenService, userRepository)

    @Bean
    fun oAuth2AuthenticationFailureHandler(): ServerAuthenticationFailureHandler =
        OAuth2AuthenticationFailureHandler()

    @Bean
    fun reactiveOAuth2UserService(): ReactiveOAuth2UserService =
        CustomReactiveOAuth2UserService(jwtTokenService)
}
```

### Reactive OAuth2 User Service

```kotlin
@com.profiletailors.common.domain.Service
class CustomReactiveOAuth2UserService(
    private val jwtTokenService: JwtTokenService,
    private val userRepository: UserRepository,
) : ReactiveOAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private val delegate = DefaultReactiveOAuth2UserService()

    override fun loadUser(userRequest: OAuth2UserRequest): Mono<OAuth2User> =
        delegate.loadUser(userRequest)
            .flatMap { oAuth2User -> processOAuth2User(userRequest, oAuth2User) }
            .onErrorMap(AuthenticationException::class.java) { it }
            .onErrorMap(Exception::class.java) {
                InternalAuthenticationServiceException(it.message, it.cause)
            }

    private fun processOAuth2User(
        userRequest: OAuth2UserRequest,
        oAuth2User: OAuth2User,
    ): Mono<OAuth2User> {
        val userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(
            userRequest.clientRegistration.registrationId,
            oAuth2User.attributes,
        )

        if (userInfo.email.isNullOrEmpty()) {
            return Mono.error(OAuth2AuthenticationProcessingException("Email not found from OAuth2 provider"))
        }

        return userRepository.findByEmail(userInfo.email)
            .flatMap { existing ->
                if (existing.provider != AuthProvider.valueOf(userRequest.clientRegistration.registrationId)) {
                    Mono.error(
                        OAuth2AuthenticationProcessingException(
                            "Looks like you're signed up with ${existing.provider}. Please use your ${existing.provider} account to login.",
                        ),
                    )
                } else {
                    updateExistingUser(existing, userInfo)
                }
            }
            .switchIfEmpty(registerNewUser(userRequest, userInfo))
            .map { user -> UserPrincipal.create(user, oAuth2User.attributes) }
    }

    private fun registerNewUser(
        userRequest: OAuth2UserRequest,
        oAuth2UserInfo: OAuth2UserInfo,
    ): Mono<UserAccount> {
        val role = roleRepository.findByName("USER")
            ?: return Mono.error(IllegalStateException("Default USER role not found"))

        val newUser = UserAccount(
            id = UserId(0L),
            email = oAuth2UserInfo.email,
            passwordHash = "",
            firstName = oAuth2UserInfo.name,
            lastName = oAuth2UserInfo.name,
            enabled = true,
            emailVerified = true,
            provider = AuthProvider.valueOf(userRequest.clientRegistration.registrationId),
            providerId = oAuth2UserInfo.id,
            imageUrl = oAuth2UserInfo.imageUrl,
        )
        newUser.assignRoles(setOf(role))
        return userRepository.save(newUser)
    }

    private fun updateExistingUser(existing: UserAccount, oAuth2UserInfo: OAuth2UserInfo): Mono<UserAccount> =
        userRepository.save(
            existing.copy(
                firstName = oAuth2UserInfo.name,
                imageUrl = oAuth2UserInfo.imageUrl,
            ),
        )
}
```

### Reactive OAuth2 Success Handler

```kotlin
@Component
class OAuth2AuthenticationSuccessHandler(
    private val jwtTokenService: JwtTokenService,
    private val refreshTokenService: RefreshTokenService,
    private val httpCookieOAuth2AuthorizationRequestRepository: HttpCookieOAuth2AuthorizationRequestRepository,
    private val objectMapper: ObjectMapper,
) : ServerAuthenticationSuccessHandler {
    override fun onAuthenticationSuccess(
        webFilterExchange: WebFilterExchange,
        authentication: Authentication,
    ): Mono<Void> {
        val oauthToken = authentication as OAuth2AuthenticationToken
        val oAuth2User = oauthToken.principal

        val email = oAuth2User.attributes["email"] as? String
            ?: return Mono.error(OAuth2AuthenticationException("Email not found in OAuth2 user info"))

        return refreshTokenService.userRepository.findByEmail(email)
            .flatMap { user ->
                val tokenMono = jwtTokenService.generateAccessToken(user)
                val refreshMono = refreshTokenService.createRefreshToken(user)
                Mono.zip(tokenMono, refreshMono).map { (access, refresh) -> access to refresh }
            }
            .flatMap { (accessToken, refreshToken) ->
                val loginResponse = OAuth2LoginResponse(
                    accessToken = accessToken.token,
                    tokenType = "Bearer",
                    expiresIn = (accessToken.expiresAt - System.currentTimeMillis()) / 1000,
                    refreshToken = refreshToken.token,
                    user = userDto(accessToken.toUser()),
                )

                webFilterExchange.exchange.response
                    .statusCode(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .writeWith(
                        Mono.just(
                            webFilterExchange.exchange.response.bufferFactory()
                                .wrap(objectMapper.writeValueAsBytes(loginResponse)),
                        ),
                    )
            }
            .then(Mono.fromRunnable {
                val exchange = webFilterExchange.exchange
                httpCookieOAuth2AuthorizationRequestRepository.removeAuthorizationRequestCookies(exchange)
            })
    }
}
```

## OAuth2 Resource Server Configuration

### Reactive JWT Resource Server

```kotlin
@Configuration
@EnableWebFluxSecurity
class OAuth2ResourceServerConfig(
    @Value("\${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") private val jwkSetUri: String,
    @Value("\${jwt.audience}") private val audience: String,
) {
    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain = http
        .csrf { it.disable() }
        .anonymous { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeExchange { auth ->
            auth
                .pathMatchers("/api/public/**").permitAll()
                .pathMatchers("/actuator/**").hasRole("ADMIN")
                .anyExchange().authenticated()
        }
        .oauth2ResourceServer { oauth2 ->
            oauth2
                .jwt { jwt ->
                    jwt
                        .decoder(jwtDecoder())
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())
                }
                .authenticationEntryPoint(authenticationEntryPoint())
        }
        .exceptionHandling { exception ->
            exception.accessDeniedHandler(accessDeniedHandler())
        }
        .build()

    @Bean
    fun jwtDecoder(): ReactiveJwtDecoder = NimbusReactiveJwtDecoder
        .withJwkSetUri(jwkSetUri)
        .build()

    @Bean
    fun jwtAuthenticationConverter(): Converter<Jwt, Mono<AbstractAuthenticationToken>> =
        Converter<Jwt, Mono<AbstractAuthenticationToken>> { jwt ->
            val authorities = mutableListOf<String>()
            jwt.getClaimAsStringList("scope")?.forEach { authorities.add("SCOPE_$it") }
            jwt.getClaimAsStringList("roles")?.forEach { authorities.add("ROLE_$it") }
            jwt.getClaimAsStringList("permissions")?.let { authorities.addAll(it) }

            val granted = authorities.map { SimpleGrantedAuthority(it) }
            Mono.just(UsernamePasswordAuthenticationToken(jwt.subject, jwt.tokenValue, granted))
        }
}
```

### Custom Reactive Claim Validation

```kotlin
@Component
class JwtAudienceValidator(
    @Value("\${jwt.audience}") private val audience: String,
) : OAuth2TokenValidator<Jwt> {
    override fun validate(jwt: Jwt): OAuth2TokenValidatorResult {
        val audiences = jwt.audience
        if (audiences.isNullOrEmpty()) {
            return OAuth2TokenValidatorResult.failure(
                OAuth2Error("invalid_token", "Missing audience claim", null),
            )
        }
        if (audiences.none { it == audience }) {
            return OAuth2TokenValidatorResult.failure(
                OAuth2Error("invalid_token", "Invalid audience", null),
            )
        }
        return OAuth2TokenValidatorResult.success()
    }
}

@Component
class CustomJwtValidator(
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}") issuer: String,
    audienceValidator: JwtAudienceValidator,
) : OAuth2TokenValidator<Jwt> by DelegatingOAuth2TokenValidator(
    JwtIssuerValidator(issuer),
    JwtTimestampValidator(Duration.ofSeconds(30)),
    audienceValidator,
)
```

## Multi-Provider OAuth2 Support

### Reactive OAuth2 Provider Factory

```kotlin
@Component
class OAuth2UserInfoFactory {
    companion object {
        fun getOAuth2UserInfo(registrationId: String, attributes: Map<String, Any>): OAuth2UserInfo = when (
            registrationId.lowercase()
        ) {
            "google" -> GoogleOAuth2UserInfo(attributes)
            "facebook" -> FacebookOAuth2UserInfo(attributes)
            "github" -> GithubOAuth2UserInfo(attributes)
            "linkedin" -> LinkedInOAuth2UserInfo(attributes)
            "microsoft" -> MicrosoftOAuth2UserInfo(attributes)
            else -> throw OAuth2AuthenticationProcessingException("Sorry! Login with $registrationId is not supported yet.")
        }
    }
}

abstract class OAuth2UserInfo(
    protected val attributes: Map<String, Any>,
) {
    abstract val id: String
    abstract val name: String
    abstract val email: String
    abstract val imageUrl: String?
}

class GoogleOAuth2UserInfo(attributes: Map<String, Any>) : OAuth2UserInfo(attributes) {
    override val id: String get() = attributes["sub"] as String
    override val name: String get() = attributes["name"] as String
    override val email: String get() = attributes["email"] as String
    override val imageUrl: String? get() = attributes["picture"] as? String
}

class FacebookOAuth2UserInfo(attributes: Map<String, Any>) : OAuth2UserInfo(attributes) {
    override val id: String get() = attributes["id"] as String
    override val name: String get() = attributes["name"] as String
    override val email: String get() = attributes["email"] as String
    override val imageUrl: String?
        get() = (attributes["picture"] as? Map<*, *>)
            ?.let { it["data"] as? Map<*, *> }
            ?.let { it["url"] as? String }
}
```

### OAuth2 Client Properties Configuration

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${OAUTH2_GOOGLE_CLIENT_ID}
            client-secret: ${OAUTH2_GOOGLE_CLIENT_SECRET}
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope:
              - email
              - profile
          facebook:
            client-id: ${OAUTH2_FACEBOOK_CLIENT_ID}
            client-secret: ${OAUTH2_FACEBOOK_CLIENT_SECRET}
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope:
              - email
              - public_profile
          github:
            client-id: ${OAUTH2_GITHUB_CLIENT_ID}
            client-secret: ${OAUTH2_GITHUB_CLIENT_SECRET}
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope:
              - user:email
              - read:user
          linkedin:
            client-id: ${OAUTH2_LINKEDIN_CLIENT_ID}
            client-secret: ${OAUTH2_LINKEDIN_CLIENT_SECRET}
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope:
              - r_emailaddress
              - r_liteprofile
          microsoft:
            client-id: ${OAUTH2_MICROSOFT_CLIENT_ID}
            client-secret: ${OAUTH2_MICROSOFT_CLIENT_SECRET}
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope:
              - openid
              - email
              - profile
        provider:
          facebook:
            authorization-uri: https://www.facebook.com/v3.0/dialog/oauth
            token-uri: https://graph.facebook.com/v3.0/oauth/access_token
            user-info-uri: https://graph.facebook.com/v3.0/me?fields=id,name,email,picture
          linkedin:
            authorization-uri: https://www.linkedin.com/oauth/v2/authorization
            token-uri: https://www.linkedin.com/oauth/v2/accessToken
            user-info-uri: https://api.linkedin.com/v2/people/~:(id,firstName,lastName,emailAddress,profilePicture(displayImage~:playableStreams))
```

## OAuth2 Token Exchange

### Coroutine-Based Token Exchange Service

```kotlin
@com.profiletailors.common.domain.Service
class TokenExchangeService(
    private val webClient: WebClient,
    @Value("\${oauth2.client-id}") private val clientId: String,
    @Value("\${oauth2.client-secret}") private val clientSecret: String,
    @Value("\${oauth2.token-endpoint}") private val tokenEndpoint: String,
) {
    suspend fun exchangeToken(accessToken: String, targetAudience: String): TokenResponse {
        val params = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
            add("subject_token", accessToken)
            add("subject_token_type", "urn:ietf:params:oauth:token-type:access_token")
            add("audience", targetAudience)
            add("requested_token_type", "urn:ietf:params:oauth:token-type:access_token")
        }
        val credentials = Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray())

        return try {
            webClient.post()
                .uri(tokenEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header(HttpHeaders.AUTHORIZATION, "Basic $credentials")
                .bodyValue(params)
                .retrieve()
                .bodyToMono(TokenResponse::class.java)
                .awaitSingle()
        } catch (e: WebClientResponseException) {
            logger.error("Token exchange failed", e)
            throw TokenExchangeException("Failed to exchange token", e)
        }
    }

    suspend fun getCachedExchangeToken(accessToken: String, targetAudience: String): TokenResponse =
        exchangeToken(accessToken, targetAudience)

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(TokenExchangeService::class.java)
    }
}
```

### Delegated Authorization

```kotlin
@com.profiletailors.common.domain.Service
class DelegatedAuthorizationService(
    private val jwtEncoder: JwtEncoder,
    private val tokenValidator: TokenValidator,
    private val delegationPermissionRepository: DelegationPermissionRepository,
) {
    suspend fun createDelegatedToken(
        userToken: String,
        delegateTo: String,
        scopes: List<String>,
    ): String {
        val validation = tokenValidator.validate(userToken)
        if (!validation.isValid) {
            throw InvalidTokenException("Invalid user token")
        }
        if (!hasDelegationPermission(validation.userId, delegateTo, scopes)) {
            throw InsufficientScopeException("Insufficient delegation permissions")
        }

        val claims = JwtClaimsSet.builder()
            .issuer("delegation-service")
            .subject(validation.userId)
            .audience(listOf(delegateTo))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
            .claim("delegated_from", validation.userId)
            .claim("scopes", scopes)
            .claim("type", "delegated")
            .build()

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue
    }

    private suspend fun hasDelegationPermission(
        userId: String,
        delegateTo: String,
        scopes: List<String>,
    ): Boolean = delegationPermissionRepository
        .existsByUserIdAndDelegateToAndScopes(userId, delegateTo, scopes)
}
```

## OAuth2 Security Events

### OAuth2 Event Tracking

```kotlin
@Component
class OAuth2EventTracker(
    private val auditLogService: AuditLogService,
) {
    @EventListener
    suspend fun handleOAuth2AuthenticationSuccess(event: OAuth2AuthenticationSuccessEvent) {
        val authentication = event.authentication
        val user = authentication.principal as OAuth2User
        val provider = authentication.authorizedClientRegistrationId

        auditLogService.save(
            OAuth2LogEntry(
                eventType = "OAUTH2_SUCCESS",
                provider = provider,
                userId = user.attributes["id"]?.toString(),
                email = user.attributes["email"] as? String,
                timestamp = Instant.now(),
                clientIp = currentClientIp(),
                userAgent = currentUserAgent(),
            ),
        )
    }

    @EventListener
    suspend fun handleOAuth2AuthenticationFailure(event: OAuth2AuthenticationFailureEvent) {
        auditLogService.save(
            OAuth2LogEntry(
                eventType = "OAUTH2_FAILURE",
                provider = event.authorizedClientRegistrationId,
                errorMessage = event.exception.message,
                timestamp = Instant.now(),
                clientIp = currentClientIp(),
                userAgent = currentUserAgent(),
            ),
        )
    }

    @EventListener
    suspend fun handleOAuth2AuthorizationRequest(event: OAuth2AuthorizationRequestEvent) {
        val request = event.authorizationRequest

        auditLogService.save(
            OAuth2LogEntry(
                eventType = "OAUTH2_REQUEST",
                provider = event.clientRegistrationId,
                clientId = request.clientId,
                scopes = request.scopes,
                redirectUri = request.redirectUri,
                state = request.state,
                timestamp = Instant.now(),
                clientIp = currentClientIp(),
            ),
        )
    }
}
```

### OAuth2 Client Registration Reactive API

```kotlin
@RestController
@RequestMapping("/api/oauth2")
class OAuth2RegistrationController(
    private val clientRegistrationRepository: ClientRegistrationRepository,
) {
    @PostMapping("/clients")
    suspend fun registerClient(
        @Valid @RequestBody request: OAuth2ClientRegistrationRequest,
    ): ResponseEntity<ClientRegistration> {
        validateClientRegistration(request)

        val registration = ClientRegistration.withRegistrationId(request.clientId)
            .clientId(request.clientId)
            .clientSecret(request.clientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/{action}/oauth2/code/{registrationId}")
            .scope(*request.scopes.toTypedArray())
            .authorizationUri(request.authorizationUri)
            .tokenUri(request.tokenUri)
            .userInfoUri(request.userInfoUri)
            .userNameAttributeName(request.userNameAttribute)
            .clientName(request.clientName)
            .build()

        clientRegistrationRepository.save(registration)

        return ResponseEntity.status(HttpStatus.CREATED).body(registration)
    }

    @GetMapping("/clients/{registrationId}")
    suspend fun getClient(@PathVariable registrationId: String): ResponseEntity<ClientRegistration> =
        clientRegistrationRepository.findByRegistrationId(registrationId)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
}
```
