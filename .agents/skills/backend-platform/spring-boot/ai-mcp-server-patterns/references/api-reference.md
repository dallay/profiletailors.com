# Spring AI MCP Server API Reference

Complete API documentation for Spring AI MCP server implementations.

## Table of Contents

1. [Core Annotations](#core-annotations)
2. [Functional Interfaces](#functional-interfaces)
3. [Configuration Classes](#configuration-classes)
4. [Transport Implementations](#transport-implementations)
5. [Security Interfaces](#security-interfaces)
6. [Utility Classes](#utility-classes)
7. [Property Bindings](#property-bindings)
8. [Event System](#event-system)

## Core Annotations

### `@`Tool

Marks a method as an MCP tool that can be invoked by AI models.

**Target**: Method
**Retention**: Runtime

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Tool(
    /**
     * Description of what this tool does.
     * Used by AI models to understand when to invoke the tool.
     */
    val description: String = "",

    /**
     * Whether this tool requires confirmation before execution.
     */
    val requiresConfirmation: Boolean = false,

    /**
     * Maximum execution time in milliseconds.
     */
    val maxExecutionTime: Long = 30000,

    /**
     * Whether execution time should be monitored.
     */
    val monitorExecution: Boolean = true
)
```

**Example**:

```kotlin
@Tool(
    description = "Get current weather for a city",
    requiresConfirmation = false,
    maxExecutionTime = 5000
)
fun getWeather(@ToolParam("City name") city: String): WeatherData {
    // Implementation
}
```

### `@`ToolParam

Documents a parameter for tool methods.

**Target**: Parameter
**Retention**: Runtime

```kotlin
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class ToolParam(
    /**
     * Description of the parameter purpose.
     */
    val value: String = "",

    /**
     * Whether this parameter is required.
     */
    val required: Boolean = true,

    /**
     * Example value for documentation.
     */
    val example: String = "",

    /**
     * Default value if not provided.
     */
    val defaultValue: String = ""
)
```

### `@`PromptTemplate

Marks a method as a prompt template provider.

**Target**: Method
**Retention**: Runtime

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PromptTemplate(
    /**
     * Unique name of the prompt template.
     */
    val name: String = "",

    /**
     * Description of when to use this template.
     */
    val description: String = "",

    /**
     * The template string with placeholders.
     * Use {placeholder} syntax for parameters.
     */
    val template: String = "",

    /**
     * Model to use for this prompt.
     */
    val model: String = "",

    /**
     * Temperature for model generation.
     */
    val temperature: Double = 0.7
)
```

**Example**:

```kotlin
@PromptTemplate(
    name = "code-review-java",
    description = "Review Java code for best practices",
    template = """
        Review the following Java code:
        ```java
        {code}
        ```
        Focus on: {focusAreas}
        """,
    temperature = 0.3
)
fun createCodeReviewPrompt(@PromptParam("code") code: String): Prompt {
    // Return populated prompt
}
```

### `@`PromptParam

Documents a parameter for prompt template methods.

**Target**: Parameter
**Retention**: Runtime

```kotlin
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class PromptParam(
    /**
     * Name of the parameter in the template.
     */
    val value: String,

    /**
     * Description of the parameter.
     */
    val description: String = "",

    /**
     * Whether this parameter is required.
     */
    val required: Boolean = true,

    /**
     * Example value.
     */
    val example: String = ""
)
```

### `@`EnableMcpServer

Enables MCP server auto-configuration.

**Target**: Type
**Retention**: Runtime

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(McpServerAutoConfiguration::class)
annotation class EnableMcpServer(
    /**
     * Base packages to scan for tools and prompts.
     */
    val basePackages: Array<String> = [],

    /**
     * Whether to enable automatic tool discovery.
     */
    val autoDiscovery: Boolean = true,

    /**
     * Configuration class to use.
     */
    val configuration: Array<KClass<*>> = []
)
```

## Functional Interfaces

### ToolExecutor

Functional interface for tool execution.

```kotlin
@FunctionalInterface
fun interface ToolExecutor {
    /**
     * Execute a tool with the given arguments.
     *
     * @param toolName Name of the tool to execute
     * @param arguments Arguments as a map
     * @return Execution result
     * @throws ToolExecutionException if execution fails
     */
    @Throws(ToolExecutionException::class)
    fun execute(toolName: String, arguments: Map<String, Any>): ToolResult
}
```

### ToolFilter

Filter for tool execution.

```kotlin
@FunctionalInterface
fun interface ToolFilter {
    /**
     * Determine if a tool should be allowed to execute.
     *
     * @param tool The tool being requested
     * @param context Execution context
     * @return true if tool should be allowed
     */
    fun isAllowed(tool: Tool, context: ToolExecutionContext): Boolean
}
```

**Default Implementation**:

```kotlin
class DefaultToolFilter : ToolFilter {
    override fun isAllowed(tool: Tool, context: ToolExecutionContext): Boolean {
        val auth = SecurityContextHolder.getContext().authentication

        // Admin tools require admin role
        if (tool.name.startsWith("admin_")) {
            return auth?.authorities?.any { it.authority == "ROLE_ADMIN" } ?: false
        }

        return true
    }
}
```

### PromptRenderer

Renders prompt templates with parameters.

```kotlin
@FunctionalInterface
fun interface PromptRenderer {
    /**
     * Render a prompt template with parameters.
     *
     * @param template The prompt template
     * @param parameters Parameters to substitute
     * @return Rendered prompt
     */
    fun render(template: PromptTemplate, parameters: Map<String, Any>): Prompt
}
```

## Configuration Classes

### McpServerAutoConfiguration

Auto-configuration for MCP servers.

```kotlin
@Configuration
@AutoConfigureAfter(WebMvcAutoConfiguration::class)
@ConditionalOnClass(McpServer::class)
@ConditionalOnProperty(name = ["spring.ai.mcp.enabled"], havingValue = "true", matchIfMissing = true)
class McpServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun mcpProperties(): McpServerProperties = McpServerProperties()

    @Bean
    @ConditionalOnMissingBean
    fun mcpServer(
        properties: McpServerProperties,
        tools: ObjectProvider<List<Tool>>,
        prompts: ObjectProvider<List<PromptTemplate>>
    ): McpServer {
        val builder = McpServer.builder()
            .serverInfo(properties.server.name, properties.server.version)
            .transport(createTransport(properties.transport))

        tools.ifAvailable { toolList -> toolList.forEach { builder.tool(it) } }
        prompts.ifAvailable { promptList -> promptList.forEach { builder.prompt(it) } }

        return builder.build()
    }

    private fun createTransport(config: TransportConfig): Transport =
        when (config.type) {
            TransportType.STDIO -> StdioTransport()
            TransportType.HTTP -> HttpTransport(config.http.port)
            TransportType.SSE -> SseTransport(config.http.port, config.http.path)
        }

    @Bean
    @ConditionalOnMissingBean
    fun toolRegistry(context: ApplicationContext): ToolRegistry {
        val registry = ToolRegistry()

        val toolBeans = context.getBeansWithAnnotation(Component::class.java)
        toolBeans.values.forEach { bean ->
            bean::class.java.methods.forEach { method ->
                if (method.isAnnotationPresent(Tool::class.java)) {
                    registry.register(Tool.fromMethod(method, bean))
                }
            }
        }

        return registry
    }
}
```

### McpServerProperties

Configuration properties for MCP server.

```kotlin
@ConfigurationProperties(prefix = "spring.ai.mcp")
class McpServerProperties {
    var server = ServerProperties()
    var transport = TransportProperties()
    var security = SecurityProperties()
    var tools = ToolsProperties()
    var prompts = PromptsProperties()
    var logging = LoggingProperties()
    var metrics = MetricsProperties()

    data class ServerProperties(
        var name: String = "spring-ai-mcp-server",
        var version: String = "1.0.0",
        var description: String = "Spring AI MCP Server"
    )

    data class TransportProperties(
        var type: TransportType = TransportType.STDIO,
        var http: HttpProperties = HttpProperties()
    ) {
        data class HttpProperties(
            var port: Int = 8080,
            var path: String = "/mcp",
            var cors: CorsProperties = CorsProperties()
        ) {
            data class CorsProperties(
                var enabled: Boolean = true,
                var allowedOrigins: List<String> = listOf("*"),
                var allowedMethods: List<String> = listOf("GET", "POST"),
                var allowedHeaders: List<String> = listOf("*")
            )
        }
    }

    data class SecurityProperties(
        var enabled: Boolean = false,
        var authorization: AuthorizationProperties = AuthorizationProperties(),
        var audit: AuditProperties = AuditProperties()
    ) {
        data class AuthorizationProperties(
            var mode: AuthorizationMode = AuthorizationMode.ROLE_BASED,
            var defaultDeny: Boolean = true,
            var allowedTools: List<String> = emptyList(),
            var adminTools: List<String> = listOf("admin_*")
        )

        data class AuditProperties(
            var enabled: Boolean = true,
            var auditedOperations: List<String> = listOf("*")
        )

        enum class AuthorizationMode {
            NONE, ROLE_BASED, PERMISSION_BASED, ATTRIBUTE_BASED
        }
    }

    data class ToolsProperties(
        var packageScan: String = "com.example.mcp.tools",
        var validation: ValidationProperties = ValidationProperties(),
        var caching: CachingProperties = CachingProperties()
    ) {
        data class ValidationProperties(
            var enabled: Boolean = true,
            var maxExecutionTime: Duration = Duration.ofSeconds(30),
            var maxArgumentsSize: Int = 1000000 // 1MB
        )

        data class CachingProperties(
            var enabled: Boolean = true,
            var ttl: Duration = Duration.ofMinutes(5),
            var maxSize: Int = 100
        )
    }

    data class PromptsProperties(
        var packageScan: String = "com.example.mcp.prompts",
        var caching: CachingProperties = CachingProperties()
    ) {
        data class CachingProperties(
            var enabled: Boolean = true,
            var ttl: Duration = Duration.ofHours(1),
            var maxSize: Int = 1000
        )
    }

    // Additional nested properties...
}
```

## Transport Implementations

### Transport Interface

```kotlin
interface Transport {
    /**
     * Start the transport.
     */
    @Throws(IOException::class)
    fun start()

    /**
     * Stop the transport.
     */
    @Throws(IOException::class)
    fun stop()

    /**
     * Send a message.
     *
     * @param message The message to send
     */
    @Throws(IOException::class)
    fun send(message: Message)

    /**
     * Receive a message.
     *
     * @return The received message
     */
    @Throws(IOException::class)
    fun receive(): Message

    /**
     * Check if transport is connected.
     */
    fun isConnected(): Boolean
}
```

### StdioTransport

Standard input/output transport for local process communication.

```kotlin
class StdioTransport : Transport {
    private val objectMapper = ObjectMapper()
    private val reader = BufferedReader(InputStreamReader(System.`in`))
    private val writer = PrintWriter(System.out, true)
    @Volatile
    private var running = false

    override fun start() {
        running = true
        log.info("STDIO transport started")
    }

    override fun stop() {
        running = false
        reader.close()
        writer.close()
        log.info("STDIO transport stopped")
    }

    override fun send(message: Message) {
        val json = objectMapper.writeValueAsString(message)
        writer.println(json)
        writer.flush()
    }

    override fun receive(): Message {
        val line = reader.readLine() ?: throw EOFException("End of stream")
        return objectMapper.readValue(line, Message::class.java)
    }

    override fun isConnected(): Boolean = running
}
```

### HttpTransport

HTTP transport for remote communication.

```kotlin
class HttpTransport(
    private val port: Int,
    private val path: String
) : Transport {
    private val server: HttpServer = HttpServer.create(InetSocketAddress(port), 0)
    private val messageHandlers: MutableList<Consumer<Message>> = CopyOnWriteArrayList()
    @Volatile
    private var running = false

    override fun start() {
        server.createContext(path) { exchange ->
            if (exchange.requestMethod == "POST") {
                val requestBody = String(exchange.requestBody.readAllBytes())
                val message = objectMapper.readValue(requestBody, Message::class.java)

                messageHandlers.forEach { it.accept(message) }

                val response = """{"status":"acknowledged"}"""
                exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
                exchange.responseBody.write(response.toByteArray())
            }
            exchange.close()
        }

        server.start()
        running = true
        log.info("HTTP transport started on port {} path {}", port, path)
    }

    override fun stop() {
        server.stop(0)
        running = false
        log.info("HTTP transport stopped")
    }

    override fun send(message: Message) {
        // HTTP transport is request-response based
        throw UnsupportedOperationException("Use HTTP client for sending")
    }

    override fun receive(): Message {
        // HTTP transport receives via POST requests
        throw UnsupportedOperationException("HTTP transport is async")
    }

    fun addMessageHandler(handler: Consumer<Message>) {
        messageHandlers.add(handler)
    }

    override fun isConnected(): Boolean = running
}
```

### SseTransport

Server-Sent Events transport for real-time communication.

```kotlin
class SseTransport(
    private val port: Int,
    private val path: String
) : Transport {
    private val emitters: MutableList<SseEmitter> = CopyOnWriteArrayList()
    private val server: HttpServer = HttpServer.create(InetSocketAddress(port), 0)
    @Volatile
    private var running = false

    override fun start() {
        // SSE endpoint for receiving messages
        server.createContext("$path/sse") { exchange ->
            if (exchange.requestMethod == "GET") {
                handleSseConnection(exchange)
            }
        }

        // POST endpoint for sending messages
        server.createContext(path) { exchange ->
            if (exchange.requestMethod == "POST") {
                handleMessage(exchange)
            }
        }

        server.start()
        running = true
        log.info("SSE transport started on port {} path {}", port, path)
    }

    private fun handleSseConnection(exchange: HttpExchange) {
        val headers = exchange.responseHeaders
        headers.add("Content-Type", "text/event-stream")
        headers.add("Cache-Control", "no-cache")
        headers.add("Connection", "keep-alive")

        exchange.sendResponseHeaders(200, 0)

        // Keep connection open
        val os = exchange.responseBody
        emitters.add(SseEmitter(os, exchange))
    }

    private fun handleMessage(exchange: HttpExchange) {
        val requestBody = String(exchange.requestBody.readAllBytes())
        val message = objectMapper.readValue(requestBody, Message::class.java)

        // Send to all SSE clients
        broadcast(message)

        val response = """{"status":"broadcasted"}"""
        exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
        exchange.responseBody.write(response.toByteArray())
        exchange.close()
    }

    private fun broadcast(message: Message) {
        val data = "data: ${toJson(message)}\n\n"
        emitters.removeIf { !it.send(data) }
    }

    override fun send(message: Message) {
        broadcast(message)
    }

    override fun receive(): Message {
        // SSE transport is async, use event-driven approach
        throw UnsupportedOperationException("SSE transport is async")
    }

    // Additional methods...
}
```

## Security Interfaces

### ToolValidator

Validates tool arguments and execution context.

```kotlin
interface ToolValidator {
    /**
     * Validate tool arguments before execution.
     *
     * @param tool The tool being executed
     * @param arguments The provided arguments
     * @throws ValidationException if validation fails
     */
    @Throws(ValidationException::class)
    fun validateArguments(tool: Tool, arguments: Map<String, Any>)

    /**
     * Validate execution context.
     *
     * @param tool The tool being executed
     * @param context The execution context
     * @throws ValidationException if validation fails
     */
    @Throws(ValidationException::class)
    fun validateContext(tool: Tool, context: ToolExecutionContext)
}
```

**Implementation Example**:

```kotlin
@Component
class DefaultToolValidator(
    private val properties: McpServerProperties
) : ToolValidator {

    override fun validateArguments(tool: Tool, arguments: Map<String, Any>) {
        // Check argument size
        val size = arguments.toString().toByteArray().size
        if (size > properties.tools.validation.maxArgumentsSize) {
            throw ValidationException("Arguments too large: $size bytes")
        }

        // Validate based on tool parameter annotations
        tool.method.parameters
            .filter { it.isAnnotationPresent(ToolParam::class.java) }
            .forEach { validateParameter(it, arguments) }
    }

    private fun validateParameter(param: Parameter, arguments: Map<String, Any>) {
        val annotation = param.getAnnotation(ToolParam::class.java)
        val paramName = param.name

        if (annotation.required && !arguments.containsKey(paramName)) {
            throw ValidationException("Required parameter missing: $paramName")
        }

        val value = arguments[paramName]
        if (value != null) {
            validateParameterType(param.type, value, paramName)
            validateParameterContent(value, paramName)
        }
    }

    private fun validateParameterType(expectedType: Class<*>, value: Any, paramName: String) {
        if (!expectedType.isAssignableFrom(value::class.java)) {
            throw ValidationException(
                "Parameter $paramName: expected ${expectedType.simpleName}, got ${value::class.simpleName}"
            )
        }
    }

    private fun validateParameterContent(value: Any, paramName: String) {
        if (value is String) {
            // Check for injection patterns
            if (value.contains(";") || value.contains("&") || value.contains("|")) {
                throw ValidationException("Invalid characters in parameter: $paramName")
            }
        }
    }

    override fun validateContext(tool: Tool, context: ToolExecutionContext) {
        // Check authentication if required
        if (tool.requiresAuthentication() && !context.isAuthenticated) {
            throw ValidationException("Authentication required for tool: ${tool.name}")
        }

        // Check rate limits
        if (exceedsRateLimit(context.user, tool)) {
            throw ValidationException("Rate limit exceeded for tool: ${tool.name}")
        }
    }

    private fun exceedsRateLimit(user: User, tool: Tool): Boolean {
        // Implement rate limiting logic
        return false
    }
}
```

### SecurityContext

Provides security context for tool execution.

```kotlin
interface SecurityContext {
    /**
     * Get the current authentication.
     */
    fun getAuthentication(): Optional<Authentication>

    /**
     * Check if current user has permission.
     */
    fun hasPermission(permission: String): Boolean

    /**
     * Check if current user has any of the given roles.
     */
    fun hasAnyRole(vararg roles: String): Boolean

    /**
     * Get user details if authenticated.
     */
    fun getUserDetails(): Optional<UserDetails>

    /**
     * Validate MFA token if required.
     */
    fun validateMfaToken(token: String): Boolean
}
```

## Utility Classes

### ToolRegistry

Manages tool registration and lookup.

```kotlin
@Component
class ToolRegistry {
    private val tools: MutableMap<String, Tool> = ConcurrentHashMap()
    private val listeners: MutableList<ToolRegistrationListener> = CopyOnWriteArrayList()

    /**
     * Register a tool.
     */
    fun register(tool: Tool) {
        tools[tool.name] = tool
        notifyListeners(tool, ToolEvent.Type.REGISTERED)
    }

    /**
     * Unregister a tool.
     */
    fun unregister(toolName: String) {
        val removed = tools.remove(toolName)
        if (removed != null) {
            notifyListeners(removed, ToolEvent.Type.UNREGISTERED)
        }
    }

    /**
     * Get a tool by name.
     */
    fun getTool(name: String): Optional<Tool> = Optional.ofNullable(tools[name])

    /**
     * List all tools.
     */
    fun listTools(): List<Tool> = tools.values.toList()

    /**
     * Add registration listener.
     */
    fun addListener(listener: ToolRegistrationListener) {
        listeners.add(listener)
    }

    private fun notifyListeners(tool: Tool, type: ToolEvent.Type) {
        val event = ToolEvent(tool, type)
        listeners.forEach { it.onToolEvent(event) }
    }
}
```

### McpMessage

Represents MCP protocol messages.

```kotlin
class McpMessage private constructor(
    val id: String?,
    val method: String?,
    val params: Map<String, Any>?,
    val result: Any?,
    val error: McpError?
) {
    val jsonrpc: String = "2.0"

    class Builder {
        private var id: String? = null
        private var method: String? = null
        private var params: Map<String, Any>? = null
        private var result: Any? = null
        private var error: McpError? = null

        fun id(id: String) = apply { this.id = id }
        fun method(method: String) = apply { this.method = method }
        fun params(params: Map<String, Any>) = apply { this.params = params }
        fun result(result: Any) = apply { this.result = result }
        fun error(error: McpError) = apply { this.error = error }

        fun build() = McpMessage(id, method, params, result, error)
    }

    companion object {
        @JvmStatic
        fun builder() = Builder()
    }

    // Getters and utility methods...
}
```

### McpError

Represents errors in MCP communication.

```kotlin
class McpError(
    val code: Int,
    val message: String,
    val data: Map<String, Any>? = null
) {
    companion object {
        // Error codes
        const val PARSE_ERROR = -32700
        const val INVALID_REQUEST = -32600
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
        const val INTERNAL_ERROR = -32603
    }

    // Static factory methods...
}
```

## Property Bindings

### spring.ai.mcp.*

Main configuration properties.

| Property                                    | Type    | Default                 | Description                       |
|---------------------------------------------|---------|-------------------------|-----------------------------------|
| `spring.ai.mcp.enabled`                     | boolean | true                    | Enable MCP server                 |
| `spring.ai.mcp.server.name`                 | string  | spring-ai-mcp-server    | Server name                       |
| `spring.ai.mcp.server.version`              | string  | 1.0.0                   | Server version                    |
| `spring.ai.mcp.transport.type`              | enum    | stdio                   | Transport type (stdio, http, sse) |
| `spring.ai.mcp.transport.http.port`         | int     | 8080                    | HTTP port                         |
| `spring.ai.mcp.transport.http.path`         | string  | /mcp                    | HTTP path                         |
| `spring.ai.mcp.security.enabled`            | boolean | false                   | Enable security                   |
| `spring.ai.mcp.security.authorization.mode` | enum    | role-based              | Authorization mode                |
| `spring.ai.mcp.security.audit.enabled`      | boolean | true                    | Enable auditing                   |
| `spring.ai.mcp.tools.package-scan`          | string  | com.example.mcp.tools   | Package to scan for tools         |
| `spring.ai.mcp.prompts.package-scan`        | string  | com.example.mcp.prompts | Package to scan for prompts       |

### Rate Limiting Properties

```yaml
spring:
  ai:
    mcp:
      rate-limiting:
        enabled: true
        requests-per-minute: 100
        burst-capacity: 150
        limit-by: user  # user, ip, global
        redis:
          enabled: true
          host: localhost
          port: 6379
```

### Threading Properties

```yaml
spring:
  ai:
    mcp:
      threading:
        executor:
          core-pool-size: 10
          max-pool-size: 50
          queue-capacity: 100
          keep-alive-time: 60s
          thread-name-prefix: mcp-
      timeout:
        default: 30s
        per-tool:
          long-running-tool: 5m
          admin-tool: 1m
```

## Event System

### McpEvent

Base class for MCP events.

```kotlin
abstract class McpEvent(
    source: Any,
    private val eventSource: String
) : ApplicationEvent(source) {
    private val timestamp: Instant = Instant.now()

    fun getTimestamp(): Instant = timestamp
    fun getSource(): String = eventSource
}
```

### ToolEvent

Events related to tool lifecycle.

```kotlin
class ToolEvent(
    private val tool: Tool,
    private val type: Type,
    private val metadata: Map<String, Any> = emptyMap()
) : McpEvent(tool, "tool-registry") {

    enum class Type {
        REGISTERED,
        UNREGISTERED,
        EXECUTED,
        FAILED,
        TIMEOUT
    }

    // Getters...
}
```

### PromptEvent

Events related to prompt operations.

```kotlin
class PromptEvent(
    private val template: PromptTemplate,
    private val type: Type,
    private val parameters: Map<String, Any>
) : McpEvent(template, "prompt-renderer") {

    enum class Type {
        RENDERED,
        CACHED,
        FAILED
    }

    // Getters...
}
```

### Event Listeners

```kotlin
@Component
class McpEventListener(
    private val metricsService: MetricsService,
    private val auditService: AuditService
) : ApplicationListener<McpEvent> {

    override fun onApplicationEvent(event: McpEvent) {
        when (event) {
            is ToolEvent -> handleToolEvent(event)
            is PromptEvent -> handlePromptEvent(event)
            else -> log.debug("Unhandled event: {}", event::class.java)
        }
    }

    private fun handleToolEvent(event: ToolEvent) {
        metricsService.recordToolEvent(
            event.tool.name,
            event.type,
            event.timestamp
        )

        if (event.type == ToolEvent.Type.FAILED) {
            auditService.logToolFailure(
                event.tool,
                event.metadata
            )
        }
    }

    private fun handlePromptEvent(event: PromptEvent) {
        if (event.type == PromptEvent.Type.CACHED) {
            metricsService.incrementPromptCacheHit()
        }
    }
}
```

## Async Execution

### AsyncToolExecutor

Asynchronous tool execution support.

```kotlin
class AsyncToolExecutor(
    private val delegate: ToolExecutor,
    private val executor: ExecutorService
) {

    fun executeAsync(
        toolName: String,
        arguments: Map<String, Any>
    ): CompletableFuture<ToolResult> =
        CompletableFuture.supplyAsync({
            try {
                delegate.execute(toolName, arguments)
            } catch (e: ToolExecutionException) {
                throw CompletionException(e)
            }
        }, executor)

    fun executeWithTimeout(
        toolName: String,
        arguments: Map<String, Any>,
        timeout: Duration
    ): ToolExecutionFuture {
        val future = executeAsync(toolName, arguments)
        return ToolExecutionFuture(future, timeout)
    }
}

class ToolExecutionFuture(
    private val future: CompletableFuture<ToolResult>,
    private val timeout: Duration
) {

    fun getResult(): Optional<ToolResult> = try {
        Optional.ofNullable(
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        )
    } catch (e: InterruptedException) {
        Optional.empty()
    } catch (e: ExecutionException) {
        Optional.empty()
    } catch (e: TimeoutException) {
        throw e
    }

    fun cancel(): Boolean = future.cancel(true)

    fun isDone(): Boolean = future.isDone
}
```

## Health Checks

### McpHealthIndicator

Spring Boot actuator health check for MCP server.

```kotlin
@Component
class McpHealthIndicator(
    private val mcpServer: McpServer,
    private val toolRegistry: ToolRegistry
) : HealthIndicator {

    override fun health(): Health {
        val builder = Health.Builder()

        try {
            // Check transport
            val transport = mcpServer.transport
            builder.withDetail("transport", transport::class.java.simpleName)
            builder.withDetail("connected", transport.isConnected())

            // Check tools
            val tools = toolRegistry.listTools()
            builder.withDetail("tools.count", tools.size)

            // Sample tool execution
            testToolExecution(builder, tools)

            builder.status(Status.UP)
        } catch (e: Exception) {
            builder.status(Status.DOWN)
                .withDetail("error", e.message)
        }

        return builder.build()
    }

    private fun testToolExecution(builder: Health.Builder, tools: List<Tool>) {
        if (tools.isNotEmpty()) {
            val sampleTool = tools[0]
            try {
                val result = sampleTool.execute(emptyMap())
                builder.withDetail("sampleTool.status", "success")
            } catch (e: Exception) {
                builder.withDetail("sampleTool.status", "failed")
                builder.withDetail("sampleTool.error", e.message)
            }
        }
    }
}
```

## Performance Metrics

### McpMetrics

Micrometer-based metrics for MCP server.

```kotlin
@Component
class McpMetrics(
    private val meterRegistry: MeterRegistry
) {

    private lateinit var toolExecutionsCounter: Counter
    private lateinit var toolExecutionTimer: Timer
    private lateinit var toolArgumentSize: DistributionSummary
    private lateinit var toolFailuresCounter: Counter
    private lateinit var promptRenderCounter: Counter

    @PostConstruct
    fun initialize() {
        toolExecutionsCounter = Counter.builder("mcp.tool.executions")
            .description("Number of tool executions")
            .register(meterRegistry)

        toolExecutionTimer = Timer.builder("mcp.tool.execution.time")
            .description("Time taken for tool execution")
            .register(meterRegistry)

        toolArgumentSize = DistributionSummary.builder("mcp.tool.arguments.size")
            .description("Size of tool arguments")
            .register(meterRegistry)

        toolFailuresCounter = Counter.builder("mcp.tool.failures")
            .description("Number of tool failures")
            .register(meterRegistry)

        promptRenderCounter = Counter.builder("mcp.prompt.renders")
            .description("Number of prompt renders")
            .register(meterRegistry)
    }

    fun recordToolExecution(toolName: String, durationMs: Long, success: Boolean) {
        toolExecutionsCounter.increment()
        toolExecutionTimer.record(durationMs, TimeUnit.MILLISECONDS)

        if (!success) {
            toolFailuresCounter.increment()
        }

        val tags = Tags.of("tool", toolName, "success", success.toString())
        meterRegistry.counter("mcp.tool.executions.byTool", tags).increment()
    }

    fun recordPromptRender(templateName: String) {
        promptRenderCounter.increment()

        val tags = Tags.of("template", templateName)
        meterRegistry.counter("mcp.prompt.renders.byTemplate", tags).increment()
    }

    fun recordArgumentSize(size: Int) {
        toolArgumentSize.record(size.toDouble())
    }
}
```