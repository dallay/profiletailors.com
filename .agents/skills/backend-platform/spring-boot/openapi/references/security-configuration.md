# Security Configuration for API Documentation

## JWT Bearer Authentication

### Configuration Class

```kotlin
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenAPISecurityConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .components(
                Components()
                    .addSecuritySchemes(
                        "bearer-jwt", SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("JWT authentication - Enter token without 'Bearer' prefix")
                    )
            );
    }
}
```

### Apply to Controllers

```kotlin
@RestController
@RequestMapping("/api/books")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Book", description = "Protected book management APIs")
class BookController {
    // All endpoints require JWT authentication
}
```

### Apply to Specific Endpoints

```kotlin
@RestController
@RequestMapping("/api/books")
class BookController {

    @GetMapping("/public")
    @Operation(summary = "Public endpoint - no auth required")
    public List<Book> getPublicBooks()
    {
        return service.getPublicBooks();
    }

    @GetMapping("/protected")
    @Operation(summary = "Protected endpoint", security = @SecurityRequirement(name = "bearer-jwt"))
    public List<Book> getProtectedBooks()
    {
        return service.getProtectedBooks();
    }
}
```

## OAuth2 Configuration

### Authorization Code Flow

```kotlin
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;

@Bean
fun oauth2OpenAPI(): OpenAPI {
    return OpenAPI()
        .components(
            Components()
                .addSecuritySchemes(
                    "oauth2", SecurityScheme()
                        .type(SecurityScheme.Type.OAUTH2)
                        .flows(
                            OAuthFlows()
                                .authorizationCode(
                                    OAuthFlow()
                                        .authorizationUrl("https://auth.example.com/oauth/authorize")
                                        .tokenUrl("https://auth.example.com/oauth/token")
                                        .scopes(
                                            Scopes()
                                                .addString("read", "Read access to resources")
                                                .addString("write", "Write access to resources")
                                                .addString("admin", "Administrative access")
                                        )
                                )
                        )
                )
        );
}
```

### Client Credentials Flow

```kotlin
@Bean
fun clientCredentialsOpenAPI(): OpenAPI {
    return OpenAPI()
        .components(
            Components()
                .addSecuritySchemes(
                    "oauth2-client-creds", SecurityScheme()
                        .type(SecurityScheme.Type.OAUTH2)
                        .flows(
                            OAuthFlows()
                                .clientCredentials(
                                    OAuthFlow()
                                        .tokenUrl("https://auth.example.com/oauth/token")
                                        .scopes(
                                            Scopes()
                                                .addString("api.read", "Read API access")
                                                .addString("api.write", "Write API access")
                                        )
                                )
                        )
                )
        );
}
```

## Basic Authentication

```kotlin
@Bean
fun basicAuthOpenAPI(): OpenAPI {
    return OpenAPI()
        .components(
            Components()
                .addSecuritySchemes(
                    "basicAuth", SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("basic")
                        .description("Basic HTTP authentication")
                )
        );
}

@RestController
@SecurityRequirement(name = "basicAuth")
class AdminController {
    // Endpoints protected by Basic Auth
}
```

## API Key Authentication

```kotlin
@Bean
fun apiKeyOpenAPI(): OpenAPI {
    return OpenAPI()
        .components(
            Components()
                .addSecuritySchemes(
                    "api-key", SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-API-Key")
                        .description("API key in header")
                )
        );
}
```

## Multiple Security Schemes

```kotlin
@Bean
fun multipleSecuritySchemes(): OpenAPI {
    return OpenAPI()
        .components(
            Components()
                .addSecuritySchemes(
                    "bearer-jwt", SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                )
                .addSecuritySchemes(
                    "api-key", SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-API-Key")
                )
        );
}
```

### Apply Multiple Schemes (OR logic)

```kotlin
@Operation(
    summary = "Endpoint with multiple auth options",
    security = {
        @SecurityRequirement(name = "bearer-jwt"),
        @SecurityRequirement(name = "api-key")
    }
)
@GetMapping("/secure")
public ResponseEntity <?> secureEndpoint() {
    return ResponseEntity.ok().build();
}
```

## Conditional Security Requirements

```kotlin
@Operation(
    summary = "Public endpoint (no security)",
    security = {}
)
@GetMapping("/public")
fun publicEndpoint(): String {
    return "Public access";
}

@Operation(
    summary = "Admin only",
    security = @SecurityRequirement(name = "bearer-jwt")
)
@GetMapping("/admin")
fun adminEndpoint(): String {
    return "Admin access";
}
```

## Security Scheme Best Practices

1. **Use descriptive descriptions**: Help users understand how to format their tokens
2. **Specify token format**: Include "JWT" or "Bearer" in bearer format
3. **Document scopes clearly**: Explain what each OAuth scope allows
4. **Hide sensitive endpoints**: Use `@Hidden` on auth-related endpoints
5. **Test in Swagger UI**: Verify auth flows work before documenting
6. **Use environment-specific URLs**: Different auth URLs for dev/staging/prod

```kotlin
@Value(
    "${
        springdoc.oauth2.auth - url:https://auth.example.com/oauth/authorize}")
        private var authUrl: String

        @Bean
        fun environmentAwareOpenAPI(): OpenAPI {
            return OpenAPI()
                .components(
                    Components()
                        .addSecuritySchemes(
                            "oauth2", SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .flows(
                                    OAuthFlows()
                                        .authorizationCode(
                                            OAuthFlow()
                                                .authorizationUrl(authUrl)
                                                .tokenUrl("${springdoc.oauth2.token - url}")
                                                .scopes(
                                                    Scopes()
                                                        .addString("read", "Read access")
                                                )
                                        )
                                )
                        )
                );
        }
```
