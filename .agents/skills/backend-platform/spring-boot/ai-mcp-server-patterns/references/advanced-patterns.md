# Spring AI MCP Server — Advanced Patterns

Advanced implementation patterns for dynamic tools, multi-model support, caching, error handling,
and security.

## Dynamic Tool Registration

Register tools at runtime based on external configuration or user requests:

```kotlin
@Service
class DynamicToolRegistry {

    private val mcpServer: McpServer
    private final Map<String, ToolRegistration> registeredTools = new ConcurrentHashMap<>();

    fun registerTool(ToolRegistration registration): void {
        registeredTools.put(registration.getId(), registration);

        Tool tool = Tool.builder()
                .name(registration.getName())
                .description(registration.getDescription())
                .inputSchema(registration.getInputSchema())
                .function(args -> executeDynamicTool(registration.getId(), args))
                .build();

        mcpServer.addTool(tool);
    }

    fun unregisterTool(String toolId): void {
        ToolRegistration registration = registeredTools.remove(toolId);
        if (registration != null) {
            mcpServer.removeTool(registration.getName());
        }
    }

    private fun executeDynamicTool(toolId: String, args: Map<String, Any>): Any {
        val registration = registeredTools[toolId] 
            ?: throw IllegalStateException("Tool not found: $toolId")

        return when (registration.type) {
            ToolType.GROOVY_SCRIPT -> executeGroovyScript(registration, args)
            ToolType.SPRING_BEAN -> executeSpringBeanMethod(registration, args)
            ToolType.HTTP_ENDPOINT -> callHttpEndpoint(registration, args)
        }
    }
}

@Data
class ToolRegistration(
    var id: String,
    var name: String,
    var description: String,
    var inputSchema: Map<String, Any>,
    var type: ToolType,
    var target: String,
    var metadata: Map<String, String>
)

enum ToolType { GROOVY_SCRIPT, SPRING_BEAN, HTTP_ENDPOINT }
```

## Multi-Model Support

Configure and select between multiple AI models:

```kotlin
@Configuration
class MultiModelConfig {

    @Bean
    @Primary
    fun primaryChatModel(@Value("\${spring.ai.primary.model}") modelName: String): ChatModel {
        return when (modelName) {
            "gpt-4" -> OpenAiChatModel(OpenAiApi.builder()
                    .apiKey(System.getenv("OPENAI_API_KEY")).build())
            "claude" -> AnthropicChatModel(AnthropicApi.builder()
                    .apiKey(System.getenv("ANTHROPIC_API_KEY")).build())
            else -> throw IllegalArgumentException("Unsupported model: $modelName")
        }
    }

    @Bean
    fun modelSelector(models: Map<String, ChatModel>): ModelSelector {
        return SpringAiModelSelector(models)
    }
}

@Component
class SpringAiModelSelector implements ModelSelector {

    private final Map<String, ChatModel> models;

    @Override
    fun selectModel(Prompt prompt, Map<String, Object> context): ChatModel {
        // Select based on complexity, cost, or latency constraints
        String modelName = determineBestModel(prompt, context);
        return models.get(modelName);
    }

    private fun determineBestModel(Prompt prompt, Map<String, Object> context): String {
        // Implement selection logic (prompt length, cost, latency)
        return "gpt-4";
    }
}
```

## Caching and Performance

```kotlin
@Configuration
@EnableCaching
class McpCacheConfig {

    @Bean
    fun cacheManager(): CacheManager {
        return ConcurrentMapCacheManager("tool-results", "prompt-templates");
    }
}

@Component
class CachedToolExecutor {

    private val mcpServer: McpServer

    @Cacheable(
        value = "tool-results",
        key = "#toolName + '_' + #args.hashCode()",
        unless = "#result.isCacheable() == false"
    )
    fun executeTool(String toolName, Map<String, Object> args): ToolResult {
        return mcpServer.executeTool(toolName, args);
    }

    @CacheEvict(value = "tool-results", allEntries = true)
    fun clearToolCache(): void { }

    @Cacheable(value = "prompt-templates", key = "#templateName")
    fun getPromptTemplate(String templateName): PromptTemplate {
        return mcpServer.getPromptTemplate(templateName);
    }
}
```

## Secure Tool Execution

Full secure tool executor with Spring Security:

```kotlin
@Component
class SecureToolExecutor {

    private val mcpServer: McpServer

    fun executeTool(String toolName, Map<String, Object> arguments): ToolResult {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (!(auth instanceof UserAuthentication userAuth)) {
            throw AccessDeniedException("User not authenticated");
        }

        if (!hasToolPermission(userAuth.getUser(), toolName)) {
            throw AccessDeniedException("Tool not allowed: " + toolName);
        }

        validateArguments(arguments);
        logToolExecution(userAuth.getUser(), toolName, arguments);

        try {
            ToolResult result = mcpServer.executeTool(toolName, arguments);
            logToolSuccess(userAuth.getUser(), toolName);
            return result;
        } catch (Exception e) {
            logToolFailure(userAuth.getUser(), toolName, e);
            throw ToolExecutionException("Tool execution failed", e);
        }
    }

    private fun hasToolPermission(user: User, toolName: String): Boolean {
        return user.authorities.any { a -> 
            a.authority == "TOOL_$toolName" || a.authority == "ROLE_ADMIN"
        }
    }

    private fun validateArguments(arguments: Map<String, Any>) {
        arguments.forEach { (key, value) ->
            if (value is String && (value.contains(";") || value.contains("--"))) {
                throw IllegalArgumentException("Invalid characters in argument: $key")
            }
        }
    }
}
```

## Input Validation with Bean Validation

```kotlin
@Component
class ValidatedTools {

    @Tool(description = "Process user data with validation")
    @Validated
    fun processUserData(
            @ToolParam("User data to process") @Valid data: UserData): ProcessingResult {
        return ProcessingResult("success", data)
    }
}

data class UserData(
    @field:NotBlank(message = "Name is required")
    @field:Size(max = 100)
    val name: String,

    @field:NotNull
    @field:Min(18) @field:Max(120)
    val age: Int,

    @field:NotBlank @field:Email
    val email: String
)
```

## Error Handling

Consistent error handling via `@ControllerAdvice`:

```kotlin
@ControllerAdvice
class McpExceptionHandler {

    @ExceptionHandler(ToolExecutionException::class)
    fun handleToolExecutionException(
            ex: ToolExecutionException, request: WebRequest): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse(
                        timestamp = LocalDateTime.now(),
                        status = 500,
                        error = "Tool Execution Failed",
                        message = ex.message ?: "Unknown error"
                ))
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse(
                        timestamp = LocalDateTime.now(),
                        status = 403,
                        error = "Access Denied",
                        message = "You do not have permission to execute this tool"
                ))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleValidation(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.badRequest()
                .body(ErrorResponse(
                        timestamp = LocalDateTime.now(),
                        status = 400,
                        error = "Validation Error",
                        message = ex.message ?: "Validation failed"
                ))
    }

    data class ErrorResponse(
        val timestamp: LocalDateTime,
        val status: Int,
        val error: String,
        val message: String
    )
}
```

## Async Tool Execution

For long-running operations that should return immediately:

```kotlin
@Tool(description = "Execute long-running task asynchronously")
fun executeAsyncTask(
        @ToolParam("Task name") taskName: String,
        @ToolParam(value = "Task parameters", required = false) paramsJson: String?): AsyncResult {

    val taskId = UUID.randomUUID().toString()

    CompletableFuture.supplyAsync({ performLongRunningTask(taskName, paramsJson) }, asyncExecutor)
            .thenAccept { result -> taskResults[taskId] = result }

    return AsyncResult(taskId, "pending", null)
}

@Tool(description = "Check status of an async task")
fun getTaskStatus(@ToolParam("Task ID") taskId: String): AsyncResult {
    val result = taskResults[taskId]
    if (result == null) return AsyncResult(taskId, "pending", null)
    return AsyncResult(taskId, "completed", result)
}

data class AsyncResult(val taskId: String, val status: String, val result: Any?)
```

## Health Check

```kotlin
@Component
class McpHealthIndicator implements HealthIndicator {

    private val mcpServer: McpServer
    private val toolRegistry: ToolRegistry

    @Override
    fun health(): Health {
        try {
            Transport transport = mcpServer.getTransport();
            List<Tool> tools = toolRegistry.listTools();

            return Health.up()
                    .withDetail("transport", transport.getClass().getSimpleName())
                    .withDetail("connected", transport.isConnected())
                    .withDetail("tools.count", tools.size())
                    .build();
        } catch (Exception e) {
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}
```

## Micrometer Metrics

```kotlin
@Component
class McpMetrics {

    private val meterRegistry: MeterRegistry

    fun recordToolExecution(String toolName, long durationMs, boolean success): void {
        meterRegistry.counter("mcp.tool.executions",
                "tool", toolName, "success", String.valueOf(success)).increment();
        meterRegistry.timer("mcp.tool.execution.time", "tool", toolName)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    fun recordPromptRender(String templateName): void {
        meterRegistry.counter("mcp.prompt.renders", "template", templateName).increment();
    }
}
```
