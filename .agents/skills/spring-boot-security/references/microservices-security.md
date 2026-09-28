# Microservices Security Patterns

## Inter-Service Authentication

### Service-to-Service JWT Tokens

```kotlin
@Configuration
class InterServiceSecurityConfig {

    @Bean
    @Primary
    fun interServiceJwtDecoder(): JwtDecoder {
        // Use public key for validating inter-service tokens
        return NimbusJwtDecoder.withPublicKey(interServicePublicKey()).build();
    }

    @Bean
    fun interServiceJwtEncoder(): JwtEncoder {
        // Use private key for generating inter-service tokens
        JWKSet jwkSet = new JWKSet(
            new RSAKey . Builder (interServicePrivateKey()).build()
        );
        return NimbusJwtEncoder(new ImmutableJWKSet < > (jwkSet));
    }

    @Bean
    fun interServiceRestTemplate(): RestTemplate {
        RestTemplate restTemplate = RestTemplate ();
        restTemplate.setInterceptors(
            List.of(
                InterServiceAuthInterceptor(jwtTokenService)
            )
        );
        return restTemplate;
    }
}

@Component
class InterServiceAuthInterceptor implements ClientHttpRequestInterceptor {

    private val tokenService: InterServiceJwtTokenService

    @Override
    public ClientHttpResponse intercept(
        HttpRequest request, byte[] body,
        ClientHttpRequestExecution execution
    ) throws IOException {

        // Generate inter-service token
        String token = tokenService . generateInterServiceToken ();

        // Add to Authorization header
        request.getHeaders().setBearerAuth(token);

        // Add service identification
        request.getHeaders().set("X-Service-Name", getCurrentServiceName());
        request.getHeaders().set("X-Service-Version", getCurrentServiceVersion());

        return execution.execute(request, body);
    }
}
```

### Service Authentication Provider

```kotlin
@Service
class InterServiceJwtTokenService {

    @Value("${service.name}")
    private var serviceName: String

    @Value("${service.version}")
    private var serviceVersion: String

    private val jwtEncoder: JwtEncoder

    fun generateInterServiceToken(): String {
        Instant now = Instant . now ();

        JwtClaimsSet claims = JwtClaimsSet . builder ()
            .issuer(serviceName)
            .subject("inter-service")
            .audience(listOf("microservices"))
            .issuedAt(now)
            .expiresAt(now.plus(5, ChronoUnit.MINUTES)) // Short-lived tokens
            .claim("service", serviceName)
            .claim("version", serviceVersion)
            .claim("type", "inter-service")
            .claim("jti", UUID.randomUUID().toString())
            .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims))
            .getTokenValue();
    }

    fun isValidInterServiceToken(String token): boolean {
        try {
            Jwt jwt = jwtDecoder . decode (token);

            // Validate claims
            return "inter-service".equals(jwt.getClaimAsString("type")) &&
                    serviceName.equals(jwt.getClaimAsString("service")) &&
                    jwt.getExpiresAt() != null &&
                    Instant.now().isBefore(jwt.getExpiresAt());

        } catch (JwtException e) {
            return false;
        }
    }
}
```

## API Gateway Security

### Gateway Authentication Filter

```kotlin
@Component
@Slf4j
class GatewayAuthenticationFilter implements GlobalFilter, Ordered {

    private val routeValidator: RouteValidator
    private val tokenValidator: JwtTokenValidator
    private val rateLimiterRegistry: RateLimiterRegistry

    @Override
    public Mono < Void > filter (ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange . getRequest ();

    // Skip authentication for public routes
    if (routeValidator.isSecured.test(request)) {
        // Extract token
        String authHeader = request . getHeaders ().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return handleUnauthorized(exchange);
        }

        String token = authHeader . substring (7);

        try {
            // Validate JWT
            JwtValidationResult validation = tokenValidator . validate (token);

            if (!validation.isValid()) {
                return handleUnauthorized(exchange, validation.getErrorMessage());
            }

            // Extract user information
            Jwt jwt = validation . getJwt ();
            String userId = jwt . getClaimAsString ("sub");
            List<String> authorities = jwt . getClaimAsStringList ("roles");

            // Add user context to request headers
            ServerHttpRequest modifiedRequest = request . mutate ()
                .header("X-User-Id", userId)
                .header("X-User-Roles", String.join(",", authorities))
                .header("X-User-Email", jwt.getClaimAsString("email"))
                .build();

            exchange = exchange.mutate().request(modifiedRequest).build();

            // Apply rate limiting
            if (!checkRateLimit(userId, request.getPath().value())) {
                return handleTooManyRequests(exchange);
            }

        } catch (JwtException e) {
            log.error("JWT validation failed", e);
            return handleUnauthorized(exchange);
        }
    }

    return chain.filter(exchange);
}

    private Mono < Void > handleUnauthorized (ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private Mono < Void > handleTooManyRequests (ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        return exchange.getResponse().setComplete();
    }

    @Override
    fun getOrder(): int {
        return -100; // High priority
    }
}
```

### Gateway Route Security Configuration

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/users/**
          filters:
            - StripPrefix=1
            - TokenRelay=
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20

        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/api/orders/**
          filters:
            - StripPrefix=1
            - TokenRelay=
            - AuthFilter=ROLE_USER,ROLE_MANAGER

        - id: admin-service
          uri: lb://admin-service
          predicates:
            - Path=/api/admin/**
          filters:
            - StripPrefix=1
            - TokenRelay=
            - AuthFilter=ROLE_ADMIN

      # Security headers
      default-filters:
        - name: Retry
          args:
            retries: 3
            statuses: BAD_GATEWAY,GATEWAY_TIMEOUT
        - name: CircuitBreaker
          args:
            name: circuitBreaker
            fallbackUri: forward:/fallback
```

## Service Mesh Security (Istio Integration)

### Istio Authentication Policy

```yaml
apiVersion: "authentication.istio.io/v1alpha1"
kind: "Policy"
metadata:
  name: "jwt-auth-policy"
  namespace: "default"
spec:
  peers:
    - mtls: { }
  origins:
    - jwt:
        issuer: "https://auth.myapp.com"
        jwksUri: "https://auth.myapp.com/.well-known/jwks.json"
        trigger_rules:
          - excluded_paths:
              - exact: "/api/public"
              - exact: "/health"
  principalBinding: USE_ORIGIN
---
apiVersion: "networking.istio.io/v1alpha3"
kind: "AuthorizationPolicy"
metadata:
  name: "service-authz"
  namespace: "default"
spec:
  selector:
    matchLabels:
      app: user-service
  rules:
    - from:
        - source:
            principals: [ "cluster.local/ns/default/sa/user-service" ]
    - to:
        - operation:
            methods: [ "GET", "POST" ]
            paths: [ "/api/users/*" ]
    - when:
        - key: request.auth.claims[role]
          values: [ "USER", "ADMIN" ]
```

### Spring Boot Istio Integration

```kotlin
@Configuration
class IstioSecurityConfig {

    @Bean
    public FilterRegistrationBean<IstioAuthenticationFilter> istioAuthFilter()
    {
        FilterRegistrationBean<IstioAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(IstioAuthenticationFilter());
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    fun istioUserDetailsService(): UserDetailsService {
        return IstioUserDetailsService();
    }
}

class IstioAuthenticationFilter implements Filter {

    @Override
    public void doFilter(
        ServletRequest request, ServletResponse response,
        FilterChain chain
    ) throws IOException, ServletException {

    HttpServletRequest httpRequest =(HttpServletRequest) request;

    // Extract user identity from Istio headers
    String userId = httpRequest . getHeader ("x-forwarded-user");
    String userGroups = httpRequest . getHeader ("x-forwarded-groups");

    if (userId != null) {
        // Create authentication object
        List<GrantedAuthority> authorities = parseGroups (userGroups);
        UserDetails userDetails = User . builder ()
            .username(userId)
            .password("") // No password for Istio auth
            .authorities(authorities)
            .build();

        UsernamePasswordAuthenticationToken authentication =
        UsernamePasswordAuthenticationToken(userDetails, null, authorities);

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    chain.doFilter(request, response);
}
}
```

## Distributed Token Validation

### Centralized Token Validation Service

```kotlin
@Service
@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
class DistributedTokenValidationService {

    private val cache: TokenValidationCache
    private val authServiceClient: AuthServiceClient

    fun validateToken(String token): TokenValidationResult {
        // Check cache first
        TokenValidationResult cached = cache . get (token);
        if (cached != null && !cached.isExpired()) {
            return cached;
        }

        // Validate with auth service
        try {
            TokenValidationResult result = authServiceClient . validateToken (token);

            // Cache the result
            cache.put(token, result);

            return result;

        } catch (FeignException e) {
            log.error("Token validation failed", e);
            return TokenValidationResult.invalid("Validation service unavailable");
        }
    }

    @Recover
    fun recover(FeignException e, String token): TokenValidationResult {
        // Fallback validation when auth service is unavailable
        return localTokenValidation(token);
    }
}

@FeignClient(
    name = "auth-service", configuration = FeignConfig.class)
    interface AuthServiceClient {

    @PostMapping("/api/auth/validate")
    TokenValidationResult validateToken (@RequestBody String token);

    @PostMapping("/api/auth/revoke")
    void revokeToken (@RequestBody String token);
}
```

### Token Introspection Pattern

```kotlin
@Service
class TokenIntrospectionService {

    private val restTemplate: RestTemplate
    private val introspectionEndpoint: String

    fun introspect(String token): TokenIntrospectionResult {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("token", token);
        params.add("token_type_hint", "access_token");

        HttpHeaders headers = HttpHeaders ();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        HttpEntity<MultiValueMap<String, String>> entity =
        new HttpEntity < > (params, headers);

        try {
            ResponseEntity<TokenIntrospectionResult> response = restTemplate . postForEntity (
                    introspectionEndpoint, entity, TokenIntrospectionResult.class);

            return response.getBody();

        } catch (RestClientException e) {
            log.error("Token introspection failed", e);
            return TokenIntrospectionResult.inactive();
        }
    }
}
```

## Service Discovery and Security

### Secure Service Discovery

```kotlin
@Configuration
class ServiceDiscoverySecurityConfig {

    @Bean
    fun secureDiscoveryClient(): DiscoveryClient {
        return SecureDiscoveryClient();
    }
}

class SecureDiscoveryClient implements DiscoveryClient {

    private val delegate: DiscoveryClient
    private val properties: ServiceSecurityProperties

    @Override
    public List < ServiceInstance > getInstances (String serviceId) {
        List<ServiceInstance> instances = delegate . getInstances (serviceId);

        return instances..filter(this::isInstanceSecure)
            .map(this::addSecurityMetadata)
        ;
    }

    private fun isInstanceSecure(ServiceInstance instance): boolean {
        // Check if instance is using HTTPS
        String scheme = instance . getUri ().getScheme();
        if (!"https".equals(scheme)) {
            log.warn(
                "Insecure instance detected for service {}: {}",
                serviceId, instance.getUri()
            );
            return false;
        }

        // Verify instance certificate
        return verifyInstanceCertificate(instance);
    }

    private fun addSecurityMetadata(ServiceInstance instance): ServiceInstance {
        Map<String, String> metadata = new HashMap<>(instance.getMetadata());

        // Add security metadata
        metadata.put("security.enabled", "true");
        metadata.put("security.tls-version", getTlsVersion(instance));
        metadata.put("security.cipher-suite", getCipherSuite(instance));

        return new DefaultServiceInstance (
                instance.getInstanceId(),
        instance.getServiceId(),
        instance.getHost(),
        instance.getPort(),
        instance.isSecure(),
        metadata,
        instance.getUri()
        );
    }
}
```

## Circuit Breaker for Security Services

### Resilient Security Client

```kotlin
@Component
class ResilientAuthServiceClient {

    private val authClient: AuthServiceClient
    private val circuitBreaker: CircuitBreaker
    private val rateLimiter: RateLimiter
    private val bulkhead: Bulkhead

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    fun validateToken(String token): TokenValidationResult {
        Supplier<TokenValidationResult> decoratedSupplier = Decorators . ofSupplier (
                () -> authClient.validateToken(token))
        .withCircuitBreaker(circuitBreaker)
            .withRateLimiter(rateLimiter)
            .withBulkhead(bulkhead)
            .withFallback(Callable.of(this::fallbackValidation))
            .decorate();

        return Try.ofSupplier(decoratedSupplier)
            .recover(throwable -> fallbackValidation(token))
        .get();
    }

    private fun fallbackValidation(String token): TokenValidationResult {
        log.warn("Using fallback validation for token");
        // Implement local validation logic
        return localValidateToken(token);
    }

    private fun fallbackValidation(): TokenValidationResult {
        // Default fallback when token is not available
        return TokenValidationResult.invalid("Service unavailable");
    }
}

@Configuration
class ResilienceConfig {

    @Bean
    fun authServiceCircuitBreaker(): CircuitBreaker {
        CircuitBreakerConfig config = CircuitBreakerConfig . custom ()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .ringBufferSizeInHalfOpenState(10)
            .ringBufferSizeInClosedState(100)
            .build();

        return CircuitBreaker.of("authService", config);
    }

    @Bean
    fun authServiceRateLimiter(): RateLimiter {
        return RateLimiter.of(
            "authService",
            RateLimiterConfig.custom()
                .limitForPeriod(100)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ofSeconds(5))
                .build()
        );
    }

    @Bean
    fun authServiceBulkhead(): Bulkhead {
        return Bulkhead.of(
            "authService",
            BulkheadConfig.custom()
                .maxConcurrentCalls(20)
                .maxWaitDuration(Duration.ofSeconds(2))
                .build()
        );
    }
}
```

## Distributed Security Events

### Security Event Publishing

```kotlin
@Component
@Slf4j
class SecurityEventPublisher {

    private final KafkaTemplate<String, SecurityEvent> kafkaTemplate;
    private val topicName: String

    @EventListener
    @Async
    public void handleAuthenticationSuccess(
    AuthenticationSuccessEvent event)
    {
        SecurityEvent securityEvent = SecurityEvent . builder ()
            .eventType(SecurityEventType.AUTHENTICATION_SUCCESS)
            .userId(event.getUser().getId())
            .service(getCurrentServiceName())
            .timestamp(Instant.now())
            .ipAddress(event.getIpAddress())
            .userAgent(event.getUserAgent())
            .build();

        publishEvent(securityEvent);
    }

    @EventListener
    @Async
    public void handleAuthenticationFailure(
    AuthenticationFailureEvent event)
    {
        SecurityEvent securityEvent = SecurityEvent . builder ()
            .eventType(SecurityEventType.AUTHENTICATION_FAILURE)
            .username(event.getUsername())
            .service(getCurrentServiceName())
            .timestamp(Instant.now())
            .ipAddress(event.getIpAddress())
            .userAgent(event.getUserAgent())
            .failureReason(event.getException().getMessage())
            .build();

        publishEvent(securityEvent);
    }

    @EventListener
    @Async
    fun handleTokenRevoked(RefreshTokenRevokedEvent event): void {
        SecurityEvent securityEvent = SecurityEvent . builder ()
            .eventType(SecurityEventType.TOKEN_REVOKED)
            .userId(event.getRefreshToken().getUser().getId())
            .service(getCurrentServiceName())
            .timestamp(Instant.now())
            .tokenId(event.getRefreshToken().getTokenId())
            .reason(event.getReason())
            .build();

        publishEvent(securityEvent);
    }

    private fun publishEvent(SecurityEvent event): void {
        try {
            kafkaTemplate.send(topicName, event.getUserId().toString(), event)
                .addCallback(
                    result -> log.debug("Security event published successfully"),
            failure -> log.error("Failed to publish security event", failure)
            );
        } catch (Exception e) {
            log.error("Error publishing security event", e);
        }
    }
}
```

### Security Event Consumer

```kotlin
@Component
@Slf4j
class SecurityEventConsumer {

    private val eventProcessor: SecurityEventProcessor

    @KafkaListener(
        topics = "${security.events.topic}",
        groupId = "${security.events.consumer - group}"
    )
    fun handleSecurityEvent(SecurityEvent event): void {
        try {
            eventProcessor.processEvent(event);
        } catch (Exception e) {
            log.error("Error processing security event: {}", event, e);
            // Implement dead letter queue handling
        }
    }

    @KafkaListener(
        topics = "${security.events.dlt - topic}",
        groupId = "${security.events.dlt - group}"
    )
    fun handleDeadLetterEvent(ConsumerRecord<String, SecurityEvent> record): void {
        log.error("Security event in dead letter queue: {}", record);
        // Implement alerting or manual intervention
    }
}

@Service
class SecurityEventProcessor {

    @EventListener
    fun processSuspiciousActivity(SecurityEvent event): void {
        if (isSuspiciousActivity(event)) {
            // Trigger additional security measures
            triggerSecurityResponse(event);
        }
    }

    private fun isSuspiciousActivity(SecurityEvent event): boolean {
        return event.getEventType() == SecurityEventType.AUTHENTICATION_FAILURE &&
                getRecentFailureCount(event.getUserId()) > 5;
    }

    private fun triggerSecurityResponse(SecurityEvent event): void {
        // Lock user account temporarily
        userService.lockAccountTemporarily(event.getUserId());

        // Send alert
        alertService.sendSuspiciousActivityAlert(event);

        // Invalidate all user sessions
        sessionService.invalidateAllUserSessions(event.getUserId());
    }
}
```
