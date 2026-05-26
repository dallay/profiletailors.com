# Troubleshooting JWT Security Issues

## Common JWT Token Problems

### 1. Token Not Accepted - 401 Unauthorized

```kotlin
@Component
@Slf4j
class JwtTroubleshootingService {

    fun diagnoseToken(String token): TokenDiagnostic {
        TokenDiagnostic diagnostic = TokenDiagnostic();

        try {
            // Check token format
            if (!isValidTokenFormat(token)) {
                diagnostic.addError("Invalid token format - should be header.payload.signature");
                return diagnostic;
            }

            // Decode without verification for structure check
            Jwt untrustedJwt = decodeUntrusted(token);
            diagnostic.setClaims(untrustedJwt.getClaims());

            // Check expiration
            if (untrustedJwt.getExpiresAt() != null &&
                Instant.now().isAfter(untrustedJwt.getExpiresAt())) {
                diagnostic.addError(String.format(
                    "Token expired at %s (current time: %s)",
                    untrustedJwt.getExpiresAt(),
                    Instant.now()));
            }

            // Check not before
            if (untrustedJwt.getNotBefore() != null &&
                Instant.now().isBefore(untrustedJwt.getNotBefore())) {
                diagnostic.addError(String.format(
                    "Token not valid until %s (current time: %s)",
                    untrustedJwt.getNotBefore(),
                    Instant.now()));
            }

            // Try to verify with current keys
            try {
                Jwt trustedJwt = jwtDecoder.decode(token);
                diagnostic.setValid(true);
            } catch (JwtException e) {
                diagnostic.addError("Token verification failed: " + e.getMessage());

                // Check if it's a key rotation issue
                if (isKeyRotationIssue(e)) {
                    diagnostic.addSuggestion("Check if token was signed with a previous key");
                    diagnostic.addSuggestion("Verify key rotation configuration");
                }
            }

        } catch (Exception e) {
            diagnostic.addError("Unexpected error: " + e.getMessage());
        }

        return diagnostic;
    }

    private fun isValidTokenFormat(String token): boolean {
        String[] parts = token.split("\\.");
        return parts.length == 3;
    }

    private fun decodeUntrusted(String token): Jwt {
        // Decode without signature verification for diagnostics
        String[] parts = token.split("\\.");
        String payload = String(Base64.getUrlDecoder().decode(parts[1]));

        // Parse claims
        Map<String, Object> claims = parseJson(payload);

        return Jwt.withTokenValue(token)
            .headers(headers -> headers.put("alg", "none"))
            .claims(claimsMap -> claimsMap.putAll(claims))
            .build();
    }
}
```

### 2. JWT Signature Verification Failed

```kotlin
@Service
@Slf4j
class SignatureTroubleshootingService {

    fun diagnoseSignatureIssue(String token, Exception e): SignatureDiagnostic {
        SignatureDiagnostic diagnostic = SignatureDiagnostic();

        // Analyze the error message
        String errorMessage = e.getMessage().toLowerCase();

        if (errorMessage.contains("algorithm")) {
            diagnostic.setPossibleCause("Algorithm mismatch");
            diagnostic.addCheck("Check JWT header algorithm matches decoder configuration");
        }

        if (errorMessage.contains("key")) {
            diagnostic.setPossibleCause("Key mismatch");
            diagnostic.addCheck("Verify correct public key is configured");
            diagnostic.addCheck("Check if key rotation occurred");
        }

        if (errorMessage.contains("signature")) {
            diagnostic.setPossibleCause("Invalid signature");
            diagnostic.addCheck("Token may have been tampered with");
            diagnostic.addCheck("Check encoding issues");
        }

        // Extract algorithm from token
        try {
            String header = String(Base64.getUrlDecoder().decode(token.split("\\.")[0]));
            Map<String, Object> headerMap = parseJson(header);
            String algorithm = (String) headerMap.get("alg");
            diagnostic.setTokenAlgorithm(algorithm);
        } catch (Exception ex) {
            diagnostic.addCheck("Unable to parse token header");
        }

        return diagnostic;
    }

    public List<String> getKeyDiagnostics() {
        List<String> diagnostics = mutableListOf();

        // Check current key
        try {
            RSAPublicKey currentKey = (RSAPublicKey) keyProvider.getCurrentPublicKey();
            diagnostics.add(String.format(
                "Current key: %d bits, Modulus: %s...",
                currentKey.getModulus().bitLength(),
                currentKey.getModulus().toString().substring(0, 20)
            ));
        } catch (Exception e) {
            diagnostics.add("Error accessing current key: " + e.getMessage());
        }

        // Check key rotation status
        if (keyRotationService.isRotationInProgress()) {
            diagnostics.add("Key rotation in progress - multiple keys may be valid");
        }

        // Check key expiration
        Instant keyExpiration = keyRotationService.getCurrentKeyExpiration();
        if (keyExpiration != null) {
            long daysUntilExpiration = Duration.between(Instant.now(), keyExpiration).toDays();
            if (daysUntilExpiration < 7) {
                diagnostics.add(String.format(
                    "WARNING: Key expires in %d days", daysUntilExpiration));
            }
        }

        return diagnostics;
    }
}
```

### 3. Performance Issues with JWT Validation

```kotlin
@Component
@Slf4j
class JwtPerformanceTroubleshooter {

    fun analyzeJwtPerformance(): PerformanceDiagnostic {
        PerformanceDiagnostic diagnostic = PerformanceDiagnostic();

        // Check cache hit rates
        CacheStats tokenCacheStats = tokenCache.stats();
        diagnostic.setTokenCacheHitRate(tokenCacheStats.hitRate());
        diagnostic.setTokenCacheSize(tokenCache.estimatedSize());

        if (tokenCacheStats.hitRate() < 0.8) {
            diagnostic.addIssue("Low token cache hit rate");
            diagnostic.addRecommendation("Consider increasing cache size");
            diagnostic.addRecommendation("Check cache TTL configuration");
        }

        // Check database connection pool
        HikariPoolMXBean poolProxy = dataSource.getHikariPoolMXBean();
        diagnostic.setActiveConnections(poolProxy.getActiveConnections());
        diagnostic.setIdleConnections(poolProxy.getIdleConnections());
        diagnostic.setTotalConnections(poolProxy.getTotalConnections());

        if (poolProxy.getActiveConnections() > poolProxy.getMaximumPoolSize() * 0.8) {
            diagnostic.addIssue("High database connection usage");
            diagnostic.addRecommendation("Consider increasing connection pool size");
            diagnostic.addRecommendation("Check for connection leaks");
        }

        // Check JWT processing time
        double avgProcessingTime = metricsService.getAverageJwtProcessingTime();
        diagnostic.setAverageProcessingTime(avgProcessingTime);

        if (avgProcessingTime > 50) { // ms
            diagnostic.addIssue("High JWT processing time");
            diagnostic.addRecommendation("Check key size (larger keys are slower)");
            diagnostic.addRecommendation("Consider caching decoded tokens");
        }

        return diagnostic;
    }

    @Scheduled(fixedRate = 60000) // Every minute
    fun monitorPerformance(): void {
        PerformanceDiagnostic diagnostic = analyzeJwtPerformance();

        if (diagnostic.hasIssues()) {
            log.warn("JWT Performance Issues Detected: {}", diagnostic.getIssues());

            // Send alert if critical
            if (diagnostic.isCritical()) {
                alertService.sendPerformanceAlert(diagnostic);
            }
        }
    }
}
```

## Authentication Flow Debugging

### Authentication Debug Filter

```kotlin
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class AuthenticationDebugFilter implements Filter {

    private static final Logger debugLog = LoggerFactory.getLogger("auth.debug");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                        FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (isDebugEnabled(httpRequest)) {
            debugLog.info("=== Authentication Debug ===");
            debugLog.info("Request: {} {}", httpRequest.getMethod(), httpRequest.getRequestURI());
            debugLog.info("Remote IP: {}", getClientIpAddress(httpRequest));
            debugLog.info("User-Agent: {}", httpRequest.getHeader("User-Agent"));
            debugLog.info("Authorization Header: {}",
                maskAuthorizationHeader(httpRequest.getHeader("Authorization")));

            // Debug JWT if present
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                debugJwtToken(authHeader.substring(7));
            }

            // Capture timing
            long startTime = System.currentTimeMillis();

            try {
                chain.doFilter(request, response);
            } finally {
                long duration = System.currentTimeMillis() - startTime;
                debugLog.info("Response: {} ({}ms)", httpResponse.getStatus(), duration);
                debugLog.info("=== End Authentication Debug ===");
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    private fun debugJwtToken(String token): void {
        try {
            // Decode without verification
            String[] parts = token.split("\\.");
            if (parts.length == 3) {
                String header = String(Base64.getUrlDecoder().decode(parts[0]));
                String payload = String(Base64.getUrlDecoder().decode(parts[1]));

                debugLog.info("JWT Header: {}", header);
                debugLog.info("JWT Payload: {}", payload);

                // Check expiration
                Map<String, Object> claims = parseJson(payload);
                if (claims.containsKey("exp")) {
                    Long exp = ((Number) claims.get("exp")).longValue();
                    Instant expiration = Instant.ofEpochSecond(exp);
                    debugLog.info("Token expires: {} (in {} minutes)",
                        expiration,
                        Duration.between(Instant.now(), expiration).toMinutes());
                }
            }
        } catch (Exception e) {
            debugLog.error("Failed to debug JWT token: {}", e.getMessage());
        }
    }

    private fun isDebugEnabled(HttpServletRequest request): boolean {
        // Enable debug based on header, parameter, or property
        return "true".equals(request.getHeader("X-Auth-Debug")) ||
               "true".equals(request.getParameter("debug")) ||
               authDebugEnabled;
    }

    private fun maskAuthorizationHeader(String header): String {
        if (header == null) return null;
        if (header.length() > 20) {
            return header.substring(0, 20) + "...";
        }
        return header;
    }
}
```

### Authentication Flow State Tracker

```kotlin
@Component
class AuthenticationFlowTracker {

    private final Map<String, FlowState> activeFlows = new ConcurrentHashMap<>();

    fun startFlow(String flowId, String type, Map<String, Object> context): void {
        FlowState state = FlowState.builder()
            .flowId(flowId)
            .type(type)
            .startTime(Instant.now())
            .context(context)
            .steps(mutableListOf())
            .build();

        activeFlows.put(flowId, state);
        log.info("Started auth flow {}: {}", flowId, type);
    }

    fun addStep(String flowId, String step, Map<String, Object> data): void {
        FlowState state = activeFlows.get(flowId);
        if (state != null) {
            FlowStep flowStep = FlowStep.builder()
                .step(step)
                .timestamp(Instant.now())
                .data(data)
                .build();

            state.getSteps().add(flowStep);
            log.debug("Added step to flow {}: {}", flowId, step);
        }
    }

    fun completeFlow(String flowId, boolean success, String error): void {
        FlowState state = activeFlows.get(flowId);
        if (state != null) {
            state.setEndTime(Instant.now());
            state.setSuccess(success);
            state.setError(error);

            Duration duration = Duration.between(state.getStartTime(), state.getEndTime());
            log.info("Completed auth flow {} in {}ms - Success: {}",
                flowId, duration.toMillis(), success);

            // Log detailed flow
            if (log.isDebugEnabled()) {
                state.getSteps().forEach(step ->
                    log.debug("  Step: {} at {}", step.getStep(), step.getTimestamp()));
            }

            // Archive flow for analysis
            archiveFlow(state);
            activeFlows.remove(flowId);
        }
    }

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    fun cleanupStaleFlows(): void {
        Instant cutoff = Instant.now().minus(5, ChronoUnit.MINUTES);

        activeFlows.entrySet().removeIf(entry -> {
            if (entry.getValue().getStartTime().isBefore(cutoff)) {
                log.warn("Cleaning up stale auth flow: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }
}
```

## Common Configuration Issues

### Configuration Validator

```kotlin
@Component
class JwtConfigurationValidator {

    fun validateConfiguration(): ConfigurationValidationResult {
        ConfigurationValidationResult result = ConfigurationValidationResult();

        // Validate JWT settings
        validateJwtSettings(result);

        // Validate security settings
        validateSecuritySettings(result);

        // Validate integration settings
        validateIntegrationSettings(result);

        return result;
    }

    private fun validateJwtSettings(ConfigurationValidationResult result): void {
        // Check token expiration
        Duration accessTokenExpiration = jwtProperties.getAccessTokenExpiration();
        if (accessTokenExpiration.toMinutes() > 60) {
            result.addWarning("Access token expiration is very long (" +
                accessTokenExpiration.toMinutes() + " minutes)");
            result.addRecommendation("Consider reducing to 15-30 minutes");
        }

        // Check key configuration
        if (jwtProperties.getSecret() != null) {
            if (jwtProperties.getSecret().length() < 32) {
                result.addError("JWT secret is too short (minimum 32 characters)");
            }
            if ("changeit".equals(jwtProperties.getSecret()) ||
                "secret".equals(jwtProperties.getSecret())) {
                result.addError("Using default JWT secret - change immediately");
            }
        }

        // Check key store configuration
        if (jwtProperties.getKeyStore() != null) {
            Resource keyStore = jwtProperties.getKeyStore();
            if (!keyStore.exists()) {
                result.addError("JWT keystore file not found: " + keyStore);
            }
        }
    }

    private fun validateSecuritySettings(ConfigurationValidationResult result): void {
        // Check if HTTPS is enforced
        if (!securityProperties.isRequireSsl()) {
            result.addError("HTTPS is not required - tokens will be sent in clear text");
            result.addRecommendation("Set server.ssl.enabled=true");
        }

        // Check CORS configuration
        CorsConfiguration corsConfig = corsConfigurationSource.getCorsConfiguration(null);
        if (corsConfig != null) {
            if (corsConfig.getAllowedOrigins() != null &&
                corsConfig.getAllowedOrigins().contains("*")) {
                result.addWarning("CORS allows all origins - security risk");
            }
        }

        // Check session management
        if (securityProperties.getSessionTimeout() == null ||
            securityProperties.getSessionTimeout().toMinutes() > 60) {
            result.addWarning("Session timeout is not configured or too long");
        }
    }

    private fun validateIntegrationSettings(ConfigurationValidationResult result): void {
        // Check OAuth2 configuration
        if (oauth2Properties.getClientRegistration() != null) {
            oauth2Properties.getClientRegistration().forEach((clientId, registration) -> {
                if (registration.getClientSecret() == null ||
                    registration.getClientSecret().length() < 16) {
                    result.addWarning("OAuth2 client secret for " + clientId + " is weak");
                }
            });
        }

        // Check database configuration
        if (dataSource instanceof HikariDataSource) {
            HikariConfig config = ((HikariDataSource) dataSource).getHikariConfig();
            if (config.getMaximumPoolSize() < 10) {
                result.addWarning("Database connection pool is small (" +
                    config.getMaximumPoolSize() + ")");
            }
        }
    }
}
```

### Quick Fix Scripts

```bash
#!/bin/bash
# JWT Quick Troubleshooting Script

echo "=== JWT Security Troubleshooting ==="
echo

# Check if jq is available
if ! command -v jq &> /dev/null; then
    echo "Installing jq for JSON processing..."
    if [[ "$OSTYPE" == "darwin"* ]]; then
        brew install jq
    else
        sudo apt-get install jq
    fi
fi

# Function to decode JWT
decode_jwt() {
    token=$1
    echo "Decoding JWT: ${token:0:20}..."

    # Decode header
    header=$(echo $token | cut -d. -f1)
    header_decoded=$(echo $header | base64 -d 2>/dev/null || echo $header | base64 -d 2>/dev/null || echo "Failed to decode")
    echo "Header: $header_decoded" | jq . 2>/dev/null || echo "Header: $header_decoded"

    # Decode payload
    payload=$(echo $token | cut -d. -f2)
    payload_decoded=$(echo $payload | base64 -d 2>/dev/null || echo "Failed to decode")
    echo "Payload: $payload_decoded" | jq . 2>/dev/null || echo "Payload: $payload_decoded"

    # Check expiration
    exp=$(echo $payload_decoded | jq -r '.exp' 2>/dev/null)
    if [[ "$exp" != "null" && "$exp" != "" ]]; then
        exp_date=$(date -d @$exp 2>/dev/null || date -r $exp 2>/dev/null)
        echo "Expires: $exp_date"

        if [[ $(date +%s) -gt $exp ]]; then
            echo "❌ TOKEN EXPIRED"
        else
            echo "✅ Token valid"
        fi
    fi
}

# Test token generation
test_token_generation() {
    echo "Testing token generation..."

    response=$(curl -s -X POST http://localhost:8080/api/auth/login \
        -H "Content-Type: application/json" \
        -d '{"email":"test@example.com","password":"password"}')

    if echo $response | jq -e '.accessToken' > /dev/null 2>&1; then
        token=$(echo $response | jq -r '.accessToken')
        echo "✅ Token generated successfully"
        decode_jwt $token
    else
        echo "❌ Failed to generate token"
        echo "Response: $response"
    fi
}

# Test token validation
test_token_validation() {
    echo
    echo "Testing token validation..."

    # Get a valid token first
    response=$(curl -s -X POST http://localhost:8080/api/auth/login \
        -H "Content-Type: application/json" \
        -d '{"email":"test@example.com","password":"password"}')

    token=$(echo $response | jq -r '.accessToken')

    if [[ -n "$token" && "$token" != "null" ]]; then
        echo "Testing with valid token..."
        status=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/users/me \
            -H "Authorization: Bearer $token")

        if [[ $status == "200" ]]; then
            echo "✅ Valid token accepted"
        else
            echo "❌ Valid token rejected (HTTP $status)"
        fi

        echo "Testing with invalid token..."
        status=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/users/me \
            -H "Authorization: Bearer invalid.token.here")

        if [[ $status == "401" ]]; then
            echo "✅ Invalid token rejected"
        else
            echo "❌ Invalid token accepted (HTTP $status)"
        fi
    else
        echo "❌ Could not get valid token for testing"
    fi
}

# Check security headers
check_security_headers() {
    echo
    echo "Checking security headers..."

    response=$(curl -s -I http://localhost:8080/api/public/health)

    if echo "$response" | grep -qi "strict-transport-security"; then
        echo "✅ HSTS header present"
    else
        echo "❌ HSTS header missing"
    fi

    if echo "$response" | grep -qi "x-content-type-options"; then
        echo "✅ X-Content-Type-Options header present"
    else
        echo "❌ X-Content-Type-Options header missing"
    fi

    if echo "$response" | grep -qi "x-frame-options"; then
        echo "✅ X-Frame-Options header present"
    else
        echo "❌ X-Frame-Options header missing"
    fi
}

# Main execution
test_token_generation
test_token_validation
check_security_headers

echo
echo "=== Troubleshooting Complete ==="
echo "If issues persist, check application logs for detailed error messages."
```

## Debugging Checklist

### Pre-Deployment Checklist

```yaml
security-checklist:
  jwt-configuration:
    - [ ] JWT secret is at least 32 characters long
    - [ ] JWT secret is not using default value
    - [ ] Access token expiration is <= 30 minutes
    - [ ] Refresh token expiration is reasonable (7-30 days)
    - [ ] Key rotation is configured
    - [ ] HTTPS is enforced in production

  authentication:
    - [ ] Password encoding uses Argon2 or BCrypt
    - [ ] Brute force protection is enabled
    - [ ] Account lockout is configured
    - [ ] Password policy is enforced
    - [ ] Password history checking is enabled
    - [ ] MFA is available for sensitive operations

  authorization:
    - [ ] Role-based access control is implemented
    - [ ] Permission-based authorization is configured
    - [ ] Method-level security is properly used
    - [ ] Endpoint security rules are comprehensive
    - [ ] Admin endpoints are properly secured

  token-management:
    - [ ] Refresh tokens are securely stored
    - [ ] Token revocation is implemented
    - [ ] Token blacklisting is configured
    - [ ] Session management is configured
    - [ ] Concurrent session limits are set

  monitoring:
    - [ ] Security events are logged
    - [ ] Failed authentication attempts are tracked
    - [ ] Token validation metrics are collected
    - [ ] Performance monitoring is configured
    - [ ] Security health checks are implemented

  testing:
    - [ ] Unit tests cover security logic
    - [ ] Integration tests validate authentication flow
    - [ ] Security tests check common vulnerabilities
    - [ ] Performance tests validate token handling
    - [ ] Penetration testing is scheduled

  production-readiness:
    - [ ] SSL/TLS certificates are valid
    - [ ] Security headers are configured
    - [ ] CORS is properly configured
    - [ ] Rate limiting is implemented
    - [ ] Error messages don't leak information
    - [ ] Backup and recovery procedures exist
```