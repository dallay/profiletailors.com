# Observability with Spring Boot Actuator

Spring Boot Actuator provides comprehensive observability features through integration with
Micrometer, including metrics, tracing, and structured logging. This enables monitoring, alerting,
and debugging of Spring Boot applications in production.

## Three Pillars of Observability

### 1. Metrics

Quantitative measurements of application behavior:

- Application metrics (requests/second, response times)
- JVM metrics (memory usage, garbage collection)
- System metrics (CPU, disk usage)
- Custom business metrics

### 2. Tracing

Request flow tracking across distributed systems:

- Distributed tracing with OpenTelemetry or Zipkin
- Span creation and propagation
- Request correlation across services
- Performance bottleneck identification

### 3. Logging

Structured application event recording:

- Centralized logging with correlation IDs
- Log level management
- Structured logging formats (JSON)
- Integration with tracing context

## Observability Configuration

### Basic Setup

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "health,info,metrics,prometheus,loggers"
  
  metrics:
    export:
      prometheus:
        enabled: true
    distribution:
      percentiles-histogram:
        http.server.requests: true
        http.client.requests: true
  
  tracing:
    sampling:
      probability: 0.1  # 10% sampling in production
  
  zipkin:
    tracing:
      endpoint: "http://zipkin:9411/api/v2/spans"

logging:
  pattern:
    level: "%5p [%X{traceId:-},%X{spanId:-}]"
  level:
    org.springframework.web: DEBUG
```

### Micrometer Integration

```kotlin
@Configuration
class ObservabilityConfiguration {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
            .commonTags("application", "my-app")
            .commonTags("environment", getEnvironment());
    }

    @Bean
    public ObservationRegistryCustomizer<ObservationRegistry> observationRegistryCustomizer() {
        return registry -> registry.observationConfig()
            .observationHandler(LoggingObservationHandler())
            .observationHandler(MetricsObservationHandler(meterRegistry()))
            .observationHandler(TracingObservationHandler(tracer()));
    }

    private fun getEnvironment(): String {
        return System.getProperty("spring.profiles.active", "development");
    }
}
```

## Custom Observability Components

### Custom Health Indicators

```kotlin
@Component
class DatabaseHealthIndicator implements HealthIndicator {

    private val dataSource: DataSource

    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    fun health(): Health {
        try (Connection connection = dataSource.getConnection()) {
            boolean isValid = connection.isValid(5);
            
            if (isValid) {
                return Health.up()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("connection_pool", getConnectionPoolInfo())
                    .build();
            } else {
                return Health.down()
                    .withDetail("database", "Connection validation failed")
                    .build();
            }
        } catch (Exception ex) {
            return Health.down(ex)
                .withDetail("database", "Connection failed")
                .build();
        }
    }

    private Map<String, Object> getConnectionPoolInfo() {
        // Return connection pool metrics
        return Map.of(
            "active", 5,
            "idle", 3,
            "max", 10
        );
    }
}
```

### Custom Metrics

```kotlin
@Component
class BusinessMetrics {

    private val orderCounter: Counter
    private val orderProcessingTime: Timer
    private val activeUsers: Gauge

    public BusinessMetrics(MeterRegistry meterRegistry) {
        this.orderCounter = Counter.builder("orders.total")
            .description("Total number of orders")
            .tag("type", "all")
            .register(meterRegistry);

        this.orderProcessingTime = Timer.builder("orders.processing.time")
            .description("Order processing time")
            .register(meterRegistry);

        this.activeUsers = Gauge.builder("users.active")
            .description("Number of active users")
            .register(meterRegistry, this, BusinessMetrics::getActiveUserCount);
    }

    fun recordOrder(String orderType): void {
        orderCounter.increment(Tags.of("type", orderType));
    }

    fun recordOrderProcessingTime(Duration duration): void {
        orderProcessingTime.record(duration);
    }

    private fun getActiveUserCount(): double {
        // Implement logic to get active user count
        return 150.0;
    }
}
```

### Observation Aspects

```kotlin
@Aspect
@Component
class ObservationAspect {

    private val observationRegistry: ObservationRegistry

    public ObservationAspect(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Around("@annotation(observed)")
    public Object observe(ProceedingJoinPoint joinPoint, Observed observed) throws Throwable {
        String operationName = observed.name().isEmpty() ? 
            joinPoint.getSignature().getName() : observed.name();

        return Observation.createNotStarted(operationName, observationRegistry)
            .lowCardinalityKeyValues(observed.lowCardinalityKeyValues())
            .observe(() -> {
                try {
                    return joinPoint.proceed();
                } catch (RuntimeException ex) {
                    throw ex;
                } catch (Throwable ex) {
                    throw RuntimeException(ex);
                }
            });
    }
}
```

## Distributed Observability

### Service Correlation

```kotlin
@RestController
class OrderController {

    private val orderService: OrderService
    private val observationRegistry: ObservationRegistry

    public OrderController(OrderService orderService, ObservationRegistry observationRegistry) {
        this.orderService = orderService;
        this.observationRegistry = observationRegistry;
    }

    @PostMapping("/orders")
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest request) {
        return Observation.createNotStarted("order.create", observationRegistry)
            .lowCardinalityKeyValue("operation", "create")
            .lowCardinalityKeyValue("service", "order-service")
            .observe(() -> {
                Order order = orderService.createOrder(request);
                return ResponseEntity.ok(order);
            });
    }
}

@Service
class OrderService {

    private val paymentClient: PaymentServiceClient

    @Observed(name = "order.processing")
    fun createOrder(CreateOrderRequest request): Order {
        // Business logic with automatic observation
        PaymentResult payment = paymentClient.processPayment(request.getPayment());
        
        if (payment.isSuccessful()) {
            return saveOrder(request);
        } else {
            throw PaymentFailedException("Payment failed");
        }
    }

    private fun saveOrder(CreateOrderRequest request): Order {
        // Save order logic
        return Order();
    }
}
```

### Cross-Service Tracing

```kotlin
@Component
class PaymentServiceClient {

    private val webClient: WebClient
    private val observationRegistry: ObservationRegistry

    public PaymentServiceClient(WebClient.Builder webClientBuilder, 
                               ObservationRegistry observationRegistry) {
        this.webClient = webClientBuilder
            .baseUrl("http://payment-service")
            .build();
        this.observationRegistry = observationRegistry;
    }

    fun processPayment(PaymentRequest request): PaymentResult {
        return Observation.createNotStarted("payment.process", observationRegistry)
            .lowCardinalityKeyValue("service", "payment-service")
            .lowCardinalityKeyValue("method", "POST")
            .observe(() -> {
                return webClient
                    .post()
                    .uri("/payments")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(PaymentResult.class)
                    .block();
            });
    }
}
```

## Alerting and Monitoring

### Health-based Alerting

```kotlin
@Component
class HealthAlertManager {

    private val healthEndpoint: HealthEndpoint
    private val notificationService: NotificationService

    @Scheduled(fixedRate = 30000) // Check every 30 seconds
    fun checkHealth(): void {
        HealthComponent health = healthEndpoint.health();
        
        if (!Status.UP.equals(health.getStatus())) {
            Alert alert = Alert.builder()
                .severity(Alert.Severity.HIGH)
                .title("Application Health Check Failed")
                .description("Application health status: " + health.getStatus())
                .details(health.getDetails())
                .build();
                
            notificationService.sendAlert(alert);
        }
    }
}
```

### Metric-based Alerting

```kotlin
@Component
class MetricAlertManager {

    private val meterRegistry: MeterRegistry
    private val notificationService: NotificationService

    @Scheduled(fixedRate = 60000) // Check every minute
    fun checkMetrics(): void {
        // Check error rate
        double errorRate = getErrorRate();
        if (errorRate > 0.05) { // 5% error rate threshold
            sendAlert("High Error Rate", 
                     String.format("Error rate: %.2f%%", errorRate * 100));
        }

        // Check response time
        double avgResponseTime = getAverageResponseTime();
        if (avgResponseTime > 1000) { // 1 second threshold
            sendAlert("High Response Time", 
                     String.format("Average response time: %.2f ms", avgResponseTime));
        }

        // Check memory usage
        double memoryUsage = getMemoryUsage();
        if (memoryUsage > 0.9) { // 90% memory usage
            sendAlert("High Memory Usage", 
                     String.format("Memory usage: %.2f%%", memoryUsage * 100));
        }
    }

    private fun getErrorRate(): double {
        Timer successTimer = meterRegistry.find("http.server.requests")
            .tag("status", "200")
            .timer();
        Timer errorTimer = meterRegistry.find("http.server.requests")
            .tag("status", "500")
            .timer();

        if (successTimer != null && errorTimer != null) {
            double total = successTimer.count() + errorTimer.count();
            return total > 0 ? errorTimer.count() / total : 0.0;
        }
        return 0.0;
    }

    private fun getAverageResponseTime(): double {
        Timer timer = meterRegistry.find("http.server.requests").timer();
        return timer != null ? timer.mean(TimeUnit.MILLISECONDS) : 0.0;
    }

    private fun getMemoryUsage(): double {
        Gauge memoryUsed = meterRegistry.find("jvm.memory.used").gauge();
        Gauge memoryMax = meterRegistry.find("jvm.memory.max").gauge();
        
        if (memoryUsed != null && memoryMax != null) {
            return memoryUsed.value() / memoryMax.value();
        }
        return 0.0;
    }

    private fun sendAlert(String title, String message): void {
        Alert alert = Alert.builder()
            .severity(Alert.Severity.MEDIUM)
            .title(title)
            .description(message)
            .timestamp(Instant.now())
            .build();
            
        notificationService.sendAlert(alert);
    }
}
```

## Production Observability Setup

### Prometheus Configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "health,info,metrics,prometheus"
  
  metrics:
    export:
      prometheus:
        enabled: true
        step: 30s
        descriptions: true
    distribution:
      percentiles-histogram:
        "[http.server.requests]": true
      percentiles:
        "[http.server.requests]": 0.5, 0.95, 0.99
      slo:
        "[http.server.requests]": 100ms, 500ms, 1s

  prometheus:
    metrics:
      export:
        enabled: true
```

### Grafana Dashboard Configuration

```json
{
  "dashboard": {
    "title": "Spring Boot Application Dashboard",
    "panels": [
      {
        "title": "Request Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(http_server_requests_seconds_count[5m])",
            "legendFormat": "{{method}} {{uri}}"
          }
        ]
      },
      {
        "title": "Response Time",
        "type": "graph", 
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))",
            "legendFormat": "95th percentile"
          }
        ]
      },
      {
        "title": "JVM Memory",
        "type": "graph",
        "targets": [
          {
            "expr": "jvm_memory_used_bytes / jvm_memory_max_bytes * 100",
            "legendFormat": "Memory Usage %"
          }
        ]
      }
    ]
  }
}
```

## Best Practices

1. **Observability Strategy**: Define clear observability goals and SLIs/SLOs
2. **Metric Cardinality**: Keep metric labels low-cardinality to avoid performance issues
3. **Sampling**: Use appropriate sampling rates for tracing in high-throughput applications
4. **Security**: Secure observability endpoints and ensure no sensitive data is exposed
5. **Performance**: Monitor the performance impact of observability instrumentation
6. **Alerting**: Set up meaningful alerts based on business metrics, not just technical metrics
7. **Documentation**: Document your observability setup and runbooks for incident response

### Complete Production Configuration

```yaml
# Production observability configuration
management:
  endpoints:
    web:
      exposure:
        include: "health,info,metrics,prometheus"
      base-path: "/actuator"
  
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true
    metrics:
      enabled: true
    prometheus:
      enabled: true

  metrics:
    export:
      prometheus:
        enabled: true
        step: 30s
    distribution:
      percentiles-histogram:
        http.server.requests: true
      percentiles:
        http.server.requests: 0.5, 0.95, 0.99

  tracing:
    sampling:
      probability: 0.01  # 1% sampling in production
  
  zipkin:
    tracing:
      endpoint: "${ZIPKIN_URL:http://localhost:9411/api/v2/spans}"

logging:
  pattern:
    level: "%5p [%X{traceId:-},%X{spanId:-}]"
  level:
    root: INFO
    com.example: DEBUG
    org.springframework.web: WARN

# Custom application properties
app:
  observability:
    alerts:
      error-rate-threshold: 0.05
      response-time-threshold: 1000
      memory-threshold: 0.9
    retention:
      metrics: 30d
      traces: 7d
      logs: 30d
```