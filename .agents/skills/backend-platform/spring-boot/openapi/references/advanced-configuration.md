# Advanced SpringDoc Configuration

## Multiple API Groups

### Group by Path

```kotlin
import org.springdoc.core.models.GroupedOpenApi;

@Bean
fun publicApi(): GroupedOpenApi {
    return GroupedOpenApi.builder()
        .group("public")
        .pathsToMatch("/api/public/**")
        .build();
}

@Bean
fun adminApi(): GroupedOpenApi {
    return GroupedOpenApi.builder()
        .group("admin")
        .pathsToMatch("/api/admin/**")
        .build();
}

@Bean
fun userApi(): GroupedOpenApi {
    return GroupedOpenApi.builder()
        .group("user")
        .pathsToMatch("/api/user/**")
        .build();
}
```

### Group by Package

```kotlin
@Bean
fun controllerGroup(): GroupedOpenApi {
    return GroupedOpenApi.builder()
        .group("controllers")
        .packagesToScan("com.example.controller")
        .build();
}

@Bean
fun controllerGroup2(): GroupedOpenApi {
    return GroupedOpenApi.builder()
        .group("vendor-controllers")
        .packagesToScan("com.vendor.controller")
        .build();
}
```

### Group with Custom Configuration

```kotlin
@Bean
fun customGroup(): GroupedOpenApi {
    return GroupedOpenApi.builder()
        .group("custom")
        .pathsToMatch("/api/custom/**")
        .addOpenApiMethodFilter(method -> method.isAnnotationPresent(CustomApi.class))
        .build();
}
```

## Custom Operation Customizer

### Global Operation Customization

```kotlin
import org.springdoc.core.customizers.OperationCustomizer;

@Bean
fun customizeOperation(): OperationCustomizer {
    return (operation, handlerMethod) -> {
        // Add custom extension
        operation.addExtension("x-custom-field", "custom-value");

        // Add tag based on annotation
        if (handlerMethod.getMethod().isAnnotationPresent(Deprecated.class)) {
            operation.addTagsItem("deprecated");
        }

        // Customize summary
        String className = handlerMethod.getBeanType().getSimpleName();
        operation.setSummary(className + ": " + operation.getSummary());

        return operation;
    };
}
```

### Conditional Customization

```kotlin
@Bean
fun authOperationCustomizer(): OperationCustomizer {
    return (operation, handlerMethod) -> {
        // Add security requirement for methods with @RequireAuth
        if (handlerMethod.hasMethodAnnotation(RequireAuth.class)) {
            operation.addSecurityItem(SecurityRequirement().addList("bearer-jwt"));
        }
        return operation;
    };
}
```

## Hide Endpoints

### Hide Single Endpoint

```kotlin
@Operation(hidden = true)
@GetMapping("/internal")
fun internalEndpoint(): String {
    return "Hidden from docs";
}
```

### Hide Entire Controller

```kotlin
import io.swagger.v3.oas.annotations.Hidden;

@Hidden
@RestController
@RequestMapping("/internal")
class InternalController {
    // All endpoints hidden from documentation
}
```

### Conditional Hiding

```kotlin
@Bean
fun conditionalHiding(): OperationCustomizer {
    return (operation, handlerMethod) -> {
        // Hide endpoints based on profile
        if (isProductionProfile()) {
            if (handlerMethod.getMethod().getName().contains("Debug")) {
                operation.setHidden(true);
            }
        }
        return operation;
    };
}
```

## Custom OpenAPI Bean

### Complete OpenAPI Configuration

```kotlin
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;

@Bean
fun customOpenAPI(): OpenAPI {
    return OpenAPI()
        .info(Info()
            .title("Book Management API")
            .description("Comprehensive API for managing books, authors, and publishers")
            .version("v1.0.0")
            .contact(Contact()
                .name("API Support")
                .email("support@example.com")
                .url("https://example.com/support")
            )
            .license(License()
                .name("MIT License")
                .url("https://opensource.org/licenses/MIT")
            )
        )
        .addServersItem(Server()
            .url("https://api.example.com")
            .description("Production server")
        )
        .addServersItem(Server()
            .url("https://staging-api.example.com")
            .description("Staging server")
        )
        .addServersItem(Server()
            .url("http://localhost:8080")
            .description("Development server")
        );
}
```

### Environment-Specific Configuration

```kotlin
@Value("${api.version:v1.0.0}")
private var apiVersion: String

@Value("${api.title:My API}")
private var apiTitle: String

@Profile("production")
@Bean
fun prodOpenAPI(): OpenAPI {
    return OpenAPI()
        .info(Info()
            .title(apiTitle)
            .version(apiVersion)
            .description("Production API")
        )
        .addServersItem(Server().url("https://api.example.com"));
}

@Profile("development")
@Bean
fun devOpenAPI(): OpenAPI {
    return OpenAPI()
        .info(Info()
            .title(apiTitle + " (DEV)")
            .version(apiVersion)
            .description("Development API")
        )
        .addServersItem(Server().url("http://localhost:8080"));
}
```

## Custom Server Configuration

### Multiple Servers with Variables

```kotlin
@Bean
fun serversOpenAPI(): OpenAPI {
    Server prodServer = Server()
        .url("https://{environment}.example.com:{port}/api")
        .description("Production server")
        .addVariable("environment", ServerVariable()
            .defaultValue("api")
            .enumeration(listOf("api", "api-staging"))
            .description("Server environment")
        )
        .addVariable("port", ServerVariable()
            .defaultValue("443")
            .description("Server port")
        );

    return OpenAPI().addServersItem(prodServer);
}
```

## Custom Tags

### Dynamic Tag Configuration

```kotlin
@Bean
fun customTagsOpenAPI(): OpenAPI {
    return OpenAPI()
        .tags(Arrays.asList(
            Tag()
                .name("public")
                .description("Publicly accessible endpoints")
                .externalDocs(ExternalDocumentation()
                    .description("Public API documentation")
                    .url("https://docs.example.com/public")
                ),
            Tag()
                .name("admin")
                .description("Administrative endpoints")
                .externalDocs(ExternalDocumentation()
                    .description("Admin guide")
                    .url("https://docs.example.com/admin")
                )
        ));
}
```

## Custom Properties

### Adding Custom Extensions

```kotlin
@Bean
fun addCustomExtensions(): OperationCustomizer {
    return (operation, handlerMethod) -> {
        // Add rate limit info
        operation.addExtension("x-rate-limit", 100);

        // Add cost info
        operation.addExtension("x-cost", 1);

        // Add deprecation notice
        if (handlerMethod.getMethod().isAnnotationPresent(Deprecated.class)) {
            operation.addExtension("x-deprecated-since", "v1.0");
            operation.addExtension("x-removal-date", "2025-01-01");
        }

        return operation;
    };
}
```

## Custom Response Headers

### Documenting Response Headers

```kotlin
@Operation(
    summary = "Get book with headers",
    responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Book found",
            headers = {
                @Header(name = "X-RateLimit-Remaining", description = "Remaining API calls", schema = @Schema(type = "integer")),
                @Header(name = "X-RateLimit-Reset", description = "Rate limit reset time", schema = @Schema(type = "string"))
            }
        )
    }
)
@GetMapping("/{id}")
fun getBook(@PathVariable Long id): Book {
    return repository.findById(id).orElseThrow();
}
```

## WebFlux Configuration

### Reactive Router Function Documentation

```kotlin
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

@Bean
public RouterFunction<ServerResponse> bookRouter(BookHandler handler) {
    return RouterFunctions.route()
        .GET("/api/books", handler::getAllBooks)
        .GET("/api/books/{id}", handler::getBookById)
        .POST("/api/books", handler::createBook)
        .build();
}
```

## Kotlin Support

### Kotlin DSL Configuration

```kotlin
@Bean
fun customOpenAPI(): OpenAPI {
    return OpenAPI()
        .info(Info()
            .title("Kotlin API")
            .version("1.0.0")
            .description("API built with Kotlin and Spring Boot")
        )
}
```
