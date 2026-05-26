# Distributed Tracing with Spring Boot Actuator

Spring Boot Actuator provides dependency management and auto-configuration
for [Micrometer Tracing](https://micrometer.io/docs/tracing), a facade for popular tracer libraries.

> **TIP**
>
> To learn more about Micrometer Tracing capabilities, see
> its [reference documentation](https://micrometer.io/docs/tracing).

## Supported Tracers

Spring Boot ships auto-configuration for the following tracers:

- [OpenTelemetry](https://opentelemetry.io/)
  with [Zipkin](https://zipkin.io/), [Wavefront](https://docs.wavefront.com/),
  or [OTLP](https://opentelemetry.io/docs/reference/specification/protocol/)
- [OpenZipkin Brave](https://github.com/openzipkin/brave) with [Zipkin](https://zipkin.io/)
  or [Wavefront](https://docs.wavefront.com/)

## Getting Started with OpenTelemetry and Zipkin

### Dependencies

Add the following dependencies to your project:

**Maven:**

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-tracing-bridge-otel</artifactId>
    </dependency>
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-exporter-zipkin</artifactId>
    </dependency>
</dependencies>
```

**Gradle:**

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-tracing-bridge-otel'
    implementation 'io.opentelemetry:opentelemetry-exporter-zipkin'
}
```

### Configuration

Add the following application properties:

```yaml
management:
  tracing:
    sampling:
      probability: 1.0  # Sample 100% of requests in development
  zipkin:
    tracing:
      endpoint: "http://localhost:9411/api/v2/spans"

logging:
  pattern:
    level: "%5p [%X{traceId:-},%X{spanId:-}]"
```

### Basic Application Example

```kotlin
@SpringBootApplication
@RestController
class MyApplication {

    private static final Logger logger = LoggerFactory.getLogger(MyApplication.class);

    @GetMapping("/")
    fun home(): String {
        logger.info("Handling home request");
        return "Hello World!";
    }

    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

## Configuration Options

### Sampling Configuration

Control which traces are collected:

```yaml
management:
  tracing:
    sampling:
      probability: 0.1  # Sample 10% of requests in production
      rate: 100         # Maximum 100 traces per second
```

### Zipkin Configuration

```yaml
management:
  zipkin:
    tracing:
      endpoint: "http://zipkin:9411/api/v2/spans"
      timeout: 1s
      connect-timeout: 1s
      read-timeout: 10s
```

### OpenTelemetry OTLP Configuration

```yaml
management:
  otlp:
    tracing:
      endpoint: "http://otlp-collector:4318/v1/traces"
      timeout: 1s
      compression: gzip
      headers:
        Authorization: "Bearer your-token"
```

### Wavefront Configuration

```yaml
management:
  wavefront:
    tracing:
      application-name: "my-application"
      service-name: "my-service"
    api-token: "${WAVEFRONT_API_TOKEN}"
    uri: "https://your-instance.wavefront.com"
```

## Custom Spans

### Using `@`Observed Annotation

```kotlin
@Service
class UserService {

    @Observed(name = "user.service.find-by-id")
    fun findById(Long id): User {
        // Service logic
        return userRepository.findById(id);
    }

    @Observed(
        name = "user.service.create", 
        contextualName = "creating-user",
        lowCardinalityKeyValues = {"operation", "create"}
    )
    fun createUser(CreateUserRequest request): User {
        // Creation logic
        return save(request.toUser());
    }
}
```

### Programmatic Span Creation

```kotlin
@Service
class OrderService {

    private val observationRegistry: ObservationRegistry

    public OrderService(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    fun processOrder(OrderRequest request): Order {
        return Observation.createNotStarted("order.processing", observationRegistry)
            .lowCardinalityKeyValue("order.type", request.getType())
            .observe(() -> {
                // Add custom tags
                Observation.Scope scope = Observation.start("order.validation", observationRegistry);
                try {
                    validateOrder(request);
                } finally {
                    scope.close();
                }
                
                // Process order
                return saveOrder(request);
            });
    }

    private fun validateOrder(OrderRequest request): void {
        // Validation logic
    }

    private fun saveOrder(OrderRequest request): Order {
        // Save logic
        return Order();
    }
}
```

### Using Micrometer's Tracer API

```kotlin
@Service
class PaymentService {

    private val tracer: Tracer

    public PaymentService(Tracer tracer) {
        this.tracer = tracer;
    }

    fun processPayment(PaymentRequest request): PaymentResult {
        Span span = tracer.nextSpan()
            .name("payment.processing")
            .tag("payment.method", request.getMethod())
            .tag("payment.amount", String.valueOf(request.getAmount()))
            .start();

        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
            // Add events
            span.event("payment.validation.started");
            validatePayment(request);
            span.event("payment.validation.completed");

            span.event("payment.processing.started");
            PaymentResult result = processPaymentInternal(request);
            span.event("payment.processing.completed");

            // Add result information
            span.tag("payment.status", result.getStatus());
            return result;
        } catch (Exception ex) {
            span.tag("error", ex.getMessage());
            throw ex;
        } finally {
            span.end();
        }
    }

    private fun validatePayment(PaymentRequest request): void {
        // Validation logic
    }

    private fun processPaymentInternal(PaymentRequest request): PaymentResult {
        // Processing logic
        return PaymentResult();
    }
}
```

## Baggage

Baggage allows you to pass context information across service boundaries:

```kotlin
@Service
class UserService {

    private val baggageManager: BaggageManager

    public UserService(BaggageManager baggageManager) {
        this.baggageManager = baggageManager;
    }

    fun getCurrentUser(String userId): User {
        // Set baggage that will be propagated to downstream services
        try (BaggageInScope baggageInScope = 
                baggageManager.createBaggage("user.id", userId).makeCurrent()) {
            
            return fetchUserFromDatabase(userId);
        }
    }

    private fun fetchUserFromDatabase(String userId): User {
        // This method and any downstream calls will have access to the baggage
        String currentUserId = baggageManager.getBaggage("user.id").get();
        // Use the user ID for security context, logging, etc.
        return userRepository.findById(userId);
    }
}
```

## HTTP Client Tracing

### WebClient Tracing

Spring Boot automatically configures tracing for WebClient:

```kotlin
@Service
class ExternalApiService {

    private val webClient: WebClient

    public ExternalApiService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            .baseUrl("https://api.example.com")
            .build();
    }

    fun callExternalApi(String data): ApiResponse {
        return webClient
            .post()
            .uri("/process")
            .bodyValue(data)
            .retrieve()
            .bodyToMono(ApiResponse.class)
            .block();
    }
}
```

### RestTemplate Tracing

For RestTemplate, add the interceptor manually:

```kotlin
@Configuration
class RestTemplateConfig {

    @Bean
    fun restTemplate(RestTemplateBuilder builder): RestTemplate {
        return builder
            .interceptors(TraceRestTemplateInterceptor())
            .build();
    }
}
```

## Database Tracing

### JPA/Hibernate Tracing

Enable SQL tracing with additional configuration:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        generate_statistics: true
        session:
          events:
            log:
              LOG_QUERIES_SLOWER_THAN_MS: 25

management:
  tracing:
    enabled: true
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
```

### Custom Database Observation

```kotlin
@Repository
class UserRepository {

    private val jdbcTemplate: JdbcTemplate
    private val observationRegistry: ObservationRegistry

    public UserRepository(JdbcTemplate jdbcTemplate, 
                         ObservationRegistry observationRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.observationRegistry = observationRegistry;
    }

    fun findById(Long id): User {
        return Observation.createNotStarted("db.user.find-by-id", observationRegistry)
            .lowCardinalityKeyValue("db.operation", "select")
            .lowCardinalityKeyValue("db.table", "users")
            .observe(() -> {
                String sql = "SELECT * FROM users WHERE id = ?";
                return jdbcTemplate.queryForObject(sql, 
                    UserRowMapper(), id);
            });
    }
}
```

## Async Processing Tracing

### `@`Async Methods

```kotlin
@Service
class NotificationService {

    @Async
    @Observed(name = "notification.send")
    public CompletableFuture<Void> sendNotificationAsync(String recipient, String message) {
        // Async notification logic
        return CompletableFuture.completedFuture(null);
    }
}
```

### Manual Trace Propagation

```kotlin
@Service
class EmailService {

    private val tracer: Tracer
    private val executorService: ExecutorService

    public EmailService(Tracer tracer) {
        this.tracer = tracer;
        this.executorService = Executors.newFixedThreadPool(5);
    }

    fun sendEmailAsync(String recipient, String subject, String body): void {
        TraceContext traceContext = tracer.currentSpan().context();
        
        executorService.submit(() -> {
            try (Tracer.SpanInScope ws = tracer.withSpanInScope(
                    tracer.toSpan(traceContext))) {
                Span span = tracer.nextSpan()
                    .name("email.send")
                    .tag("email.recipient", recipient)
                    .start();
                
                try (Tracer.SpanInScope emailScope = tracer.withSpanInScope(span)) {
                    // Send email logic
                    sendEmailInternal(recipient, subject, body);
                } finally {
                    span.end();
                }
            }
        });
    }

    private fun sendEmailInternal(String recipient, String subject, String body): void {
        // Email sending implementation
    }
}
```

## Production Configuration

### Performance Optimizations

```yaml
management:
  tracing:
    sampling:
      probability: 0.01  # Sample 1% in production
      rate: 1000        # Max 1000 traces per second
    baggage:
      enabled: false    # Disable if not needed
      remote-fields: []
    zipkin:
      tracing:
        endpoint: "${ZIPKIN_ENDPOINT:http://zipkin:9411/api/v2/spans}"
        timeout: 1s
        connect-timeout: 1s

# Optimize logging for performance
logging:
  pattern:
    level: "%5p [%X{traceId:-},%X{spanId:-}]"
  level:
    io.micrometer.tracing: WARN
    org.springframework.web.servlet.mvc.method.annotation: WARN
```

### Security Considerations

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "health,info,metrics"
        exclude: "trace"  # Don't expose trace endpoint
  endpoint:
    trace:
      enabled: false
  tracing:
    baggage:
      correlation:
        enabled: false  # Disable MDC correlation if sensitive data
      remote-fields: []   # Don't propagate sensitive fields
```

## Troubleshooting

### Common Issues

1. **No traces appearing**: Check sampling probability and endpoint configuration
2. **High overhead**: Reduce sampling probability or disable baggage
3. **Missing spans**: Ensure proper dependency injection of ObservationRegistry
4. **Broken trace context**: Check async processing and thread boundaries

### Debug Configuration

```yaml
logging:
  level:
    io.micrometer.tracing: DEBUG
    io.opentelemetry: DEBUG
    brave: DEBUG
    zipkin2: DEBUG

management:
  tracing:
    sampling:
      probability: 1.0  # Sample everything for debugging
```

### Health Check for Tracing

```kotlin
@Component
class TracingHealthIndicator implements HealthIndicator {

    private val tracer: Tracer

    public TracingHealthIndicator(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    fun health(): Health {
        try {
            Span span = tracer.nextSpan().name("health.check.tracing").start();
            span.end();
            return Health.up()
                .withDetail("tracer", tracer.getClass().getSimpleName())
                .build();
        } catch (Exception ex) {
            return Health.down()
                .withDetail("error", ex.getMessage())
                .build();
        }
    }
}
```

## Best Practices

1. **Sampling Strategy**: Use lower sampling rates in production (1-10%)
2. **Span Naming**: Use consistent, meaningful span names with low cardinality
3. **Tag Strategy**: Add meaningful tags but avoid high-cardinality values
4. **Error Handling**: Always properly handle and tag errors in spans
5. **Performance**: Monitor the overhead of tracing in production
6. **Security**: Be careful not to include sensitive data in span tags or baggage
7. **Correlation**: Use correlation IDs to link traces across service boundaries
8. **Testing**: Include tracing in your testing strategy with TestObservationRegistry