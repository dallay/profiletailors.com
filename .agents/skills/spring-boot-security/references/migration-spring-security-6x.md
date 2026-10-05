# Migration Guide: From Spring Security 5.x servlet to reactive WebFlux

This guide documents the historical migration steps from a Spring Security 5.x
servlet stack (`HttpSecurity`, `SecurityFilterChain`, `OncePerRequestFilter`) to
the reactive WebFlux stack (`ServerHttpSecurity`, `SecurityWebFilterChain`,
`WebFilter`) used by the SMP backend. The file is intentionally kept as a
reference for engineers who must understand both shapes.

> **Status**: legacy. The blocks labelled `<!-- pre-migration -->` describe the
> servlet-stack patterns that are no longer used in production code; the blocks
> labelled `<!-- post-migration -->` describe the current reactive patterns.

## Table of Contents

1. [Overview of Changes](#overview-of-changes)
2. [Configuration Changes](#configuration-changes)
3. [JWT Filter Changes](#jwt-filter-changes)
4. [Authentication Provider Changes](#authentication-provider-changes)
5. [CORS Configuration Changes](#cors-configuration-changes)
6. [Method Security Changes](#method-security-changes)
7. [Common Migration Issues](#common-migration-issues)
8. [Step-by-Step Migration](#step-by-step-migration)
9. [New Capabilities in the Reactive Stack](#new-capabilities-in-the-reactive-stack)

## Overview of Changes

The reactive migration introduces a different programming model. Beyond the
Spring Security 5.x → 6.x servlet-API changes (which are summarised in the
`<!-- pre-migration -->` blocks for historical context), the move to WebFlux
replaces every servlet binding with its reactive equivalent.

### Key Reactive Changes

| Servlet API                                  | WebFlux reactive equivalent                             |
|----------------------------------------------|---------------------------------------------------------|
| `WebSecurityConfigurerAdapter`               | (removed) `SecurityWebFilterChain` bean                 |
| `HttpSecurity`                               | `ServerHttpSecurity`                                    |
| `SecurityFilterChain`                        | `SecurityWebFilterChain`                                |
| `OncePerRequestFilter`                       | `WebFilter` (implements `Mono<Void>`)                   |
| `HttpServletRequest` / `HttpServletResponse` | `ServerWebExchange` / `ServerHttpRequest`/`Response`    |
| `FilterChain.doFilter(req, res)`             | `chain.filter(exchange).then()`                         |
| `SecurityContextHolder.getContext()`         | `exchange.exchange.getPrincipal<Authentication>()`      |
| `UsernamePasswordAuthenticationFilter`       | `SecurityWebFiltersOrder.HTTP_BASIC` (or a filter slot) |
| `AuthenticationManager`                      | `ReactiveAuthenticationManager`                         |
| `AuthenticationProvider`                     | `ReactiveAuthenticationProvider` (Coroutine variant)    |
| `UserDetailsService`                         | `ReactiveUserDetailsService`                            |
| `requestMatchers(...)` (path matcher)        | `pathMatchers(...)`                                     |
| `antMatchers(...)`                           | `pathMatchers(...)`                                     |
| `authorizeHttpRequests(...)`                 | `authorizeExchange(...)`                                |
| `csrf(...)`                                  | `csrf { ... }` (same DSL, runs on `ServerHttpSecurity`) |
| `oauth2ResourceServer(...)`                  | same DSL, runs against `ServerHttpSecurity`             |
| `@EnableMethodSecurity`                      | `@EnableReactiveMethodSecurity`                         |
| `MockMvc`                                    | `WebTestClient`                                         |
| `@AutoConfigureMockMvc`                      | `@AutoConfigureWebTestClient`                           |
| `@MockBean`                                  | `@MockkBean` (`com.ninja-squad:springmockk`)            |
| `@WebMvcTest`                                | `@WebFluxTest`                                          |
| `JdbcTemplate` / `JpaRepository`             | `DatabaseClient` / `CoroutineCrudRepository`            |
| `spring.datasource.*` properties             | `spring.r2dbc.*` properties                             |
| `starter-data-jpa` / `starter-web`           | `starter-data-r2dbc` / `starter-webflux`                |

## Configuration Changes

<!-- pre-migration: Spring Security 5.x servlet configuration -->

```kotlin
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
class SecurityConfig : WebSecurityConfigurerAdapter() {

    override fun configure(http: HttpSecurity) {
        http
            .csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeRequests()
            .antMatchers("/api/auth/**").permitAll()
            .antMatchers("/api/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated()
            .and()
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
    }
}
```

<!-- post-migration: reactive WebFlux configuration -->

```kotlin
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
class SecurityConfig(
    private val authenticationManager: ReactiveAuthenticationManager,
    private val jwtAuthenticationFilter: JwtAuthenticationWebFilter,
) {
    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain = http
        .csrf { it.disable() }
        .anonymous { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeExchange { auth ->
            auth
                .pathMatchers("/api/auth/**").permitAll()
                .pathMatchers("/api/admin/**").hasRole("ADMIN")
                .anyExchange().authenticated()
        }
        .authenticationManager(authenticationManager)
        .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.HTTP_BASIC)
        .build()
}
```

## JWT Filter Changes

<!-- pre-migration: servlet OncePerRequestFilter -->

```kotlin
class JwtAuthenticationFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val token = authHeader.substring(7)
        }

        filterChain.doFilter(request, response)
    }
}
```

<!-- post-migration: reactive WebFilter -->

```kotlin
@Component
class JwtAuthenticationWebFilter(
    private val jwtService: JwtService,
    private val userDetailsService: ReactiveUserDetailsService,
) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val authHeader = exchange.request.headers.header("Authorization").firstOrNull()

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange)
        }

        val token = authHeader.substring(7)
        return chain.filter(exchange)
    }
}
```

## Authentication Provider Changes

<!-- pre-migration: servlet DaoAuthenticationProvider -->

```kotlin
@Bean
fun authenticationProvider(): AuthenticationProvider {
    val authProvider = DaoAuthenticationProvider()
    authProvider.setUserDetailsService(userDetailsService())
    authProvider.setPasswordEncoder(passwordEncoder())
    return authProvider
}
```

<!-- post-migration: reactive DaoReactiveAuthenticationProvider -->

```kotlin
@Bean
fun authenticationManager(
    userDetailsService: ReactiveUserDetailsService,
    passwordEncoder: PasswordEncoder,
): ReactiveAuthenticationManager {
    val provider = DaoReactiveAuthenticationProvider(userDetailsService)
    provider.setPasswordEncoder(passwordEncoder)
    return ProviderManagerReactiveAuthenticationManager(provider)
}
```

## CORS Configuration Changes

<!-- pre-migration: WebMvcConfigurer-based CORS registration -->

```kotlin
@Configuration
class CorsConfig : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOrigins("*")
            .allowedMethods("GET", "POST", "PUT", "DELETE")
            .allowedHeaders("*")
            .allowCredentials(true)
    }
}
```

<!-- post-migration: reactive CorsConfigurationSource -->

```kotlin
@Configuration
class CorsConfig {
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOriginPatterns = listOf("*")
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = 3600L
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }
}
```

## Method Security Changes

<!-- pre-migration: Spring Security 5.x annotations -->

```kotlin
@EnableGlobalMethodSecurity(
    prePostEnabled = true,
    securedEnabled = true,
    jsr250Enabled = true,
)
```

<!-- post-migration: reactive annotations -->

```kotlin
@EnableReactiveMethodSecurity(
    prePostEnabled = true,
    securedEnabled = true,
    jsr250Enabled = true,
)
```

### Method Security Usage in Coroutine Services

<!-- pre-migration: sync service annotated with @PreAuthorize -->

```kotlin
@Service
class UserService {

    @PreAuthorize("hasRole('ADMIN')")
    fun getAllUsers(): List<User> = emptyList()

    @PreAuthorize("hasRole('USER') or #username == authentication.name")
    fun getUser(username: String): User = TODO()
}
```

<!-- post-migration: reactive coroutine service with the same annotations -->

```kotlin
@com.profiletailors.common.domain.Service
class UserService {
    @PreAuthorize("hasRole('ADMIN')")
    suspend fun listAll(): List<UserAccount> = userRepository.findAll().toList()

    @PreAuthorize("hasRole('USER') or #username == authentication.name")
    suspend fun findByUsername(username: String): UserAccount? = userRepository.findByEmail(username)
}
```

`@PreAuthorize` works the same way; the only change is the surrounding function
is now `suspend` and the principal comes from the reactive security context.

## Common Migration Issues

### Issue 1: Ant matchers unavailable in WebFlux

<!-- pre-migration: servlet request matchers -->

```kotlin
.antMatchers("/api/auth/**").permitAll()
```

<!-- post-migration: reactive path matchers -->

```kotlin
.pathMatchers("/api/auth/**").permitAll()
```

### Issue 2: Chaining returning `null`

<!-- pre-migration: servlet chaining ends with .and() -->

```kotlin
http
    .csrf().disable()
    .and()
    .sessionManagement()...
```

<!-- post-migration: lambda DSL with explicit returns -->

```kotlin
http
    .csrf { it.disable() }
    .sessionManagement { ... }
    .build()
```

### Issue 3: `WebSecurityConfigurerAdapter` deprecation

<!-- pre-migration: subclassing the deprecated adapter -->

```kotlin
class SecurityConfig : WebSecurityConfigurerAdapter() {
    override fun configure(http: HttpSecurity) { /* ... */ }
}
```

<!-- post-migration: declaring a SecurityWebFilterChain bean -->

```kotlin
class SecurityConfig {
    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
        http.authorizeExchange { it.anyExchange().authenticated() }.build()
}
```

### Issue 4: `AuthenticationManagerBuilder` not wired

<!-- pre-migration: configure(AuthenticationManagerBuilder) -->

```kotlin
override fun configure(auth: AuthenticationManagerBuilder) {
    auth.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder())
}
```

<!-- post-migration: explicit ReactiveAuthenticationManager bean -->

```kotlin
@Bean
fun authenticationManager(
    userDetailsService: ReactiveUserDetailsService,
    passwordEncoder: PasswordEncoder,
): ReactiveAuthenticationManager = ProviderManagerReactiveAuthenticationManager(
    DaoReactiveAuthenticationProvider(userDetailsService).apply {
        setPasswordEncoder(passwordEncoder)
    },
)
```

## Step-by-Step Migration

### Step 1: Update Dependencies

<!-- pre-migration: starter-web + starter-data-jpa -->

```xml
<properties>
  <spring-boot.version>3.5.0</spring-boot.version>
  <spring-security.version>6.3.0</spring-security.version>
</properties>

<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

<!-- post-migration: starter-webflux + starter-data-r2dbc -->

```kotlin
implementation("org.springframework.boot:spring-boot-starter-webflux")
implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
```

Exact versions live in `gradle/libs.versions.toml`.

### Step 2: Convert the configuration class

<!-- pre-migration: SecurityFilterChain bean on HttpSecurity -->

```kotlin
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityWebFilterChain =
        http
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .build()
}
```

<!-- post-migration: SecurityWebFilterChain bean on ServerHttpSecurity -->

```kotlin
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
class SecurityConfig(
    private val authenticationManager: ReactiveAuthenticationManager,
) {
    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
        http
            .authorizeExchange { it.anyExchange().authenticated() }
            .authenticationManager(authenticationManager)
            .build()
}
```

### Step 3: Convert path matchers

<!-- pre-migration: servlet requestMatchers() -->

```kotlin
http
    .authorizeHttpRequests { authz ->
        authz
            .requestMatchers("/api/auth/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
            .anyRequest().authenticated()
    }
```

<!-- post-migration: reactive pathMatchers() / authorizeExchange() -->

```kotlin
http
    .authorizeExchange { authz ->
        authz
            .pathMatchers("/api/auth/**").permitAll()
            .pathMatchers(HttpMethod.GET, "/api/public/**").permitAll()
            .anyExchange().authenticated()
    }
```

### Step 4: Convert CORS

<!-- pre-migration: WebMvcConfigurer -->

```kotlin
@Configuration
class CorsConfig : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) { /* ... */ }
}
```

<!-- post-migration: reactive CorsConfigurationSource bean -->

```kotlin
@Configuration
class CorsConfig {
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource { /* ... */ }
}
```

### Step 5: Convert JWT filter

<!-- pre-migration: OncePerRequestFilter -->

```kotlin
class JwtAuthenticationFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        @NonNull request: HttpServletRequest,
        @NonNull response: HttpServletResponse,
        @NonNull filterChain: FilterChain,
    ) { /* ... */ }
}
```

<!-- post-migration: WebFilter returning Mono<Void> -->

```kotlin
@Component
class JwtAuthenticationWebFilter : WebFilter {
    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> = chain.filter(exchange)
}
```

### Step 6: Convert authentication manager

<!-- pre-migration: AuthenticationManager bean from ProviderManager -->

```kotlin
@Bean
fun authenticationManager(
    userDetailsService: UserDetailsService,
    passwordEncoder: PasswordEncoder,
): AuthenticationManager {
    val provider = DaoAuthenticationProvider()
    provider.setUserDetailsService(userDetailsService)
    provider.setPasswordEncoder(passwordEncoder)
    return ProviderManager(provider)
}
```

<!-- post-migration: ReactiveAuthenticationManager bean -->

```kotlin
@Bean
fun authenticationManager(
    userDetailsService: ReactiveUserDetailsService,
    passwordEncoder: PasswordEncoder,
): ReactiveAuthenticationManager =
    ProviderManagerReactiveAuthenticationManager(
        DaoReactiveAuthenticationProvider(userDetailsService).apply {
            setPasswordEncoder(passwordEncoder)
        },
    )
```

### Step 7: Convert tests

<!-- pre-migration: MockMvc + @MockBean + JUnit Jupiter + Mockito -->

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
class SecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun protectedEndpoint_returnsForbiddenWhenUnauthenticated() {
        mockMvc.perform(get("/api/protected"))
            .andExpect(status().isForbidden)
    }
}
```

<!-- post-migration: WebTestClient + @MockkBean + Kotest + MockK -->

```kotlin
@SpringBootTest
@AutoConfigureWebTestClient
class SecurityTest : StringSpec({
    "protected endpoint returns 401 when unauthenticated" {
        webTestClient.get().uri("/api/protected")
            .exchange()
            .expectStatus().isUnauthorized
    }
})
```

## New Capabilities in the Reactive Stack

### Reactive Path Authorization with `AuthorizationManager`

<!-- post-migration: ReactiveAuthorizationManager binding -->

```kotlin
@Component
class PathAuthorizationManager(userRepository: UserRepository) : ReactiveAuthorizationManager<AuthorizationContext> {
    override fun check(
        authentication: Mono<Authentication>,
        context: AuthorizationContext,
    ): Mono<AuthorizationDecision> = authentication.zipWith(
        Mono.justOrEmpty(context.variables["userId"]?.toString()),
    ).flatMap { (auth, userId) ->
        userRepository.findById(userId.toLong())
            .map { auth.principal == it || it.roles.contains("ADMIN") }
            .defaultIfEmpty(false)
            .map(::AuthorizationDecision)
    }
}

http.authorizeExchange { auth ->
    auth.pathMatchers("/api/users/{userId}/**")
        .access(PathAuthorizationManager(userRepository))
        .anyExchange().authenticated()
}
```

### Coroutine Application Services

<!-- pre-migration: synchronous service with @PreAuthorize -->

```kotlin
@Service
class ResourceService {
    @PreAuthorize("@securityService.hasPermission(#id, authentication)")
    fun deleteResource(id: Long) {
        resourceRepository.deleteById(id)
    }
}
```

<!-- post-migration: coroutine service with the same authorization -->

```kotlin
@com.profiletailors.common.domain.Service
class ResourceService {
    @PreAuthorize("@securityService.hasPermission(#id, authentication)")
    suspend fun deleteResource(id: ResourceId) {
        resourceRepository.delete(id)
    }
}
```

### Reactive Security Expressions

<!-- pre-migration: same expression syntax -->

```kotlin
@PreAuthorize("@securityService.hasPermission(#id, authentication)")
fun deleteResource(id: Long) { /* ... */ }
```

<!-- post-migration: same expression, but with suspend coroutine -->

```kotlin
@PreAuthorize("@securityService.hasPermission(#id, authentication)")
suspend fun deleteResource(id: ResourceId) { /* ... */ }
```

## Verification Checklist

After the migration, verify:

- [ ] All endpoints are properly secured through `pathMatchers(...)`
- [ ] JWT authentication works end-to-end through the WebFlux resource server
- [ ] CORS configuration is applied through `CorsConfigurationSource`
- [ ] `@PreAuthorize`/`@PostAuthorize` work on `suspend fun` methods
- [ ] All tests pass using `WebTestClient` and Kotest
- [ ] No deprecated API references remain in production code
- [ ] Application starts without WebFlux/Servlet mixing warnings
- [ ] Token generation, refresh, and revocation work in the reactive flow
- [ ] Logout clears the reactive `SecurityContext`
- [ ] Refresh token rotation issues new access tokens correctly

## Rollback Plan

If issues arise after the migration:

1. Keep a branch with the previous servlet configuration for emergency reference
2. Roll back bounded contexts one at a time, starting from the leaf contexts
3. Run integration tests through `WebTestClient` and BDD lanes per context
4. Monitor application logs and metrics after each rollout
5. Have the previous version ready as a fallback container image

## References

- [Spring Security WebFlux Reference](https://docs.spring.io/spring-security/reference/reactive/index.html)
- [Spring Security 6.x Migration Guide](https://docs.spring.io/spring-security/reference/5.8/migration/index.html)
- [Spring Boot WebFlux Reference](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html)
- [Reactive OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/reactive/oauth2/resource-server/index.html)
- [Spring Modulith Reactive](https://docs.spring.io/spring-modulith/reference/)
