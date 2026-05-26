---
name: spring-boot-ai-mcp-server-patterns
description: Use when building Model Context Protocol servers in Spring Boot with Spring AI, exposing tools or resources, configuring transports, or integrating AI tool-calling workflows.
allowed-tools: Read, Write, Edit, Bash, Glob, Grep
---

# Spring AI MCP Server Implementation Patterns

Implements MCP servers with Spring AI for AI function calling, tool handlers, and MCP transport
configuration.

## Overview

Production-ready MCP server patterns: `@Tool` functions, `@PromptTemplate` resources, and
stdio/HTTP/SSE transports with Spring AI security.

## When to Use

MCP servers, Spring AI function calling, AI tools, tool calling, custom tool handlers, Spring Boot
MCP, resource endpoints, or MCP transport configuration.

## Quick Reference

### Core Annotations

| Annotation              | Target    | Purpose                              |
|-------------------------|-----------|--------------------------------------|
| `@EnableMcpServer`      | Class     | Enable MCP server auto-configuration |
| `@Tool(description)`    | Method    | Declare AI-callable tool             |
| `@ToolParam(value)`     | Parameter | Document tool parameter for AI       |
| `@PromptTemplate(name)` | Method    | Declare reusable prompt template     |
| `@PromptParam(value)`   | Parameter | Document prompt parameter            |

### Transport Types

| Transport | Use Case                       | Config         |
|-----------|--------------------------------|----------------|
| `stdio`   | Local process / Claude Desktop | Default        |
| `http`    | Remote HTTP clients            | `port`, `path` |
| `sse`     | Real-time streaming clients    | `port`, `path` |

### Key Dependencies

```xml
<!-- Maven -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-server</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
    <version>1.0.0</version>
</dependency>
```

```gradle
// Gradle
implementation 'org.springframework.ai:spring-ai-mcp-server:1.0.0'
implementation 'org.springframework.ai:spring-ai-starter-model-openai:1.0.0'
```

## Instructions

### 1. Project Setup

Add Spring AI MCP dependencies (see Quick Reference above), configure the AI model in
`application.properties`, and enable MCP with `@EnableMcpServer`:

```kotlin
@SpringBootApplication
@EnableMcpServer
class MyMcpApplication

fun main(args: Array<String>) {
    runApplication<MyMcpApplication>(*args)
}
```

```properties
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.mcp.enabled=true
spring.ai.mcp.transport.type=stdio
```

### 2. Define Tools

Annotate methods with `@Tool` inside `@Component` beans. Use `@ToolParam` to document parameters:

```kotlin
@Component
class WeatherTools {

    @Tool(description = "Get current weather for a city")
    fun getWeather(@ToolParam("City name") city: String): WeatherData {
        return weatherService.getCurrentWeather(city)
    }

    @Tool(description = "Get 5-day forecast for a city")
    fun getForecast(
        @ToolParam("City name") city: String,
        @ToolParam(value = "Unit: celsius or fahrenheit", required = false) unit: String?
    ): ForecastData {
        return weatherService.getForecast(city, unit ?: "celsius")
    }
}
```

See [references/implementation-patterns.md](references/implementation-patterns.md) for database
tools, API integration tools, and the `FunctionCallback` low-level pattern.

### 3. Create Prompt Templates

```kotlin
@Component
class CodeReviewPrompts {

    @PromptTemplate(
        name = "kotlin-code-review",
        description = "Review Kotlin code for best practices and issues"
    )
    fun createCodeReviewPrompt(
        @PromptParam("code") code: String,
        @PromptParam(value = "focusAreas", required = false) focusAreas: List<String>?
    ): Prompt {
        val focus = focusAreas?.joinToString(", ") ?: "general best practices"
        return Prompt.builder()
            .system("You are an expert Kotlin code reviewer with 20 years of experience.")
            .user("Review the following Kotlin code for $focus:\n```kotlin\n$code\n```")
            .build()
    }
}
```

See [references/implementation-patterns.md](references/implementation-patterns.md) for additional
prompt template patterns.

### 4. Configure Transport

```yaml
spring:
  ai:
    mcp:
      enabled: true
      transport:
        type: stdio       # stdio | http | sse
        http:
          port: 8080
          path: /mcp
      server:
        name: my-mcp-server
        version: 1.0.0
```

### 5. Add Security

```kotlin
@Configuration
class McpSecurityConfig {

    @Bean
    fun toolFilter(securityService: SecurityService): ToolFilter {
        return ToolFilter { tool, context ->
            val user = securityService.getCurrentUser()
            if (tool.name().startsWith("admin_")) {
                user.hasRole("ADMIN")
            } else {
                securityService.isToolAllowed(user, tool.name())
            }
        }
    }
}
```

Use `@PreAuthorize("hasRole('ADMIN')")` on tool methods for method-level security.
See [references/implementation-patterns.md](references/implementation-patterns.md) for full security
patterns.

### 6. Testing

```kotlin
@SpringBootTest
class WeatherToolsTest {

    @Autowired
    private lateinit var weatherTools: WeatherTools

    @MockBean
    private lateinit var weatherService: WeatherService

    @Test
    fun `test getWeather success`() {
        whenever(weatherService.getCurrentWeather("London"))
            .thenReturn(WeatherData("London", "Cloudy", 15.0))

        val result = weatherTools.getWeather("London")

        assertThat(result.city).isEqualTo("London")
        verify(weatherService).getCurrentWeather("London")
    }
}
```

See [references/testing-guide.md](references/testing-guide.md) for integration tests,
Testcontainers, security tests, and slice tests.

## Best Practices

### Tool Design

- Keep tools focused — one operation per tool
- Use clear, action-oriented names (`getWeather`, `executeQuery`)
- Always annotate parameters with `@ToolParam` and descriptive text
- Return structured records/DTOs, not raw strings or maps
- Design tools to be idempotent when possible

### Security

- Validate and sanitize all inputs — AI-generated parameters are untrusted
- Use parameterized queries for SQL; validate and normalize paths for file tools
- Apply `@PreAuthorize` for role-based access on sensitive tools
- Audit log all data-modifying tool executions
- Never expose credentials or sensitive data in tool descriptions or error messages

### Performance

- Use `@Cacheable` for expensive operations with appropriate TTL
- Set timeouts for all external calls
- Use `@Async` for long-running operations
- Monitor with Micrometer metrics

### Error Handling

- Return structured error responses with user-friendly messages
- Log context (user, tool name, parameters) for debugging
- Implement retry logic for transient failures
- Implement `@ControllerAdvice` for consistent error responses

## Examples

### Example 1: Minimal Weather MCP Server

```kotlin
@SpringBootApplication
@EnableMcpServer
class WeatherMcpApplication

fun main(args: Array<String>) {
    runApplication<WeatherMcpApplication>(*args)
}

@Component
class WeatherTools {

    @Tool(description = "Get current weather for a city")
    fun getWeather(@ToolParam("City name") city: String): WeatherData {
        return WeatherData(city, "Sunny", 22.5)
    }
}

data class WeatherData(val city: String, val condition: String, val temperatureCelsius: Double)
```

### Example 2: Secure Database Tool

```kotlin
@Component
@PreAuthorize("hasRole('USER')")
class DatabaseTools(
    private val jdbcTemplate: JdbcTemplate
) {

    @Tool(description = "Execute a read-only SQL query and return results")
    fun executeQuery(
        @ToolParam("SQL SELECT query") sql: String,
        @ToolParam(value = "Parameters as JSON array", required = false) paramsJson: String?
    ): QueryResult {
        if (!sql.trim().uppercase().startsWith("SELECT")) {
            throw IllegalArgumentException("Only SELECT queries are allowed")
        }
        val params = paramsJson?.let { objectMapper.readValue(it, Array<Any>::class.java) } ?: emptyArray()
        val rows = jdbcTemplate.queryForList(sql, *params)
        return QueryResult(rows, rows.size)
    }
}
```

See [references/examples.md](references/examples.md) for complete examples including file system
tools, REST API integration, and prompt template servers.

## Constraints and Warnings

### Security

- **Never expose** sensitive data in tool descriptions, parameters, or error messages
- **Input validation is mandatory** — always validate before executing
- **External content is untrusted** — tools fetching URLs may receive prompt injection payloads;
  validate all fetched content
- **SQL injection**: use parameterized queries exclusively
- **Path traversal**: normalize and validate all file paths against a base path

### Operational

- Responses should be concise — large responses can exceed AI context window limits
- All tools must implement timeouts; default should be configurable
- Rate limit expensive operations
- Tools may be called concurrently — ensure thread safety

### Spring AI Specific

- Spring AI is actively developed — pin specific versions in production
- Error messages thrown by tools are exposed to AI models; sanitize them
- Choose transport type carefully: `stdio` for local processes, `http`/`sse` for remote clients

## References

Consult these files for detailed patterns and examples:

- **[references/implementation-patterns.md](references/implementation-patterns.md)** - Tool
  creation, prompt templates, FunctionCallback, Spring Boot auto-configuration, application
  properties
- **[references/advanced-patterns.md](references/advanced-patterns.md)** - Dynamic tool
  registration, multi-model support, caching, error handling
- **[references/testing-guide.md](references/testing-guide.md)** - Unit tests, integration tests,
  Testcontainers, security tests, slice tests
- **[references/examples.md](references/examples.md)** - Complete server examples: weather,
  database, file system, REST API, prompt templates
- **[references/api-reference.md](references/api-reference.md)** - Full API: annotations,
  interfaces, configuration classes, transport implementations, event system
- **[references/migration-guide.md](references/migration-guide.md)** - Migrating from LangChain4j
  MCP to Spring AI MCP
