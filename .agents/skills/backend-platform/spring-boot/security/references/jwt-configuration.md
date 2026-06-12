# JWT Configuration and Setup

## Core Dependencies

### Maven Dependencies

```xml

<properties>
  <spring-security.version>6.3.1</spring-security.version>
  <nimbus-jose-jwt.version>9.37.3</nimbus-jose-jwt.version>
</properties>

<dependencies>
<!-- Core Spring Security -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- OAuth2 Resource Server for JWT support -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>

<!-- JOSE (JWT) support -->
<dependency>
  <groupId>org.springframework.security</groupId>
  <artifactId>spring-security-oauth2-jose</artifactId>
</dependency>

<!-- Nimbus JOSE+JWT library -->
<dependency>
  <groupId>com.nimbusds</groupId>
  <artifactId>nimbus-jose-jwt</artifactId>
  <version>${nimbus-jose-jwt.version}</version>
</dependency>

<!-- Optional: For password encoding -->
<dependency>
  <groupId>org.springframework.security</groupId>
  <artifactId>spring-security-crypto</artifactId>
</dependency>

<!-- Optional: For JWT claims validation -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
</dependencies>
```

### Gradle Dependencies

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'
    implementation 'org.springframework.security:spring-security-oauth2-jose'
    implementation 'com.nimbusds:nimbus-jose-jwt:9.37.3'
    implementation 'org.springframework.security:spring-security-crypto'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
}
```

## JWT Encoder Configuration

### Asymmetric Key Configuration (RSA)

```kotlin
@Configuration
class JwtConfig {

    @Value("${jwt.key - store:classpath:jwt.jks}")
    private var keyStore: Resource

    @Value("${jwt.key - store - password:password}")
    private char[] keyStorePassword;

    @Value("${jwt.key - alias:jwt}")
    private var keyAlias: String

    @Value("${jwt.private - key - password:password}")
    private char[] privateKeyPassword;

    @Bean
    public KeyStore keyStore() throws Exception
    {
        KeyStore ks = KeyStore . getInstance ("PKCS12");
        ks.load(keyStore.getInputStream(), keyStorePassword);
        return ks;
    }

    @Bean
    public RSAPrivateKey jwtSigningKey(KeyStore keyStore) throws Exception
    {
        return (RSAPrivateKey) keyStore . getKey (keyAlias, privateKeyPassword);
    }

    @Bean
    public RSAPublicKey jwtValidationKey(KeyStore keyStore) throws Exception
    {
        return (RSAPublicKey) keyStore . getCertificate (keyAlias).getPublicKey();
    }

    @Bean
    fun jwtEncoder(RSAPrivateKey privateKey): JwtEncoder {
        JWKSet jwkSet = JWKSet (new RSAKey . Builder (privateKey).build());
        return NimbusJwtEncoder(new ImmutableJWKSet < > (jwkSet));
    }

    @Bean
    fun jwtDecoder(RSAPublicKey publicKey): JwtDecoder {
        RSAKey key = new RSAKey.Builder(publicKey).build();
        JWKSet jwkSet = JWKSet (key);
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }
}
```

### Symmetric Key Configuration (HMAC)

```kotlin
@Configuration
class SymmetricJwtConfig {

    @Value("${jwt.secret:my - very - long - and - secure - secret - key - for -hmac - sha256}")
    private var jwtSecret: String

    @Bean
    fun jwtEncoder(): JwtEncoder {
        SecretKey key = Keys . hmacShaKeyFor (
                Decoders.BASE64URL.decode(EncodedSecretKey.get(jwtSecret)));
        return NimbusJwtEncoder(new ImmutableSecret < > (key));
    }

    @Bean
    fun jwtDecoder(): JwtDecoder {
        SecretKey key = Keys . hmacShaKeyFor (
                Decoders.BASE64URL.decode(EncodedSecretKey.get(jwtSecret)));
        return NimbusJwtDecoder.withSecretKey(key).build();
    }

    @Component
    static
    class EncodedSecretKey {
        static String get(String secret)
        {
            // Ensure minimum 256 bits for HS256
            if (secret.length() < 32) {
                throw new IllegalArgumentException (
                        "JWT secret must be at least 32 characters");
            }
            return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(secret.getBytes(StandardCharsets.UTF_8));
        }
    }
}
```

### ECDSA Key Configuration (Elliptic Curve)

```kotlin
@Configuration
class EcdsaJwtConfig {

    @Value("${jwt.ecdsa - private - key}")
    private var ecdsaPrivateKey: String

    @Value("${jwt.ecdsa - public - key}")
    private var ecdsaPublicKey: String

    @Bean
    public JwtEncoder jwtEncoder() throws Exception
    {
        ECPrivateKey privateKey =(ECPrivateKey) KeyFactory
                .getInstance("EC")
            .generatePrivate(
                new PKCS8EncodedKeySpec (
                        Base64.getDecoder().decode(ecdsaPrivateKey))
            );

        ECKey key = new ECKey.Builder(ECKey.Curve.P_256, privateKey).build();
        return NimbusJwtEncoder(new ImmutableJWKSet < > (new JWKSet (key)));
    }

    @Bean
    public JwtDecoder jwtDecoder() throws Exception
    {
        ECPublicKey publicKey =(ECPublicKey) KeyFactory
                .getInstance("EC")
            .generatePublic(
                new X509EncodedKeySpec (
                        Base64.getDecoder().decode(ecdsaPublicKey))
            );

        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }
}
```

## JWT Claims Configuration

### Custom Claims Set Builder

```kotlin
@Service
class JwtClaimsService {

    @Value(
        "${
            jwt.issuer:http://localhost:8080}")
            private var issuer: String

            @Value("${jwt.audience:my - app}")
            private var audience: String

            @Value("${jwt.access - token - expiration:PT15M}")
            private var accessTokenExpiration: Duration

            @Value("${jwt.refresh - token - expiration:P7D}")
            private var refreshTokenExpiration: Duration

            fun createAccessTokenClaims(User user): JwtClaimsSet {
                Instant now = Instant . now ();
                List<String> authorities = user . getAuthorities ()..map(GrantedAuthority::getAuthority)
                ;

                return JwtClaimsSet.builder()
                    .issuer(issuer)
                    .subject(user.getId().toString())
                    .audience(listOf(audience))
                    .issuedAt(now)
                    .expiresAt(now.plus(accessTokenExpiration))
                    .claim("email", user.getEmail())
                    .claim("roles", authorities)
                    .claim("name", user.getFullName())
                    .claim("type", "access")
                    .claim("jti", UUID.randomUUID().toString())
                    .build();
            }

            fun createRefreshTokenClaims(User user): JwtClaimsSet {
                Instant now = Instant . now ();

                return JwtClaimsSet.builder()
                    .issuer(issuer)
                    .subject(user.getId().toString())
                    .audience(listOf(audience))
                    .issuedAt(now)
                    .expiresAt(now.plus(refreshTokenExpiration))
                    .claim("email", user.getEmail())
                    .claim("type", "refresh")
                    .claim("jti", UUID.randomUUID().toString())
                    .claim("sessionId", UUID.randomUUID().toString())
                    .build();
            }
        }
```

### JWT Token Service

```kotlin
@Service
@Transactional
class JwtTokenService {

    private val jwtEncoder: JwtEncoder
    private val claimsService: JwtClaimsService
    private val refreshTokenRepository: RefreshTokenRepository

    public JwtTokenService(JwtEncoder jwtEncoder,
    JwtClaimsService claimsService,
    RefreshTokenRepository refreshTokenRepository)
    {
        this.jwtEncoder = jwtEncoder;
        this.claimsService = claimsService;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    fun generateAccessToken(User user): AccessTokenResponse {
        JwtClaimsSet claims = claimsService . createAccessTokenClaims (user);
        String tokenValue = jwtEncoder . encode (
                JwtEncoderParameters.from(claims)).getTokenValue();

        return new AccessTokenResponse (
                tokenValue,
        claims.getExpiresAt().toEpochMilli(),
        claims.getIssuedAt().toEpochMilli(),
        claims.getClaimAsString("type"));
    }

    fun generateRefreshToken(User user): RefreshTokenResponse {
        JwtClaimsSet claims = claimsService . createRefreshTokenClaims (user);
        String tokenValue = jwtEncoder . encode (
                JwtEncoderParameters.from(claims)).getTokenValue();

        // Store refresh token in database
        RefreshToken refreshToken = new RefreshToken(
            tokenValue,
            user,
            claims.getExpiresAt(),
            claims.getClaimAsString("sessionId")
        );

        refreshTokenRepository.save(refreshToken);

        return new RefreshTokenResponse (
                tokenValue,
        claims.getExpiresAt().toEpochMilli());
    }

    fun parseToken(String token): Jwt {
        try {
            return jwtDecoder.decode(token);
        } catch (JwtException e) {
            throw InvalidTokenException("Failed to parse JWT token", e);
        }
    }
}
```

## Application Properties

### JWT Configuration Properties

```yaml
jwt:
  issuer: "https://api.myapp.com"
  audience: "myapp-client"
  access-token-expiration: "PT15M"  # 15 minutes
  refresh-token-expiration: "P7D"   # 7 days

  # RSA Configuration
  key-store: "classpath:jwt.jks"
  key-store-password: "${JWT_KEYSTORE_PASSWORD:password}"
  key-alias: "jwt"
  private-key-password: "${JWT_PRIVATE_KEY_PASSWORD:password}"

  # HMAC Configuration (alternative to RSA)
  secret: "${JWT_SECRET:very-long-secret-key-at-least-256-bits}"

  # ECDSA Configuration (alternative)
  ecdsa-private-key: "${JWT_ECDSA_PRIVATE_KEY}"
  ecdsa-public-key: "${JWT_ECDSA_PUBLIC_KEY}"

  # Token validation
  allowed-clock-skew: "PT30S"  # 30 seconds
  require-issuer: true
  require-audience: true
  require-subject: true
  require-expiration: true
```

### Security Configuration

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: "https://auth.myapp.com"
          jwk-set-uri: "https://auth.myapp.com/.well-known/jwks.json"

          # Custom JWT decoder configuration
          audiences: "myapp-client"
          public-key-location: "classpath:jwt.pub"

          # JWT claim mappings
          principal-attribute: "sub"
          authorities-attribute: "roles"

          # Decoder configuration
          jwt-decoder-algorithm: "RS256"  # RS256, ES256, HS256

          # Cache configuration for JWK Set
          jwk-set-cache:
            cache-timeout: "PT5M"   # 5 minutes
            cache-ttl: "PT30M"      # 30 minutes

    # Session management for stateful cookie-based auth
    sessions:
      maximum-sessions: 5
      max-sessions-prevents-login: false

    # Remember-me configuration
    remember-me:
      key: "${REMEMBER_ME_KEY}"
      token-validity: 604800  # 7 days

# CORS configuration
cors:
  allowed-origins:
    - "https://app.myapp.com"
    - "https://admin.myapp.com"
  allowed-methods:
    - "GET"
    - "POST"
    - "PUT"
    - "DELETE"
    - "OPTIONS"
  allowed-headers:
    - "Authorization"
    - "Content-Type"
    - "X-Requested-With"
  allow-credentials: true
  max-age: 3600
```

## Key Management Utilities

### Key Generator for RSA

```kotlin
@Component
class RsaKeyGenerator {

    @EventListener(
        ApplicationReadyEvent.class)
            public void generateKeys () throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator . getInstance ("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator . generateKeyPair ();

        // Store keys in JKS format
        KeyStore keyStore = KeyStore . getInstance ("PKCS12");
        keyStore.load(null, null);

        char[] password = "changeit".toCharArray();
        Certificate[] chain = new Certificate[] { generateSelfSignedCertificate(keyPair) };

        keyStore.setKeyEntry("jwt", keyPair.getPrivate(), password, chain);

        // Save to file
        try (FileOutputStream fos = FileOutputStream ("jwt.jks")) {
            keyStore.store(fos, password);
        }
        }

            private fun generateSelfSignedCertificate(KeyPair keyPair): Certificate {
        // Implementation for self-signed certificate generation
        // Use Bouncy Castle or similar library
        return null;
    }
}
```

### Key Rotation Support

```kotlin
@Configuration
class KeyRotationConfig {

    @Bean
    @Primary
    fun jwtDecoder(List<JWKSet> jwkSets): JwtDecoder {
        // Merge multiple key sets for rotation support
        JWKSet mergedSet = jwkSets .. reduce (JWKSet::new)
            .orElse(JWKSet());

        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri())
            .jwsAlgorithm(JWSAlgorithm.RS256)
            .build();
    }

    @Bean
    fun currentJwkSet(@Value("${jwt.current - key - id}") String currentKeyId): JWKSet {
        // Load current key set from database or file
        return loadJwkSetFromStorage(currentKeyId);
    }

    @Bean
    fun previousJwkSet(@Value("${jwt.previous - key - id}") String previousKeyId): JWKSet {
        // Load previous key set for validation of existing tokens
        return loadJwkSetFromStorage(previousKeyId);
    }
}
```

## Custom JWT Decoder

### Enhanced JWT Decoder with Validation

```kotlin
@Component
class CustomJwtDecoder implements JwtDecoder {

    private val delegate: JwtDecoder
    private val claimsValidator: JwtClaimsValidator

    public CustomJwtDecoder (JwtDecoder delegate,
    JwtClaimsValidator claimsValidator) {
    this.delegate = delegate;
    this.claimsValidator = claimsValidator;
}

    @Override
    public Jwt decode(String token) throws JwtException {
        Jwt jwt = delegate . decode (token);

        // Validate claims
        claimsValidator.validate(jwt);

        // Add custom processing
        if (hasRequiredClaims(jwt)) {
            return jwt;
        }

        throw JwtException("JWT validation failed");
    }

    private fun hasRequiredClaims(Jwt jwt): boolean {
        return jwt.containsClaim("type") &&
                jwt.containsClaim("jti") &&
                jwt.containsClaim("sessionId");
    }
}
```

### Claims Validator

```kotlin
@Component
class JwtClaimsValidator {

    @Value("${jwt.issuer}")
    private var expectedIssuer: String

    @Value("${jwt.audience}")
    private var expectedAudience: String

    @Value("${jwt.allowed - clock - skew:PT30S}")
    private var allowedClockSkew: Duration

    fun validate(Jwt jwt): void {
        validateIssuer(jwt);
        validateAudience(jwt);
        validateExpiration(jwt);
        validateNotBefore(jwt);
        validateIssuedAt(jwt);
        validateType(jwt);
    }

    private fun validateIssuer(Jwt jwt): void {
        if (!expectedIssuer.equals(jwt.getIssuer())) {
            throw JwtException("Invalid issuer: " + jwt.getIssuer());
        }
    }

    private fun validateAudience(Jwt jwt): void {
        if (jwt.getAudience() == null ||
            !jwt.getAudience().contains(expectedAudience)
        ) {
            throw JwtException("Invalid audience");
        }
    }

    private fun validateExpiration(Jwt jwt): void {
        Instant now = Instant . now ();
        Instant exp = jwt . getExpiresAt ();

        if (exp == null || now.isAfter(exp.plus(allowedClockSkew))) {
            throw JwtException("Token expired");
        }
    }

    private fun validateNotBefore(Jwt jwt): void {
        Instant now = Instant . now ();
        Instant nbf = jwt . getNotBefore ();

        if (nbf != null && now.isBefore(nbf.minus(allowedClockSkew))) {
            throw JwtException("Token not yet valid");
        }
    }

    private fun validateIssuedAt(Jwt jwt): void {
        Instant now = Instant . now ();
        Instant iat = jwt . getIssuedAt ();

        if (iat != null && now.isBefore(iat.minus(allowedClockSkew))) {
            throw JwtException("Token issued in the future");
        }
    }

    private fun validateType(Jwt jwt): void {
        String type = jwt . getClaimAsString ("type");
        if (!"access".equals(type)) {
            throw JwtException("Invalid token type: " + type);
        }
    }
}
```