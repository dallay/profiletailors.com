# Migrating from LangChain4j MCP to Spring AI MCP

Guide for migrating existing LangChain4j MCP server implementations to Spring AI.

## Key Differences

| Aspect               | LangChain4j       | Spring AI                         |
|----------------------|-------------------|-----------------------------------|
| Tool annotation      | `@ToolMethod`     | `@Tool(description)`              |
| Parameter annotation | `@P`              | `@ToolParam`                      |
| Configuration        | Custom properties | Spring Boot auto-configuration    |
| Security             | Manual            | Spring Security integration       |
| Integration          | Standalone        | Deep Spring ecosystem integration |
| Function calling     | `AiTool`          | `FunctionCallback`                |

## Migration Steps

1. Replace `@ToolMethod("description")` with `@Tool(description = "description")` on tool methods
2. Replace `@P("description")` on parameters with `@ToolParam("description")`
3. Wrap tool classes with `@Component` (LangChain4j may use different registration)
4. Update configuration from LangChain4j properties to `spring.ai.mcp.*` properties
5. Migrate prompt templates to use `@PromptTemplate` and `@PromptParam`
6. Replace LangChain4j-specific types with Spring AI equivalents
7. Add `@EnableMcpServer` to the main application class
8. Configure Spring Security if you had custom auth in LangChain4j

## Code Migration Example

**Before (LangChain4j):**

```kotlin
class WeatherTools {

    @ToolMethod("Get weather information for a city")
    fun getWeather(@P("city name") city: String): String =
        weatherService.getWeather(city)

    @ToolMethod("Get 5-day forecast")
    fun getForecast(
        @P("city name") city: String,
        @P("temperature unit") unit: String
    ): String =
        weatherService.getForecast(city, unit)
}
```

**After (Spring AI):**

```kotlin
@Component
class WeatherTools(
    private val weatherService: WeatherService
) {

    @Tool(description = "Get weather information for a city")
    fun getWeather(@ToolParam("City name") city: String): WeatherResponse =
        weatherService.getWeather(city)

    @Tool(description = "Get 5-day forecast")
    fun getForecast(
        @ToolParam("City name") city: String,
        @ToolParam(
            value = "Temperature unit: celsius or fahrenheit",
            required = false
        ) unit: String?
    ): ForecastResponse =
        weatherService.getForecast(city, unit ?: "celsius")
}
```

## Configuration Migration

**Before (LangChain4j application.properties):**

```properties
langchain4j.mcp.enabled=true
langchain4j.mcp.transport=stdio
langchain4j.openai.api-key=${OPENAI_API_KEY}
```

**After (Spring AI application.properties):**

```properties
spring.ai.mcp.enabled=true
spring.ai.mcp.transport.type=stdio
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.options.model=gpt-4o-mini
```

## Prompt Template Migration

**Before (LangChain4j):**

```kotlin
class CodePrompts {

    fun createCodeReviewPrompt(code: String): AiPrompt =
        AiPrompt.builder()
            .system("You are a code reviewer.")
            .user("Review: $code")
            .build()
}
```

**After (Spring AI):**

```kotlin
@Component
class CodePrompts {

    @PromptTemplate(
        name = "code-review",
        description = "Review Java code for best practices"
    )
    fun createCodeReviewPrompt(@PromptParam("code") code: String): Prompt =
        Prompt.builder()
            .system("You are a code reviewer.")
            .user("Review the following code:\n```java\n$code\n```")
            .build()
}
```

## Main Application Class Update

**Before:**

```kotlin
@SpringBootApplication
class MyApplication

fun main(args: Array<String>) {
    runApplication<MyApplication>(*args)
}
```

**After:**

```kotlin
@SpringBootApplication
@EnableMcpServer
class MyApplication

fun main(args: Array<String>) {
    runApplication<MyApplication>(*args)
}
```

## Benefits After Migration

- Full Spring Boot auto-configuration reduces boilerplate
- Native Spring Security integration simplifies auth
- Spring Cache integration via `@Cacheable` on tool methods
- Spring Actuator health checks out of the box
- Micrometer metrics integration
- Constructor injection for better testability
- Spring profiles for environment-specific configuration
- Deep integration with Spring Data, WebFlux, and other Spring modules
