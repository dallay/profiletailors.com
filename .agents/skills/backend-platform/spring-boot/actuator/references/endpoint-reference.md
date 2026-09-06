# Endpoint Reference

This document provides a comprehensive reference for all available Spring Boot Actuator endpoints.

## Built-in Endpoints

| Endpoint           | HTTP Method | Description                                     | Default Exposure |
|--------------------|-------------|-------------------------------------------------|------------------|
| `auditevents`      | GET         | Audit events for the application                | JMX              |
| `beans`            | GET         | Complete list of Spring beans                   | JMX              |
| `caches`           | GET, DELETE | Available caches                                | JMX              |
| `conditions`       | GET         | Configuration and auto-configuration conditions | JMX              |
| `configprops`      | GET         | Configuration properties                        | JMX              |
| `env`              | GET, POST   | Environment properties                          | JMX              |
| `flyway`           | GET         | Flyway database migrations                      | JMX              |
| `health`           | GET         | Application health information                  | Web, JMX         |
| `heapdump`         | GET         | Heap dump                                       | JMX              |
| `httpexchanges`    | GET         | HTTP exchange information                       | JMX              |
| `info`             | GET         | Application information                         | Web, JMX         |
| `integrationgraph` | GET         | Spring Integration graph                        | JMX              |
| `logfile`          | GET         | Application log file                            | JMX              |
| `loggers`          | GET, POST   | Logger configuration                            | JMX              |
| `liquibase`        | GET         | Liquibase database migrations                   | JMX              |
| `mappings`         | GET         | Request mapping information                     | JMX              |
| `metrics`          | GET         | Application metrics                             | JMX              |
| `prometheus`       | GET         | Prometheus metrics                              | None             |
| `quartz`           | GET         | Quartz scheduler information                    | JMX              |
| `scheduledtasks`   | GET         | Scheduled tasks                                 | JMX              |
| `sessions`         | GET, DELETE | User sessions                                   | JMX              |
| `shutdown`         | POST        | Graceful application shutdown                   | JMX              |
| `startup`          | GET         | Application startup information                 | JMX              |
| `threaddump`       | GET         | Thread dump                                     | JMX              |

## Endpoint URLs

### Web Endpoints

- Base path: `/actuator`
- Example: `GET /actuator/health`
- Custom base path: `management.endpoints.web.base-path`

### JMX Endpoints

- Domain: `org.springframework.boot`
- Example: `org.springframework.boot:type=Endpoint,name=Health`

## Endpoint Configuration

### Global Configuration

```yaml
management:
  endpoints:
    enabled-by-default: true
    web:
      exposure:
        include: "health,info,metrics"
        exclude: "env,beans"
      base-path: "/actuator"
      path-mapping:
        health: "status"
    jmx:
      exposure:
        include: "*"
```

### Individual Endpoint Configuration

```yaml
management:
  endpoint:
    health:
      enabled: true
      show-details: when-authorized
      show-components: always
      cache:
        time-to-live: 10s
    metrics:
      enabled: true
      cache:
        time-to-live: 0s
    info:
      enabled: true
```

## Security Configuration

### Web Security

```kotlin
@Configuration
class ActuatorSecurityConfiguration {

    @Bean
    @Order(1)
    fun actuatorSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .securityMatcher(EndpointRequest.toAnyEndpoint())
            .authorizeHttpRequests { requests ->
                requests
                    .requestMatchers(EndpointRequest.to("health", "info")).permitAll()
                    .anyRequest().hasRole("ACTUATOR")
            }
            .httpBasic(withDefaults())
            .build()
    }
}
```

### Method-level Security

```kotlin
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/actuator/shutdown")
fun shutdown(): Object {
    // Shutdown logic
}
```

## Custom Endpoints

### Creating Custom Endpoints

```kotlin
@Component
@Endpoint(id = "custom")
class CustomEndpoint {

    @ReadOperation
    fun customEndpoint(): Map<String, Any> {
        return mapOf("custom" to "data")
    }

    @WriteOperation
    fun writeOperation(@Selector name: String, value: String) {
        // Write operation
    }

    @DeleteOperation
    fun deleteOperation(@Selector name: String) {
        // Delete operation
    }
}
```

### Web-specific Endpoints

```kotlin
@Component
@WebEndpoint(id = "web-custom")
class WebCustomEndpoint {

    @ReadOperation
    fun webCustomEndpoint(): WebEndpointResponse<Map<String, Any>> {
        val data = mapOf("web" to "specific")
        return WebEndpointResponse(data, 200)
    }
}
```

## Best Practices

1. **Security**: Always secure actuator endpoints in production
2. **Exposure**: Only expose necessary endpoints
3. **Performance**: Configure appropriate caching for endpoints
4. **Monitoring**: Monitor actuator endpoint usage
5. **Documentation**: Document custom endpoints thoroughly
