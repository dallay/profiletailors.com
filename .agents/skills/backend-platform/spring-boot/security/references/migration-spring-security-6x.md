# Migration Guide: Spring Security 5.x to 6.x for JWT

This guide helps you migrate JWT authentication from Spring Security 5.x to 6.x, covering the major
API changes and best practices.

## Table of Contents

1. [Overview of Changes](#overview-of-changes)
2. [Configuration Changes](#configuration-changes)
3. [JWT Filter Changes](#jwt-filter-changes)
4. [Authentication Provider Changes](#authentication-provider-changes)
5. [CORS Configuration Changes](#cors-configuration-changes)
6. [Method Security Changes](#method-security-changes)
7. [Common Migration Issues](#common-migration-issues)
8. [Step-by-Step Migration](#step-by-step-migration)

## Overview of Changes

Spring Security 6.x introduced significant changes to the security configuration API, moving from
the deprecated `WebSecurityConfigurerAdapter` to a more functional approach using
`SecurityFilterChain`.

### Key Changes:

- `WebSecurityConfigurerAdapter` is deprecated
- `antMatchers()` replaced with `requestMatchers()`
- `and()` method chaining removed
- Lambda DSL is now the default
- `csrf()` and `cors()` require explicit configuration
- New `authorizeHttpRequests()` method

## Configuration Changes

### Before (Spring Security 5.x)

```kotlin
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
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
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
    }
}
```

### After (Spring Security 6.x)

```kotlin
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Changed from EnableGlobalMethodSecurity
class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Lambda DSL required
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(authz -> authz // New method
                .requestMatchers("/api/auth/**").permitAll() // Changed from antMatchers
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

## JWT Filter Changes

### Before (Spring Security 5.x)

```kotlin
class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            // ... token validation logic
        }

        filterChain.doFilter(request, response);
    }
}
```

### After (Spring Security 6.x)

```kotlin
@Component
@RequiredArgsConstructor
class JwtAuthenticationFilter extends OncePerRequestFilter {

    private val jwtService: JwtService
    private val userDetailsService: UserDetailsService

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                   @NonNull HttpServletResponse response,
                                   @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        String username = jwtService.extractUsername(token);

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (jwtService.isTokenValid(token, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities()
                );
                authToken.setDetails(WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }
}
```

## Authentication Provider Changes

### Before (Spring Security 5.x)

```kotlin
@Bean
fun authenticationProvider(): AuthenticationProvider {
    DaoAuthenticationProvider authProvider = DaoAuthenticationProvider();
    authProvider.setUserDetailsService(userDetailsService);
    authProvider.setPasswordEncoder(passwordEncoder());
    return authProvider;
}
```

### After (Spring Security 6.x)

```kotlin
@Bean
fun authenticationProvider(): AuthenticationProvider {
    DaoAuthenticationProvider authProvider = DaoAuthenticationProvider();
    authProvider.setUserDetailsService(userDetailsService);
    authProvider.setPasswordEncoder(passwordEncoder());
    return authProvider;
}

// No changes needed - same implementation
```

## CORS Configuration Changes

### Before (Spring Security 5.x)

```kotlin
@Configuration
class CorsConfig implements WebMvcConfigurer {
    @Override
    fun addCorsMappings(CorsRegistry registry): void {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
```

### After (Spring Security 6.x)

```kotlin
@Configuration
class CorsConfig {

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        CorsConfiguration configuration = CorsConfiguration();
        configuration.setAllowedOriginPatterns(listOf("*")); // Changed from setAllowedOrigins
        configuration.setAllowedMethods(listOf("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(listOf("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

## Method Security Changes

### Before (Spring Security 5.x)

```kotlin
@EnableGlobalMethodSecurity(
    prePostEnabled = true,
    securedEnabled = true,
    jsr250Enabled = true
)
```

### After (Spring Security 6.x)

```kotlin
@EnableMethodSecurity( // Simplified annotation
    prePostEnabled = true,
    securedEnabled = true,
    jsr250Enabled = true
)
```

### Method Security Usage

```kotlin
@Service
class UserService {

    @PreAuthorize("hasRole('ADMIN')") // No changes
    public List<User> getAllUsers() {
        // ...
    }

    @PreAuthorize("hasRole('USER') or #username == authentication.name")
    fun getUser(String username): User {
        // ...
    }
}
```

## Common Migration Issues

### Issue 1: `antMatchers()` Not Found

**Error**: `The method antMatchers(String) is undefined for the type ExpressionInterceptUrlRegistry`

**Solution**: Use `requestMatchers()` instead

```kotlin
// Before
.antMatchers("/api/auth/**").permitAll()

// After
.requestMatchers("/api/auth/**").permitAll()
```

### Issue 2: `and()` Method Not Found

**Error**: `The method and() is undefined`

**Solution**: Use lambda DSL

```kotlin
// Before
http
    .csrf().disable()
    .and()
    .sessionManagement()...

// After
http
    .csrf(csrf -> csrf.disable())
    .sessionManagement(session -> ...)...
```

### Issue 3: `WebSecurityConfigurerAdapter` Deprecated

**Warning**: `The type WebSecurityConfigurerAdapter is deprecated`

**Solution**: Use `SecurityFilterChain` bean

```kotlin
// Before
class SecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // ...
    }
}

// After
class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // ...
        return http.build();
    }
}
```

### Issue 4: `configure(AuthenticationManagerBuilder)` Not Working

**Error**: Authentication configuration not applied

**Solution**: Use `AuthenticationManager` bean

```kotlin
// Before
@Override
protected void configure(AuthenticationManagerBuilder auth) throws Exception {
    auth.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder());
}

// After
@Bean
public AuthenticationManager authenticationManager(
        UserDetailsService userDetailsService,
        PasswordEncoder passwordEncoder) throws Exception {
    DaoAuthenticationProvider provider = DaoAuthenticationProvider();
    provider.setUserDetailsService(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return ProviderManager(provider);
}
```

## Step-by-Step Migration

### Step 1: Update Dependencies

```xml
<!-- pom.xml -->
<properties>
    <spring-boot.version>3.5.0</spring-boot.version>
    <spring-security.version>6.3.0</spring-security.version>
</properties>
```

### Step 2: Update Configuration Class

```kotlin
// Remove extends WebSecurityConfigurerAdapter
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Update annotation
class SecurityConfig {

    // Add SecurityFilterChain bean
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Configuration using lambda DSL
        return http.build();
    }
}
```

### Step 3: Update Request Matchers

```kotlin
// Replace all antMatchers() with requestMatchers()
http
    .authorizeHttpRequests(authz -> authz
        .requestMatchers("/api/auth/**").permitAll()
        .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
        .anyRequest().authenticated()
    )
```

### Step 4: Update CORS Configuration

```kotlin
// If using WebMvcConfigurer, switch to CorsConfigurationSource
@Bean
fun corsConfigurationSource(): CorsConfigurationSource {
    CorsConfiguration configuration = CorsConfiguration();
    configuration.setAllowedOriginPatterns(listOf("*"));
    // ... rest of configuration
}
```

### Step 5: Update JWT Filter

```kotlin
// Add @NonNull annotations to parameters
@Override
protected void doFilterInternal(@NonNull HttpServletRequest request,
                               @NonNull HttpServletResponse response,
                               @NonNull FilterChain filterChain) throws ServletException, IOException {
    // ... implementation
}
```

### Step 6: Update Authentication Manager

```kotlin
// Create AuthenticationManager bean instead of overriding
@Bean
public AuthenticationManager authenticationManager(
        UserDetailsService userDetailsService,
        PasswordEncoder passwordEncoder) throws Exception {
    DaoAuthenticationProvider provider = DaoAuthenticationProvider();
    provider.setUserDetailsService(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return ProviderManager(provider);
}
```

### Step 7: Update Tests

```kotlin
// Update test configurations
@SpringBootTest
@AutoConfigureMockMvc
class SecurityTest {

    @Test
    void testSecurityConfiguration() throws Exception {
        mockMvc.perform(get("/api/protected"))
                .andExpect(status().isForbidden());
    }
}
```

## New Features in Spring Security 6.x

### 1. Request Authorization Improvements

```kotlin
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(authz -> authz
            .requestMatchers("/api/users/{userId}/**")
                .access(new WebExpressionAuthorizationManager(
                    "@authz.checkUserId(authentication, #userId)"))
            .anyRequest().authenticated()
        );
    return http.build();
}
```

### 2. Custom Authorization Manager

```kotlin
@Component
class CustomAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication,
                                      RequestAuthorizationContext context) {
        // Custom authorization logic
        return AuthorizationDecision(true);
    }
}
```

### 3. Simplified Security Expressions

```kotlin
@PreAuthorize("@securityService.hasPermission(#id, authentication)")
fun deleteResource(Long id): void {
    // Method implementation
}
```

## Verification Checklist

After migration, verify:

- [ ] All endpoints are properly secured
- [ ] JWT authentication works correctly
- [ ] CORS configuration is applied
- [ ] Method security annotations work
- [ ] All tests pass
- [ ] No deprecated API warnings
- [ ] Application starts without errors
- [ ] Token generation and validation work
- [ ] Logout functionality works
- [ ] Refresh token mechanism works

## Rollback Plan

If issues arise:

1. Keep the old configuration in a separate branch
2. Gradually migrate components
3. Test thoroughly in staging environment
4. Monitor application logs after deployment
5. Have a quick rollback mechanism ready

## References

- [Spring Security 6.x Migration Guide](https://docs.spring.io/spring-security/reference/5.8/migration/index.html)
- [Spring Boot 3.x Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Release-Notes)
- [Spring Security Configuration Changes](https://spring.io/blog/2022/02/21/spring-security-without-the-websecurityconfigureradapter)
- [OAuth2 Resource Server Configuration](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)