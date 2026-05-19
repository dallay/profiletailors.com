---
name: spring-boot-security
description: Use when implementing reactive authentication or authorization in Spring Boot 4 with Spring Security, JWT bearer or cookie flows, RBAC or permission checks, token rotation, revocation, or OAuth2/resource server integration.
allowed-tools: Read, Write, Edit, Bash, Glob, Grep
---

# Spring Boot Reactive Security

Reactive authentication and authorization patterns for **Spring Boot 4 + WebFlux + Kotlin
coroutines**.

This skill is the canonical security companion for the backend stack. It covers reactive security
configuration, JWT/resource-server flows, role/permission checks, token lifecycle concerns, and test
strategies that align with `WebFlux` instead of the servlet stack.

## Stack Baseline

These assumptions are canonical unless a project documents an explicit exception:

- **Spring Security model:** WebFlux security
- **HTTP security chain:** `SecurityWebFilterChain`
- **Configuration API:** `ServerHttpSecurity`
- **Primary auth strategy:** Bearer JWT or OAuth2 resource server
- **Testing baseline:** `WebTestClient` and reactive security test support
- **Persistence note:** if refresh tokens or revocation state are stored, prefer reactive adapters

## When to Use

Use this skill when requests involve:

- implementing JWT authentication in a reactive API
- securing WebFlux endpoints
- configuring `SecurityWebFilterChain`
- role-based or permission-based access control
- token rotation, revocation, or refresh flows
- OAuth2 resource server integration
- reactive method security
- cookie-vs-bearer token tradeoffs for SPA/backend integrations

## What This Skill Does Not Own

Use companion skills instead when the main concern is:

- core DTO/controller/infrastructure boundaries → `spring-boot`
- HTTP contract and status design → `spring-boot-api-standards`
- reactive controller/security verification → `spring-boot-testing-webflux`

## Core Rules

- Use `SecurityWebFilterChain`, not servlet `SecurityFilterChain`.
- Use `ServerHttpSecurity`, not `HttpSecurity`.
- Do **not** use `OncePerRequestFilter` as the default JWT pattern in WebFlux.
- Keep authn/authz concerns in infrastructure.
- Use `oauth2ResourceServer().jwt()` when bearer JWT is the primary model.
- Use method security intentionally; do not hide API-level authorization mistakes behind service
  annotations alone.
- Treat refresh tokens, blacklisting, and revocation as persistence problems as much as security
  problems.

## Dependencies

| Artifact                                     | Scope   |
|----------------------------------------------|---------|
| `spring-boot-starter-security`               | compile |
| `spring-boot-starter-oauth2-resource-server` | compile |
| `spring-security-test`                       | test    |
| `io.jsonwebtoken:jjwt-api`                   | compile |
| `io.jsonwebtoken:jjwt-impl`                  | runtime |
| `io.jsonwebtoken:jjwt-jackson`               | runtime |

Use JJWT only if the application is responsible for minting tokens. If the application only
validates
bearer JWTs from an external issuer, the resource-server stack may be enough.

## Configuration Properties

```yaml
jwt:
  issuer: profile-tailors
  access-token-expiration: 900000
  refresh-token-expiration: 604800000
  cookie-name: pt-access-token
  cookie-http-only: true
  cookie-secure: true
```

### Rules

- Never hardcode secrets.
- Use secure cookies in production.
- Validate issuer/audience consistently.
- Keep expiration settings explicit and environment-aware.

## Reactive Security Configuration

Use a reactive security chain as the default baseline.

```kotlin
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
class SecurityConfiguration {

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
        http
            .csrf { it.disable() }
            .authorizeExchange {
                it.pathMatchers(
                    "/actuator/health",
                    "/api/auth/**",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                ).permitAll()
                it.anyExchange().authenticated()
            }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt()
            }
            .build()
}
```

## Token Strategies

## 1. Bearer JWT in Authorization Header

Use this when:

- clients are API-first
- mobile/native integrations dominate
- token transport should stay explicit

### Rules

- Expect `Authorization: Bearer <token>`.
- Reject malformed or expired tokens consistently.
- Keep claims minimal and stable.

## 2. HttpOnly Cookie Token Transport

Use this when:

- browser clients dominate
- you want to reduce token handling in JavaScript
- CSRF implications are understood and handled intentionally

### Rules

- Use `HttpOnly`, `Secure`, and appropriate `SameSite` settings.
- Document how browser and API clients differ.
- Do not pretend cookie transport removes all security concerns.

## JWT Service Pattern

If the application issues JWTs itself, centralize signing and claim rules.

```kotlin
@Service
class JwtService(
    private val jwtProperties: JwtProperties,
) {
    fun generateAccessToken(subject: String, authorities: Collection<String>): String =
        Jwts.builder()
            .subject(subject)
            .issuer(jwtProperties.issuer)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + jwtProperties.accessTokenExpiration))
            .claim("authorities", authorities)
            .signWith(signingKey())
            .compact()
}
```

### Rules

- Keep claim names stable.
- Do not put sensitive data in JWT payloads.
- Prefer short-lived access tokens.
- If refresh is needed, rotate refresh tokens instead of reusing them forever.

## Refresh Token and Revocation Guidance

### Rules

- Store refresh-token state server-side if revocation matters.
- Prefer rotation on refresh.
- Mark tokens revoked on logout or compromise.
- If using Redis or a database for revocation, keep that adapter reactive when possible.

### Minimal persistence model

Track fields such as:

- token identifier (`jti`)
- subject/user id
- expiry
- revoked flag
- issued-at metadata

## Authorization Patterns

### Role-based checks

```kotlin
@PreAuthorize("hasRole('ADMIN')")
suspend fun listAllUsers(): List<UserResponse> = TODO()
```

### Permission-based checks

```kotlin
@PreAuthorize("hasAuthority('WORKSPACE_READ')")
suspend fun getWorkspace(id: UUID): WorkspaceResponse = TODO()
```

### Rules

- Prefer coarse-grained endpoint rules first.
- Use method security for deeper business authorization only when it adds clarity.
- Keep authority naming explicit and grep-friendly.

## Reactive User Lookup Guidance

If security depends on application-managed users/permissions:

- prefer reactive data access
- avoid bridging to blocking persistence adapters inside request-time auth flows
- isolate blocking lookups if a legacy adapter is unavoidable

## OAuth2 / Resource Server Guidance

Use resource-server mode when tokens come from an identity provider.

### Rules

- Prefer issuer/jwk-set based validation over custom parsing when possible.
- Keep audience and issuer validation explicit.
- Separate identity-provider config from business authorization rules.

## Security Testing Baseline

Use reactive HTTP tests for endpoint-level access control.

```kotlin
@WebFluxTest(AdminController::class)
@Import(SecurityConfiguration::class)
class AdminControllerSecurityTest(
    @Autowired private val webTestClient: WebTestClient,
) {
    @Test
    fun `rejects anonymous access`() {
        webTestClient.get()
            .uri("/api/admin/users")
            .exchange()
            .expectStatus().isUnauthorized
    }
}
```

### Testing Rules

- Test anonymous, forbidden, and authorized paths.
- Use `WebTestClient`, not `MockMvc`.
- Keep HTTP security tests and method-security tests distinct when useful.
- Verify error status and body shape for denied access when the API contract exposes them.

## Common Mistakes

- ❌ Using `OncePerRequestFilter` as the default JWT model in WebFlux
- ❌ Configuring `HttpSecurity` in a reactive application
- ❌ Doing blocking repository lookups in the hot authentication path
- ❌ Treating refresh-token storage as an afterthought
- ❌ Relying only on `@PreAuthorize` while endpoint rules remain too broad
- ❌ Testing security only with `MockMvc`
- ❌ Returning internal auth failure details to clients

## Related Skills

- [`../SKILL.md`](../SKILL.md) — Core reactive Spring Boot boundaries and wiring
- `spring-boot-api-standards` — HTTP contract, status code, and error response design
- `spring-boot-testing-webflux` — Reactive controller and security verification
