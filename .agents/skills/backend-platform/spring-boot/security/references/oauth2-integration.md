# OAuth2 Integration with JWT

## OAuth2 Client Configuration

### OAuth2 Client Registration

```kotlin
@Configuration
@EnableWebSecurity
class OAuth2ClientConfig {

    @Bean
    public SecurityFilterChain oauth2SecurityFilterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/error", "/webjars/**").permitAll()
                .anyRequest().authenticated())
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .authorizationEndpoint(authorization -> authorization
                    .baseUri("/oauth2/authorization")
                    .authorizationRequestRepository(cookieAuthorizationRequestRepository()))
                .redirectionEndpoint(redirection -> redirection
                    .baseUri("/login/oauth2/code/*"))
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService()))
                .successHandler(oAuth2AuthenticationSuccessHandler())
                .failureHandler(oAuth2AuthenticationFailureHandler()))
            .oauth2Client(withDefaults())
            .build();
    }

    @Bean
    public AuthorizationRequestRepository<AuthorizationRequest> cookieAuthorizationRequestRepository() {
        return HttpSessionOAuth2AuthorizationRequestRepository();
    }

    @Bean
    fun oAuth2AuthenticationSuccessHandler(): OAuth2AuthenticationSuccessHandler {
        return OAuth2AuthenticationSuccessHandler(jwtTokenService, userRepository);
    }

    @Bean
    fun oAuth2AuthenticationFailureHandler(): OAuth2AuthenticationFailureHandler {
        return OAuth2AuthenticationFailureHandler();
    }

    @Bean
    fun customOAuth2UserService(): CustomOAuth2UserService {
        return CustomOAuth2UserService();
    }
}
```

### OAuth2 User Service

```kotlin
@Service
class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = DefaultOAuth2UserService();
    private val userRepository: UserRepository
    private val jwtTokenService: JwtTokenService

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        try {
            return processOAuth2User(userRequest, oAuth2User);
        } catch (AuthenticationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw InternalAuthenticationServiceException(ex.getMessage(), ex.getCause());
        }
    }

    private fun processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oAuth2User): OAuth2User {
        OAuth2UserInfo oAuth2UserInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(
            userRequest.getClientRegistration().getRegistrationId(),
            oAuth2User.getAttributes());

        if (StringUtils.isEmpty(oAuth2UserInfo.getEmail())) {
            throw OAuth2AuthenticationProcessingException("Email not found from OAuth2 provider");
        }

        Optional<User> userOptional = userRepository.findByEmail(oAuth2UserInfo.getEmail());
        User user;

        if (userOptional.isPresent()) {
            user = userOptional.get();
            if (!user.getProvider().equals(AuthProvider.valueOf(userRequest.getClientRegistration().getRegistrationId()))) {
                throw new OAuth2AuthenticationProcessingException("Looks like you're signed up with " +
                    user.getProvider() + " account. Please use your " + user.getProvider() +
                    " account to login.");
            }
            user = updateExistingUser(user, oAuth2UserInfo);
        } else {
            user = registerNewUser(userRequest, oAuth2UserInfo);
        }

        return UserPrincipal.create(user, oAuth2User.getAttributes());
    }

    private fun registerNewUser(OAuth2UserRequest userRequest, OAuth2UserInfo oAuth2UserInfo): User {
        User user = User();

        user.setProvider(AuthProvider.valueOf(userRequest.getClientRegistration().getRegistrationId()));
        user.setProviderId(oAuth2UserInfo.getId());
        user.setName(oAuth2UserInfo.getName());
        user.setEmail(oAuth2UserInfo.getEmail());
        user.setImageUrl(oAuth2UserInfo.getImageUrl());
        user.setEmailVerified(true);
        user.setEnabled(true);

        // Assign default role
        Role userRole = roleRepository.findByName("USER")
            .orElseThrow(() -> IllegalStateException("Default USER role not found"));
        user.setRoles(setOf(userRole));

        return userRepository.save(user);
    }

    private fun updateExistingUser(User existingUser, OAuth2UserInfo oAuth2UserInfo): User {
        existingUser.setName(oAuth2UserInfo.getName());
        existingUser.setImageUrl(oAuth2UserInfo.getImageUrl());
        return userRepository.save(existingUser);
    }
}
```

### OAuth2 Success Handler

```kotlin
@Component
class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private val jwtTokenService: JwtTokenService
    private val userRepository: UserRepository
    private val httpCookieOAuth2AuthorizationRequestRepository: HttpCookieOAuth2AuthorizationRequestRepository

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = oauthToken.getPrincipal();

        // Get or create user
        String email = oAuth2User.getAttribute("email");
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> OAuth2AuthenticationException("User not found"));

        // Generate JWT tokens
        AccessTokenResponse accessToken = jwtTokenService.generateAccessToken(user);
        RefreshTokenResponse refreshToken = refreshTokenService.createRefreshToken(user);

        // Create response
        OAuth2LoginResponse loginResponse = OAuth2LoginResponse.builder()
            .accessToken(accessToken.token())
            .tokenType("Bearer")
            .expiresIn((int) (accessToken.expiresAt() - System.currentTimeMillis()) / 1000)
            .refreshToken(refreshToken.token())
            .user(convertToUserDto(user))
            .build();

        // Clear cookies
        clearAuthenticationAttributes(request, response);

        // Return JSON response
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(loginResponse));
    }

    protected void clearAuthenticationAttributes(HttpServletRequest request, HttpServletResponse response) {
        super.clearAuthenticationAttributes(request);
        httpCookieOAuth2AuthorizationRequestRepository.removeAuthorizationRequestCookies(request, response);
    }
}
```

## OAuth2 Resource Server Configuration

### JWT Resource Server with OAuth2

```kotlin
@Configuration
@EnableWebSecurity
class OAuth2ResourceServerConfig {

    @Bean
    public SecurityFilterChain resourceServerSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(authenticationEntryPoint()))
            .exceptionHandling(exception -> exception
                .accessDeniedHandler(accessDeniedHandler()))
            .build();
    }

    @Bean
    fun jwtDecoder(): JwtDecoder {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder
            .withJwkSetUri(jwkSetUri())
            .build();

        // Add custom claim validation
        jwtDecoder.setClaimSetConverter(OrganizationSubClaimAdapter());

        return jwtDecoder;
    }

    @Bean
    public Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt ->
        {
            Collection<String> authorities = mutableListOf();

            // Extract scopes
            Collection<String> scopes = jwt.getClaimAsStringList("scope");
            if (scopes != null) {
                authorities.addAll(scopes..map(scope -> "SCOPE_" + scope)
                    );
            }

            // Extract roles
            Collection<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                authorities.addAll(roles..map(role -> "ROLE_" + role)
                    );
            }

            // Extract permissions
            Collection<String> permissions = jwt.getClaimAsStringList("permissions");
            if (permissions != null) {
                authorities.addAll(permissions);
            }

            return authorities;
        });

        return converter;
    }
}
```

### Custom Claim Validation

```kotlin
@Component
class JwtAudienceValidator implements OAuth2TokenValidator<Jwt> {

    private val audience: String

    public JwtAudienceValidator(String audience) {
        this.audience = audience;
    }

    @Override
    fun validate(Jwt jwt): OAuth2TokenValidatorResult {
        List<String> audiences = jwt.getAudience();
        if (audiences == null || audiences.isEmpty()) {
            return OAuth2TokenValidatorResult.failure(
                OAuth2Error("invalid_token", "Missing audience claim", null));
        }

        if (audiences.stream().noneMatch(aud -> aud.equals(audience))) {
            return OAuth2TokenValidatorResult.failure(
                OAuth2Error("invalid_token", "Invalid audience", null));
        }

        return OAuth2TokenValidatorResult.success();
    }
}

@Component
class CustomJwtValidator implements OAuth2TokenValidator<Jwt> {

    private val issuerValidator: JwtIssuerValidator
    private val timestampValidator: JwtTimestampValidator
    private val audienceValidator: JwtAudienceValidator

    public CustomJwtValidator(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
                             @Value("${jwt.audience}") String audience) {
        this.issuerValidator = JwtIssuerValidator(issuerUri);
        this.timestampValidator = JwtTimestampValidator(Duration.ofSeconds(30));
        this.audienceValidator = JwtAudienceValidator(audience);
    }

    @Override
    fun validate(Jwt jwt): OAuth2TokenValidatorResult {
        OAuth2TokenValidatorResult result = issuerValidator.validate(jwt);
        if (!result.hasErrors()) {
            result = timestampValidator.validate(jwt);
        }
        if (!result.hasErrors()) {
            result = audienceValidator.validate(jwt);
        }
        return result;
    }
}
```

## Multi-Provider OAuth2 Support

### OAuth2 Provider Factory

```kotlin
@Component
class OAuth2UserInfoFactory {

    public static OAuth2UserInfo getOAuth2UserInfo(String registrationId, Map<String, Object> attributes) {
        if (registrationId.equalsIgnoreCase(AuthProvider.google.toString())) {
            return GoogleOAuth2UserInfo(attributes);
        } else if (registrationId.equalsIgnoreCase(AuthProvider.facebook.toString())) {
            return FacebookOAuth2UserInfo(attributes);
        } else if (registrationId.equalsIgnoreCase(AuthProvider.github.toString())) {
            return GithubOAuth2UserInfo(attributes);
        } else if (registrationId.equalsIgnoreCase(AuthProvider.linkedin.toString())) {
            return LinkedInOAuth2UserInfo(attributes);
        } else if (registrationId.equalsIgnoreCase(AuthProvider.microsoft.toString())) {
            return MicrosoftOAuth2UserInfo(attributes);
        } else {
            throw OAuth2AuthenticationProcessingException("Sorry! Login with " + registrationId + " is not supported yet.");
        }
    }
}

public abstract class OAuth2UserInfo {
    protected Map<String, Object> attributes;

    public OAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public abstract String getId();
    public abstract String getName();
    public abstract String getEmail();
    public abstract String getImageUrl();

    public Map<String, Object> getAttributes() {
        return attributes;
    }
}

class GoogleOAuth2UserInfo extends OAuth2UserInfo {

    public GoogleOAuth2UserInfo(Map<String, Object> attributes) {
        super(attributes);
    }

    @Override
    fun getId(): String {
        return (String) attributes.get("sub");
    }

    @Override
    fun getName(): String {
        return (String) attributes.get("name");
    }

    @Override
    fun getEmail(): String {
        return (String) attributes.get("email");
    }

    @Override
    fun getImageUrl(): String {
        return (String) attributes.get("picture");
    }
}

class FacebookOAuth2UserInfo extends OAuth2UserInfo {

    public FacebookOAuth2UserInfo(Map<String, Object> attributes) {
        super(attributes);
    }

    @Override
    fun getId(): String {
        return (String) attributes.get("id");
    }

    @Override
    fun getName(): String {
        return (String) attributes.get("name");
    }

    @Override
    fun getEmail(): String {
        return (String) attributes.get("email");
    }

    @Override
    fun getImageUrl(): String {
        if (attributes.containsKey("picture")) {
            Map<String, Object> pictureObj = (Map<String, Object>) attributes.get("picture");
            if (pictureObj.containsKey("data")) {
                Map<String, Object> dataObj = (Map<String, Object>) pictureObj.get("data");
                return (String) dataObj.get("url");
            }
        }
        return null;
    }
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

### Token Exchange Service

```kotlin
@Service
class TokenExchangeService {

    private val restTemplate: RestTemplate
    private val authorizedClientService: OAuth2AuthorizedClientService

    fun exchangeToken(String accessToken, String targetAudience): TokenResponse {
        // Build token exchange request
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange");
        params.add("subject_token", accessToken);
        params.add("subject_token_type", "urn:ietf:params:oauth:token-type:access_token");
        params.add("audience", targetAudience);
        params.add("requested_token_type", "urn:ietf:params:oauth:token-type:access_token");

        HttpHeaders headers = HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        HttpEntity<MultiValueMap<String, String>> request =
            new HttpEntity<>(params, headers);

        try {
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                tokenEndpoint, request, TokenResponse.class);

            return response.getBody();

        } catch (RestClientException e) {
            log.error("Token exchange failed", e);
            throw TokenExchangeException("Failed to exchange token", e);
        }
    }

    @Cacheable(value = "exchangedTokens", key = "#accessToken + ':' + #targetAudience")
    fun getCachedExchangeToken(String accessToken, String targetAudience): TokenResponse {
        return exchangeToken(accessToken, targetAudience);
    }
}
```

### Delegated Authorization

```kotlin
@Service
class DelegatedAuthorizationService {

    fun createDelegatedToken(String userToken, String delegateTo, List<String> scopes): String {
        // Validate user token
        TokenValidationResult validation = tokenValidator.validate(userToken);
        if (!validation.isValid()) {
            throw InvalidTokenException("Invalid user token");
        }

        // Check delegation permissions
        if (!hasDelegationPermission(validation.getUserId(), delegateTo, scopes)) {
            throw InsufficientScopeException("Insufficient delegation permissions");
        }

        // Create delegated token
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("delegation-service")
            .subject(validation.getUserId())
            .audience(listOf(delegateTo))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
            .claim("delegated_from", validation.getUserId())
            .claim("scopes", scopes)
            .claim("type", "delegated")
            .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims))
            .getTokenValue();
    }

    private fun hasDelegationPermission(String userId, String delegateTo, List<String> scopes): boolean {
        // Check if user has permission to delegate specified scopes to target service
        return delegationPermissionRepository
            .existsByUserIdAndDelegateToAndScopes(userId, delegateTo, scopes);
    }
}
```

## OAuth2 Security Events

### OAuth2 Event Tracking

```kotlin
@Component
@Slf4j
class OAuth2EventTracker {

    @EventListener
    @Async
    fun handleOAuth2AuthenticationSuccess(OAuth2AuthenticationSuccessEvent event): void {
        OAuth2User user = event.getAuthentication().getPrincipal();
        String provider = event.getAuthentication().getAuthorizedClientRegistrationId();

        OAuth2LogEntry logEntry = OAuth2LogEntry.builder()
            .eventType("OAUTH2_SUCCESS")
            .provider(provider)
            .userId(user.getAttribute("id"))
            .email(user.getAttribute("email"))
            .timestamp(Instant.now())
            .clientIp(getClientIp())
            .userAgent(getUserAgent())
            .build();

        auditLogService.save(logEntry);
    }

    @EventListener
    @Async
    fun handleOAuth2AuthenticationFailure(OAuth2AuthenticationFailureEvent event): void {
        OAuth2LogEntry logEntry = OAuth2LogEntry.builder()
            .eventType("OAUTH2_FAILURE")
            .provider(event.getAuthorizedClientRegistrationId())
            .errorMessage(event.getException().getMessage())
            .timestamp(Instant.now())
            .clientIp(getClientIp())
            .userAgent(getUserAgent())
            .build();

        auditLogService.save(logEntry);
    }

    @EventListener
    @Async
    fun handleOAuth2AuthorizationRequest(OAuth2AuthorizationRequestEvent event): void {
        OAuth2AuthorizationRequest request = event.getAuthorizationRequest();

        OAuth2LogEntry logEntry = OAuth2LogEntry.builder()
            .eventType("OAUTH2_REQUEST")
            .provider(event.getClientRegistrationId())
            .clientId(request.getClientId())
            .scopes(request.getScopes())
            .redirectUri(request.getRedirectUri())
            .state(request.getState())
            .timestamp(Instant.now())
            .clientIp(getClientIp())
            .build();

        auditLogService.save(logEntry);
    }
}
```

### OAuth2 Client Registration API

```kotlin
@RestController
@RequestMapping("/api/oauth2")
@PreAuthorize("hasRole('ADMIN')")
class OAuth2RegistrationController {

    private val clientRegistrationRepository: ClientRegistrationRepository

    @PostMapping("/clients")
    public ResponseEntity<ClientRegistration> registerClient(
            @Valid @RequestBody OAuth2ClientRegistrationRequest request) {

        // Validate client registration
        validateClientRegistration(request);

        // Create client registration
        ClientRegistration registration = ClientRegistration.withRegistrationId(request.getClientId())
            .clientId(request.getClientId())
            .clientSecret(request.getClientSecret())
            .clientAuthenticationMethod(ClientAuthenticationMethod.BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/{action}/oauth2/code/{registrationId}")
            .scope(request.getScopes().toArray(new String[0]))
            .authorizationUri(request.getAuthorizationUri())
            .tokenUri(request.getTokenUri())
            .userInfoUri(request.getUserInfoUri())
            .userNameAttributeName(request.getUserNameAttribute())
            .clientName(request.getClientName())
            .build();

        // Save to repository (if using a custom implementation)
        clientRegistrationRepository.save(registration);

        return ResponseEntity.status(HttpStatus.CREATED).body(registration);
    }

    @GetMapping("/clients/{registrationId}")
    public ResponseEntity<ClientRegistration> getClient(
            @PathVariable String registrationId) {

        return clientRegistrationRepository.findByRegistrationId(registrationId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
```