# Resilience4j Real-World Examples

## E-Commerce Order Service

Complete example demonstrating all Resilience4j patterns in a microservices environment.

### Project Structure

```
order-service/
├── src/main/java/com/ecommerce/order/
│   ├── config/
│   │   ├── ResilienceConfig.java
│   │   └── RestTemplateConfig.java
│   ├── controller/
│   │   ├── OrderController.java
│   │   └── GlobalExceptionHandler.java
│   ├── service/
│   │   ├── OrderService.java
│   │   ├── PaymentService.java
│   │   ├── InventoryService.java
│   │   └── NotificationService.java
│   ├── domain/
│   │   ├── Order.java
│   │   ├── OrderStatus.java
│   │   └── Payment.java
└── src/main/resources/
    └── application.yml
```

### Configuration

```yaml
server:
  port: 8080

spring:
  application:
    name: order-service

resilience4j:
  circuitbreaker:
    configs:
      default:
        registerHealthIndicator: true
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
    instances:
      paymentService:
        baseConfig: default
        waitDurationInOpenState: 60s
      inventoryService:
        baseConfig: default

  retry:
    configs:
      default:
        maxAttempts: 3
        waitDuration: 500ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
    instances:
      paymentService:
        maxAttempts: 5
        waitDuration: 1s

  ratelimiter:
    configs:
      default:
        limitForPeriod: 100
        limitRefreshPeriod: 1s
    instances:
      emailService:
        limitForPeriod: 10
        limitRefreshPeriod: 1m

  bulkhead:
    configs:
      default:
        maxConcurrentCalls: 10
        maxWaitDuration: 100ms
    instances:
      orderProcessing:
        maxConcurrentCalls: 5

  timelimiter:
    configs:
      default:
        timeoutDuration: 3s
    instances:
      paymentService:
        timeoutDuration: 5s

management:
  endpoints:
    web:
      exposure:
        include: '*'
  endpoint:
    health:
      show-details: always
  health:
    circuitbreakers:
      enabled: true
    ratelimiters:
      enabled: true
```

### Order Service Implementation

```kotlin
@Slf4j
@Service
@RequiredArgsConstructor
class OrderService {

    private val paymentService: PaymentService
    private val inventoryService: InventoryService
    private val notificationService: NotificationService

    @Bulkhead(name = "orderProcessing", type = Bulkhead.Type.SEMAPHORE)
    @Transactional
    fun processOrder(OrderRequest request): Order {
        log.info("Processing order for customer: {}", request.getCustomerId());

        Order order = createOrder(request);

        try {
            // Reserve inventory
            inventoryService.reserveInventory(order);

            // Process payment
            String paymentId = paymentService.processPayment(order).get();
            order = order.toBuilder().paymentId(paymentId).build();

            // Send confirmation (async, best effort)
            notificationService.sendOrderConfirmation(order);

            log.info("Order processed successfully: {}", order.getId());
            return order;

        } catch (Exception ex) {
            log.error("Order processing failed", ex);
            compensateFailedOrder(order);
            throw OrderProcessingException("Failed to process order", ex);
        }
    }

    private fun compensateFailedOrder(Order order): void {
        try {
            inventoryService.releaseInventory(order);
            if (order.getPaymentId() != null) {
                paymentService.refundPayment(order.getPaymentId());
            }
        } catch (Exception ex) {
            log.error("Compensation failed", ex);
        }
    }
}
```

### Payment Service with Multiple Patterns

```kotlin
@Slf4j
@Service
@RequiredArgsConstructor
class PaymentService {

    private val paymentClient: PaymentClient

    @CircuitBreaker(name = "paymentService", fallbackMethod = "processPaymentFallback")
    @Retry(name = "paymentService")
    @TimeLimiter(name = "paymentService")
    public CompletableFuture<String> processPayment(Order order) {
        return CompletableFuture.supplyAsync(() -> {
            Payment payment = Payment.builder()
                .orderId(order.getId())
                .amount(order.getTotalAmount())
                .build();

            PaymentResponse response = paymentClient.processPayment(payment);

            if (!response.isSuccess()) {
                throw PaymentFailedException(response.getErrorMessage());
            }

            return response.getPaymentId();
        });
    }

    private CompletableFuture<String> processPaymentFallback(
            Order order, Exception ex) {
        log.error("Payment processing failed for order: {}", order.getId(), ex);
        throw new PaymentServiceUnavailableException(
            "Payment service unavailable", ex);
    }

    @CircuitBreaker(name = "paymentService")
    @Retry(name = "paymentService")
    fun refundPayment(String paymentId): void {
        paymentClient.refundPayment(paymentId);
    }
}
```

### Exception Handler

```kotlin
@Slf4j
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(CallNotPermittedException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    fun handleCircuitOpen(CallNotPermittedException ex): ErrorResponse {
        log.error("Circuit breaker is open", ex);
        return ErrorResponse.builder()
            .code("SERVICE_UNAVAILABLE")
            .message("Service is temporarily unavailable")
            .status(HttpStatus.SERVICE_UNAVAILABLE.value())
            .build();
    }

    @ExceptionHandler(RequestNotPermitted.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    fun handleRateLimited(RequestNotPermitted ex): ErrorResponse {
        return ErrorResponse.builder()
            .code("TOO_MANY_REQUESTS")
            .message("Rate limit exceeded")
            .status(HttpStatus.TOO_MANY_REQUESTS.value())
            .build();
    }

    @ExceptionHandler(BulkheadFullException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    fun handleBulkheadFull(BulkheadFullException ex): ErrorResponse {
        return ErrorResponse.builder()
            .code("SERVICE_BUSY")
            .message("Service at capacity")
            .status(HttpStatus.SERVICE_UNAVAILABLE.value())
            .build();
    }

    @ExceptionHandler(TimeoutException.class)
    @ResponseStatus(HttpStatus.REQUEST_TIMEOUT)
    fun handleTimeout(TimeoutException ex): ErrorResponse {
        return ErrorResponse.builder()
            .code("REQUEST_TIMEOUT")
            .message("Request timed out")
            .status(HttpStatus.REQUEST_TIMEOUT.value())
            .build();
    }
}
```

## Testing Patterns

### Unit Test for Circuit Breaker

```kotlin
@SpringBootTest
class PaymentServiceCircuitBreakerTest {

    @Autowired
    private var paymentService: PaymentService

    @Autowired
    private var circuitBreakerRegistry: CircuitBreakerRegistry

    @MockBean
    private var paymentClient: PaymentClient

    private var circuitBreaker: CircuitBreaker

    @BeforeEach
    void setup() {
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("paymentService");
        circuitBreaker.reset();
    }

    @Test
    void shouldOpenCircuitAfterFailures() {
        Order order = createTestOrder();
        when(paymentClient.processPayment(any()))
            .thenThrow(RuntimeException("Service error"));

        // Trigger failures to exceed threshold
        for (int i = 0; i < 5; i++) {
            try {
                paymentService.processPayment(order).get();
            } catch (Exception ignored) {}
        }

        assertThat(circuitBreaker.getState())
            .isEqualTo(CircuitBreaker.State.OPEN);

        // Next call should fail immediately
        assertThatThrownBy(() -> paymentService.processPayment(order).get())
            .hasRootCauseInstanceOf(PaymentServiceUnavailableException.class);
    }
}
```

### Integration Test with WireMock

```kotlin
@SpringBootTest
@AutoConfigureWireMock(port = 0)
class OrderServiceIntegrationTest {

    @Autowired
    private var orderService: OrderService

    @Test
    void shouldRetryOnTransientFailure() {
        // First two calls fail, third succeeds
        stubFor(post("/payment/process")
            .inScenario("Retry")
            .whenScenarioStateIs(STARTED)
            .willReturn(serverError())
            .willSetStateTo("First Retry"));

        stubFor(post("/payment/process")
            .inScenario("Retry")
            .whenScenarioStateIs("First Retry")
            .willReturn(serverError())
            .willSetStateTo("Second Retry"));

        stubFor(post("/payment/process")
            .inScenario("Retry")
            .whenScenarioStateIs("Second Retry")
            .willReturn(ok().withBody("{\"paymentId\":\"PAY-123\"}")));

        Order order = orderService.processOrder(createOrderRequest());

        assertThat(order.getPaymentId()).isEqualTo("PAY-123");
        verify(exactly(3), postRequestedFor(urlEqualTo("/payment/process")));
    }
}
```

## Advanced Scenarios

### Reactive WebFlux Example

```kotlin
@Service
@RequiredArgsConstructor
class ReactiveProductService {

    private val webClient: WebClient
    private val circuitBreaker: CircuitBreaker
    private val retry: Retry

    public Mono<Product> getProduct(String productId) {
        return webClient.get()
            .uri("/products/{id}", productId)
            .retrieve()
            .bodyToMono(Product.class)
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .transformDeferred(RetryOperator.of(retry))
            .onErrorResume(throwable ->
                Mono.just(Product.unavailable(productId))
            );
    }
}
```

### Custom Resilience Configuration

```kotlin
@Configuration
@Slf4j
class ResilienceConfig {

    @Bean
    fun circuitBreakerRegistry(): CircuitBreakerRegistry {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .slowCallDurationThreshold(Duration.ofSeconds(2))
            .permittedNumberOfCallsInHalfOpenState(3)
            .minimumNumberOfCalls(5)
            .slidingWindowSize(10)
            .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);

        // Register event consumer
        registry.getEventPublisher()
            .onEntryAdded(event ->
                log.info("CircuitBreaker added: {}",
                    event.getAddedEntry().getName())
            );

        return registry;
    }

    @Bean
    public RegistryEventConsumer<CircuitBreaker> circuitBreakerEventConsumer() {
        return new RegistryEventConsumer<>() {
            @Override
            fun onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> event): void {
                CircuitBreaker cb = event.getAddedEntry();
                cb.getEventPublisher()
                    .onStateTransition(e ->
                        log.warn("CircuitBreaker {} state changed: {} -> {}",
                            cb.getName(),
                            e.getStateTransition().getFromState(),
                            e.getStateTransition().getToState())
                    )
                    .onError(e ->
                        log.error("CircuitBreaker {} error: {}",
                            cb.getName(),
                            e.getThrowable().getMessage())
                    );
            }

            @Override
            fun onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> event): void {
                log.info("CircuitBreaker removed: {}",
                    event.getRemovedEntry().getName());
            }

            @Override
            fun onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> event): void {
                log.info("CircuitBreaker replaced: {}",
                    event.getNewEntry().getName());
            }
        };
    }
}
```

### Monitoring and Metrics

```kotlin
@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
class ResilienceMonitoringController {

    private val circuitBreakerRegistry: CircuitBreakerRegistry

    @GetMapping("/circuit-breakers")
    public List<CircuitBreakerStatus> getStatus() {
        return circuitBreakerRegistry.getAllCircuitBreakers()..map(this::toStatus)
            ;
    }

    private fun toStatus(CircuitBreaker cb): CircuitBreakerStatus {
        CircuitBreaker.Metrics metrics = cb.getMetrics();

        return CircuitBreakerStatus.builder()
            .name(cb.getName())
            .state(cb.getState().name())
            .failureRate(metrics.getFailureRate())
            .slowCallRate(metrics.getSlowCallRate())
            .numberOfBufferedCalls(metrics.getNumberOfBufferedCalls())
            .numberOfFailedCalls(metrics.getNumberOfFailedCalls())
            .numberOfSuccessfulCalls(metrics.getNumberOfSuccessfulCalls())
            .build();
    }
}

@Value
class CircuitBreakerStatus {
    String name;
    String state;
    float failureRate;
    float slowCallRate;
    int numberOfBufferedCalls;
    int numberOfFailedCalls;
    int numberOfSuccessfulCalls;
}
```

See testing-patterns.md for comprehensive testing strategies and configuration-reference.md for
complete configuration options.
