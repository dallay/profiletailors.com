# Loggers Endpoint

Spring Boot Actuator includes the ability to view and configure the log levels of your application
at runtime. You can view either the entire list or an individual logger's configuration, which is
made up of both the explicitly configured logging level as well as the effective logging level given
to it by the logging framework. These levels can be one of:

- `TRACE`
- `DEBUG`
- `INFO`
- `WARN`
- `ERROR`
- `FATAL`
- `OFF`
- `null`

`null` indicates that there is no explicit configuration.

## Viewing Logger Configuration

### View All Loggers

To view the configuration of all loggers:

```
GET /actuator/loggers
```

Response example:

```json
{
  "levels": [
    "OFF",
    "ERROR",
    "WARN",
    "INFO",
    "DEBUG",
    "TRACE"
  ],
  "loggers": {
    "ROOT": {
      "configuredLevel": "INFO",
      "effectiveLevel": "INFO"
    },
    "com.example": {
      "configuredLevel": null,
      "effectiveLevel": "INFO"
    },
    "com.example.MyClass": {
      "configuredLevel": "DEBUG",
      "effectiveLevel": "DEBUG"
    }
  }
}
```

### View Specific Logger

To view the configuration of a specific logger:

```
GET /actuator/loggers/com.example.MyClass
```

Response example:

```json
{
  "configuredLevel": "DEBUG",
  "effectiveLevel": "DEBUG"
}
```

## Configuring a Logger

To configure a given logger, `POST` a partial entity to the resource's URI, as the following example
shows:

```
POST /actuator/loggers/com.example.MyClass
Content-Type: application/json

{
    "configuredLevel": "DEBUG"
}
```

> **TIP**
>
> To "reset" the specific level of the logger (and use the default configuration instead), you can
> pass a value of `null` as the `configuredLevel`.

### Reset Logger Level

To reset a logger to its default level:

```
POST /actuator/loggers/com.example.MyClass
Content-Type: application/json

{
    "configuredLevel": null
}
```

## Configuration Examples

### Enable Debug Logging for Specific Package

```bash
curl -X POST http://localhost:8080/actuator/loggers/com.example.service \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'
```

### Enable Trace Logging for Spring Security

```bash
curl -X POST http://localhost:8080/actuator/loggers/org.springframework.security \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "TRACE"}'
```

### Set Root Logger Level

```bash
curl -X POST http://localhost:8080/actuator/loggers/ROOT \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "WARN"}'
```

## Programmatic Logger Management

You can also manage loggers programmatically in your application:

```kotlin
@RestController
class LoggerController {

    private val loggingSystem: LoggingSystem

    public LoggerController(LoggingSystem loggingSystem)
    {
        this.loggingSystem = loggingSystem;
    }

    @PostMapping("/admin/logger/{name}")
    fun setLogLevel(@PathVariable String name, @RequestBody LogLevelRequest request): void {
        LogLevel level = request . getLevel () != null ?
        LogLevel.valueOf(request.getLevel().toUpperCase()) : null;
        loggingSystem.setLogLevel(name, level);
    }

    public static
    class LogLevelRequest {
        private var level: String

        fun getLevel(): String {
            return level; }
        fun setLevel(String level): void {
            this.level = level; }
    }
}
```

## Conditional Logging

### Environment-based Configuration

```yaml
logging:
  level:
    com.example: ${LOGGING_LEVEL_EXAMPLE:INFO}
    org.springframework.web: ${LOGGING_LEVEL_WEB:WARN}
    org.hibernate.SQL: ${LOGGING_LEVEL_SQL:WARN}
    org.hibernate.type.descriptor.sql: ${LOGGING_LEVEL_SQL_PARAMS:WARN}

---
spring:
  config:
    activate:
      on-profile: development
logging:
  level:
    com.example: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql: TRACE

---
spring:
  config:
    activate:
      on-profile: production
logging:
  level:
    root: WARN
    com.example: INFO
```

### Feature Toggle Logging

```kotlin
@Component
class FeatureLoggingController {

    private val loggingSystem: LoggingSystem
    private val environment: Environment

    public FeatureLoggingController(LoggingSystem loggingSystem, Environment environment)
    {
        this.loggingSystem = loggingSystem;
        this.environment = environment;
    }

    @EventListener
    fun handleFeatureToggleChange(FeatureToggleEvent event): void {
        if ("debug-logging".equals(event.getFeatureName())) {
            if (event.isEnabled()) {
                enableDebugLogging();
            } else {
                disableDebugLogging();
            }
        }
    }

    private fun enableDebugLogging(): void {
        loggingSystem.setLogLevel("com.example.service", LogLevel.DEBUG);
        loggingSystem.setLogLevel("com.example.repository", LogLevel.DEBUG);
    }

    private fun disableDebugLogging(): void {
        loggingSystem.setLogLevel("com.example.service", null);
        loggingSystem.setLogLevel("com.example.repository", null);
    }
}
```

## Security Considerations

### Securing the Loggers Endpoint

```kotlin
@Configuration
class LoggersSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain loggersSecurityFilterChain(HttpSecurity http) throws Exception
    {
        return http
            .requestMatcher(EndpointRequest.to("loggers"))
            .authorizeHttpRequests(requests ->
        requests.anyRequest().hasRole("ADMIN"))
        .httpBasic(withDefaults())
        .build();
    }
}
```

### Read-only Access

To provide read-only access to the loggers endpoint:

```yaml
management:
  endpoint:
    loggers:
      access: read-only
```

Or configure programmatically:

```kotlin
@Configuration
class LoggersAccessConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain loggersSecurityFilterChain(HttpSecurity http) throws Exception
    {
        return http
            .requestMatcher(EndpointRequest.to("loggers"))
            .authorizeHttpRequests(requests ->
        requests
            .requestMatchers(HttpMethod.GET).hasRole("LOGGER_READER")
            .requestMatchers(HttpMethod.POST).hasRole("LOGGER_ADMIN")
            .anyRequest().denyAll())
        .httpBasic(withDefaults())
        .build();
    }
}
```

## OpenTelemetry Integration

By default, logging via OpenTelemetry is not configured. You have to provide the location of the
OpenTelemetry logs endpoint to configure it:

```yaml
management:
  otlp:
    logging:
      endpoint: "https://otlp.example.com:4318/v1/logs"
```

> **NOTE**
>
> The OpenTelemetry Logback appender and Log4j appender are not part of Spring Boot. For more
> details, see
>
the [OpenTelemetry Logback appender](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/logback/logback-appender-1.0/library)
> or
>
the [OpenTelemetry Log4j2 appender](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/log4j/log4j-appender-2.17/library)
> in
>
the [OpenTelemetry Java instrumentation GitHub repository](https://github.com/open-telemetry/opentelemetry-java-instrumentation).

> **TIP**
>
> You have to configure the appender in your `logback-spring.xml` or `log4j2-spring.xml`
> configuration to get OpenTelemetry logging working.

The `OpenTelemetryAppender` for both Logback and Log4j requires access to an `OpenTelemetry`
instance to function properly. This instance must be set programmatically during application
startup:

```kotlin
@Component
class OpenTelemetryAppenderInitializer {

    public OpenTelemetryAppenderInitializer(OpenTelemetry openTelemetry)
    {
        // Configure Logback appender
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext) {
            LoggerContext context =(LoggerContext) LoggerFactory . getILoggerFactory ();
            context.getStatusManager().add(OnConsoleStatusListener());

            OpenTelemetryAppender appender = OpenTelemetryAppender ();
            appender.setContext(context);
            appender.setOpenTelemetry(openTelemetry);
            appender.start();

            ch.qos.logback.classic.Logger rootLogger = context . getLogger (Logger.ROOT_LOGGER_NAME);
            rootLogger.addAppender(appender);
        }
    }
}
```

## Best Practices

1. **Monitor Performance**: Changing log levels at runtime can impact application performance
2. **Security**: Always secure the loggers endpoint in production environments
3. **Audit Changes**: Log when log levels are changed and by whom
4. **Temporary Changes**: Consider making runtime log level changes temporary
5. **Documentation**: Document the purpose of different log levels in your application
6. **Testing**: Test your application with different log levels to ensure it performs well
7. **Correlation IDs**: Use correlation IDs to track requests across log entries

### Audit Log Level Changes

```kotlin
@Component
class LoggerAuditListener {

    private static final Logger logger = LoggerFactory.getLogger(LoggerAuditListener.
    class);

    @EventListener
    fun handleLoggerConfigurationChange(LoggerConfigurationChangeEvent event): void {
        String username = getCurrentUsername ();
        logger.info(
            "Logger level changed: logger={}, oldLevel={}, newLevel={}, user={}",
            event.getLoggerName(),
            event.getOldLevel(),
            event.getNewLevel(),
            username
        );
    }

    private fun getCurrentUsername(): String {
        Authentication auth = SecurityContextHolder . getContext ().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
```

### Temporary Log Level Changes

```kotlin
@Component
class TemporaryLogLevelManager {

    private val loggingSystem: LoggingSystem
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Map<String, LogLevel> originalLevels = new ConcurrentHashMap<>();

    public TemporaryLogLevelManager(LoggingSystem loggingSystem)
    {
        this.loggingSystem = loggingSystem;
    }

    fun setTemporaryLogLevel(String loggerName, LogLevel level, Duration duration): void {
        // Store original level
        LoggerConfiguration config = loggingSystem . getLoggerConfiguration (loggerName);
        originalLevels.put(loggerName, config.getConfiguredLevel());

        // Set new level
        loggingSystem.setLogLevel(loggerName, level);

        // Schedule reset
        scheduler.schedule(() -> resetLogLevel(loggerName), duration.toMillis(), TimeUnit.MILLISECONDS);
    }

    private fun resetLogLevel(String loggerName): void {
        LogLevel originalLevel = originalLevels . remove (loggerName);
        loggingSystem.setLogLevel(loggerName, originalLevel);
    }
}
```