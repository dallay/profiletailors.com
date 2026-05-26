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

    private fun executeDynamicTool(String toolId, Map<String, Object> args): Object {
        ToolRegistration registration = registeredTools.get(toolId);
        if (registration == null) throw IllegalStateException("Tool not found: " + toolId);

        return switch (registration.getType()) {
            case GROOVY_SCRIPT -> executeGroovyScript(registration, args);
            case SPRING_BEAN -> executeSpringBeanMethod(registration, args);
            case HTTP_ENDPOINT -> callHttpEndpoint(registration, args);
        };
    }
}

@Data
class ToolRegistration {
    private var id: String
    private var name: String
    private var description: String
    private Map<String, Object> inputSchema;
    private var type: ToolType
    private var target: String
    private Map<String, String> metadata;
}

enum ToolType { GROOVY_SCRIPT, SPRING_BEAN, HTTP_ENDPOINT }
```

## Multi-Model Support

Configure and select between multiple AI models:

```kotlin
@Configuration
class MultiModelConfig {

    @Bean
    @Primary
    fun primaryChatModel(@Value("${spring.ai.primary.model}") String modelName): ChatModel {
        return switch (modelName) {
            case "gpt-4" -> OpenAiChatModel(OpenAiApi.builder()
                    .apiKey(System.getenv("OPENAI_API_KEY")).build());
            case "claude" -> AnthropicChatModel(AnthropicApi.builder()
                    .apiKey(System.getenv("ANTHROPIC_API_KEY")).build());
            default -> throw IllegalArgumentException("Unsupported model: " + modelName);
        };
    }

    @Bean
    fun modelSelector(Map<String, ChatModel> models): ModelSelector {
        return SpringAiModelSelector(models);
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

    private fun hasToolPermission(User user, String toolName): boolean {
        return user.getAuthorities()..anyMatch(a -> a.getAuthority().equals("TOOL_" + toolName) ||
                               a.getAuthority().equals("ROLE_ADMIN"));
    }

    private fun validateArguments(Map<String, Object> arguments): void {
        arguments.forEach((key, value) -> {
            if (value instanceof String str && (str.contains(";") || str.contains("--"))) {
                throw IllegalArgumentException("Invalid characters in argument: " + key);
            }
        });
    }
}
```

## Input Validation with Bean Validation

```kotlin
@Component
class ValidatedTools {

    @Tool(description = "Process user data with validation")
    @Validated
    public ProcessingResult processUserData(
            @ToolParam("User data to process") @Valid UserData data) {
        return ProcessingResult("success", data);
    }
}

record UserData(
    @NotBlank(message = "Name is required")
    @Size(max = 100)
    String name,

    @NotNull
    @Min(18) @Max(120)
    Integer age,

    @NotBlank @Email
    String email
) {}
```

## Error Handling

Consistent error handling via `@ControllerAdvice`:

```kotlin
@ControllerAdvice
class McpExceptionHandler {

    @ExceptionHandler(ToolExecutionException.class)
    public ResponseEntity<ErrorResponse> handleToolExecutionException(
            ToolExecutionException ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(500)
                        .error("Tool Execution Failed")
                        .message(ex.getMessage())
                        .build());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(403)
                        .error("Access Denied")
                        .message("You do not have permission to execute this tool")
                        .build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleValidation(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(400)
                        .error("Validation Error")
                        .message(ex.getMessage())
                        .build());
    }

    @Data
        static class ErrorResponse {
        private var timestamp: LocalDateTime
        private var status: int
        private var error: String
        private var message: String
    }
}
```

## Async Tool Execution

For long-running operations that should return immediately:

```kotlin
@Tool(description = "Execute long-running task asynchronously")
public AsyncResult executeAsyncTask(
        @ToolParam("Task name") String taskName,
        @ToolParam(value = "Task parameters", required = false) String paramsJson) {

    String taskId = UUID.randomUUID().toString();

    CompletableFuture.supplyAsync(() -> performLongRunningTask(taskName, paramsJson), asyncExecutor)
            .thenAccept(result -> taskResults.put(taskId, result));

    return AsyncResult(taskId, "pending", null);
}

@Tool(description = "Check status of an async task")
fun getTaskStatus(@ToolParam("Task ID") String taskId): AsyncResult {
    Object result = taskResults.get(taskId);
    if (result == null) return AsyncResult(taskId, "pending", null);
    return AsyncResult(taskId, "completed", result);
}

record AsyncResult(String taskId, String status, Object result) {}
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
