# Spring AI MCP Server — Implementation Patterns

Detailed patterns for creating tools, prompt templates, and configuring Spring Boot MCP servers.

## Tool Creation Patterns

### Database Tool

```kotlin
@Component
class DatabaseTools(
    private val jdbcTemplate: JdbcTemplate
) {

    @Tool(description = "Execute a safe read-only SQL query")
    fun executeQuery(
        @ToolParam("SQL SELECT query") query: String,
        @ToolParam(value = "Query parameters", required = false) params: Map<String, Any>?
    ): List<Map<String, Any>> {
        require(query.trim().uppercase().startsWith("SELECT")) {
            "Only SELECT queries are allowed"
        }
        return jdbcTemplate.queryForList(query, params)
    }

    @Tool(description = "Get table schema information")
    fun getTableSchema(@ToolParam("Table name") tableName: String): TableSchema {
        val sql = """
            SELECT column_name, data_type 
            FROM information_schema.columns 
            WHERE table_name = ?
        """.trimIndent()
        val columns = jdbcTemplate.queryForList(sql, tableName)
        return TableSchema(tableName, columns)
    }
}

data class TableSchema(val tableName: String, val columns: List<Map<String, Any>>)
```

### API Integration Tool

```kotlin
@Component
class ApiTools(webClientBuilder: WebClient.Builder) {

    private val webClient: WebClient = webClientBuilder.build()

    @Tool(description = "Make HTTP GET request to an API")
    fun callApi(
        @ToolParam("API URL") url: String,
        @ToolParam(value = "Headers as JSON string", required = false) headersJson: String?
    ): ApiResponse {
        runCatching { URL(url) }.getOrElse {
            throw IllegalArgumentException("Invalid URL format")
        }

        val headers = HttpHeaders()
        if (!headersJson.isNullOrBlank()) {
            runCatching {
                val map = ObjectMapper().readValue(headersJson, Map::class.java) as Map<String, String>
                map.forEach { (key, value) -> headers.add(key, value) }
            }.getOrElse {
                throw IllegalArgumentException("Invalid headers JSON")
            }
        }

        return webClient.get()
            .uri(url)
            .headers { it.addAll(headers) }
            .retrieve()
            .bodyToMono(ApiResponse::class.java)
            .block()!!
    }
}

data class ApiResponse(val status: Int, val body: Map<String, Any>, val headers: HttpHeaders)
```

### File System Tool

```kotlin
@Component
class FileSystemTools(@Value("\${mcp.file.base-path:/tmp}") basePath: String) {

    private val basePath: Path = Paths.get(basePath).normalize()

    @Tool(description = "List files in a directory")
    fun listFiles(
        @ToolParam(value = "Directory path (relative to base)", required = false) directory: String?
    ): List<FileInfo> {
        val targetPath = resolvePath(directory ?: "")
        validatePath(targetPath)

        return runCatching {
            Files.list(targetPath).use { stream ->
                stream.filter { Files.isRegularFile(it) }
                    .map { toFileInfo(it) }
                    .toList()
            }
        }.getOrElse { throw RuntimeException("Failed to list files", it) }
    }

    @Tool(description = "Read file contents")
    fun readFile(
        @ToolParam("File path (relative to base)") filePath: String,
        @ToolParam(value = "Maximum lines to read", required = false) maxLines: Int?
    ): FileContent {
        val targetPath = resolvePath(filePath)
        validatePath(targetPath)

        return runCatching {
            val lines = if (maxLines != null) {
                Files.lines(targetPath).use { it.limit(maxLines.toLong()).toList() }
            } else {
                Files.readAllLines(targetPath)
            }
            FileContent(targetPath.toString(), lines)
        }.getOrElse { throw RuntimeException("Failed to read file", it) }
    }

    private fun resolvePath(relativePath: String): Path =
        basePath.resolve(relativePath).normalize()

    private fun validatePath(path: Path) {
        require(path.startsWith(basePath)) { "Path traversal not allowed" }
    }

    private fun toFileInfo(path: Path): FileInfo = runCatching {
        FileInfo(
            basePath.relativize(path).toString(),
            Files.size(path),
            Files.getLastModifiedTime(path).toInstant()
        )
    }.getOrElse {
        FileInfo(path.toString(), 0, Instant.now())
    }
}

data class FileInfo(val path: String, val size: Long, val lastModified: Instant)
data class FileContent(val path: String, val lines: List<String>)
```

## Prompt Template Patterns

```kotlin
@Component
class CodeReviewPrompts {

    @PromptTemplate(
        name = "java-code-review",
        description = "Review Java code for best practices and issues"
    )
    fun createJavaCodeReviewPrompt(
        @PromptParam("code") code: String,
        @PromptParam(value = "focusAreas", required = false) focusAreas: List<String>?
    ): Prompt {
        val focus = focusAreas?.joinToString(", ") ?: "general best practices"
        return Prompt.builder()
            .system("You are an expert Java code reviewer with 20 years of experience.")
            .user("""
                Review the following Java code for $focus:
                ```java
                $code
                ```
                Format: ## Critical Issues | ## Warnings | ## Suggestions | ## Positive Aspects
            """.trimIndent())
            .build()
    }

    @PromptTemplate(
        name = "generate-unit-tests",
        description = "Generate comprehensive unit tests for Java code"
    )
    fun createTestGenerationPrompt(
        @PromptParam("code") code: String,
        @PromptParam("className") className: String,
        @PromptParam(value = "testingFramework", required = false) framework: String?
    ): Prompt {
        val testFramework = framework ?: "JUnit 5"
        return Prompt.builder()
            .system("You are an expert in test-driven development.")
            .user("""
                Generate comprehensive unit tests for class $className using $testFramework:
                ```kotlin
                $code
                ```
                Requirements: test all public methods, include edge cases, use AAA pattern, mock dependencies.
            """.trimIndent())
            .build()
    }
}
```

## FunctionCallback (Low-Level Pattern)

For low-level function calling without annotations:

```kotlin
@Configuration
class FunctionConfig {

    @Bean
    fun weatherFunction(): FunctionCallback =
        FunctionCallback.builder()
            .function("getCurrentWeather", WeatherService())
            .description("Get the current weather for a location")
            .inputType(WeatherRequest::class.java)
            .build()

    @Bean
    fun calculatorFunction(): FunctionCallback =
        FunctionCallbackWrapper.builder(Calculator())
            .withName("calculate")
            .withDescription("Perform mathematical calculations")
            .build()
}

class WeatherService : Function<WeatherRequest, WeatherResponse> {
    override fun apply(request: WeatherRequest): WeatherResponse =
        WeatherResponse(request.location, 72.0, "Sunny")
}

data class WeatherRequest(val location: String)
data class WeatherResponse(val location: String, val temperature: Double, val condition: String)
```

## Spring Boot Auto-Configuration

```kotlin
@Configuration
@AutoConfigureAfter(WebMvcAutoConfiguration::class)
@ConditionalOnClass(McpServer::class, ChatModel::class)
@ConditionalOnProperty(name = ["spring.ai.mcp.enabled"], havingValue = "true", matchIfMissing = true)
class McpAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun mcpServer(
        functionCallbacks: List<FunctionCallback>,
        promptTemplates: List<PromptTemplate>,
        properties: McpServerProperties
    ): McpServer {
        val builder = McpServer.builder()
            .serverInfo("spring-ai-mcp", "1.0.0")
            .transport(properties.transport.create())

        functionCallbacks.forEach { builder.tool(Tool.fromFunctionCallback(it)) }
        promptTemplates.forEach { builder.prompt(Prompt.fromTemplate(it)) }

        return builder.build()
    }

    @Bean
    @ConditionalOnProperty(name = ["spring.ai.mcp.actuator.enabled"], havingValue = "true")
    fun mcpHealthIndicator(mcpServer: McpServer): McpHealthIndicator =
        McpHealthIndicator(mcpServer)
}
```

## Application Properties Reference

### application.yml (complete)

```yaml
spring:
  application:
    name: my-mcp-server
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
          temperature: 0.7
    mcp:
      enabled: true
      server:
        name: my-mcp-server
        version: 1.0.0
      transport:
        type: stdio          # stdio | http | sse
        http:
          port: 8080
          path: /mcp
          cors:
            enabled: true
            allowed-origins: "*"
      security:
        enabled: true
        authorization:
          mode: role-based   # none | role-based | permission-based | attribute-based
          default-deny: true
        audit:
          enabled: true
      tools:
        package-scan: com.example.mcp.tools
        validation:
          enabled: true
          max-execution-time: 30s
        caching:
          enabled: true
          ttl: 5m
      prompts:
        package-scan: com.example.mcp.prompts
        caching:
          enabled: true
          ttl: 1h
      actuator:
        enabled: true
      rate-limiter:
        enabled: true
        requests-per-minute: 100

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true

logging:
  level:
    com.example.mcp: DEBUG
    org.springframework.ai: INFO
```

### Custom Server Configuration (Interceptors)

```kotlin
@Configuration
class CustomMcpConfig {

    @Bean
    fun mcpServerCustomizer(meterRegistry: MeterRegistry): McpServerCustomizer = McpServerCustomizer { server ->
        server.addToolInterceptor { tool, args, chain ->
            val start = System.currentTimeMillis()
            val result = chain.execute(tool, args)
            val duration = System.currentTimeMillis() - start
            meterRegistry.timer("mcp.tool.duration", "tool", tool.name())
                .record(duration, TimeUnit.MILLISECONDS)
            result
        }
    }
}
```
