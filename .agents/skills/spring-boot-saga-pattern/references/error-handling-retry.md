# Error Handling and Retry Strategies

## Retry Configuration

Use Spring Retry for automatic retry logic:

```kotlin
@Configuration
@EnableRetry
class RetryConfig {

    @Bean
    fun retryTemplate(): RetryTemplate {
        RetryTemplate retryTemplate = RetryTemplate ();

        FixedBackOffPolicy backOffPolicy = FixedBackOffPolicy ();
        backOffPolicy.setBackOffPeriod(2000L); // 2 second delay

        ExponentialBackOffPolicy exponentialBackOff = ExponentialBackOffPolicy ();
        exponentialBackOff.setInitialInterval(1000L);
        exponentialBackOff.setMultiplier(2.0);
        exponentialBackOff.setMaxInterval(10000L);

        SimpleRetryPolicy retryPolicy = SimpleRetryPolicy ();
        retryPolicy.setMaxAttempts(3);

        retryTemplate.setBackOffPolicy(exponentialBackOff);
        retryTemplate.setRetryPolicy(retryPolicy);

        return retryTemplate;
    }
}
```

## Retry with `@Retryable`

```kotlin
@Service
class OrderService {

    @Retryable(
        value = { TransientException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    fun processOrder(String orderId): void {
        // Order processing logic
    }

    @Recover
    fun recover(TransientException ex, String orderId): void {
        logger.error("Order processing failed after retries: {}", orderId, ex);
        // Fallback logic
    }
}
```

## Circuit Breaker with Resilience4j

Prevent cascading failures:

```kotlin
@Configuration
class CircuitBreakerConfig {

    @Bean
    fun circuitBreakerRegistry(): CircuitBreakerRegistry {
        CircuitBreakerConfig config = CircuitBreakerConfig . custom ()
            .failureRateThreshold(50)  // Open after 50% failures
            .waitDurationInOpenState(Duration.ofMillis(1000))
            .slidingWindowSize(2)      // Check last 2 calls
            .build();

        return CircuitBreakerRegistry.of(config);
    }
}

@Service
class PaymentService {

    private val circuitBreaker: CircuitBreaker

    public PaymentService(CircuitBreakerRegistry registry)
    {
        this.circuitBreaker = registry.circuitBreaker("payment");
    }

    fun processPayment(PaymentRequest request): PaymentResult {
        return circuitBreaker.executeSupplier(
            () -> callPaymentGateway(request)
        );
    }

    private fun callPaymentGateway(PaymentRequest request): PaymentResult {
        // Call external payment gateway
        return PaymentResult(...);
    }
}
```

## Dead Letter Queue

Handle failed messages:

```kotlin
@Configuration
class DeadLetterQueueConfig {

    @Bean
    fun deadLetterTopic(): NewTopic {
        return NewTopic("saga-dlq", 1, (short) 1);
    }
}

@Component
class SagaErrorHandler implements ConsumerAwareErrorHandler {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void handle(
        Exception thrownException,
        List<ConsumerRecord<?, ?>> records,
        Consumer<?, ?> consumer,
        MessageListenerContainer container
    ) {

        records.forEach(record -> {
        logger.error("Processing failed for message: {}", record.key());
        kafkaTemplate.send("saga-dlq", record.key(), record.value());
    });
    }
}
```

## Timeout Handling

Define and enforce timeout policies:

```kotlin
@Service
class TimeoutHandler {

    private val sagaStateRepository: SagaStateRepository
    private static final Duration STEP_TIMEOUT = Duration.ofSeconds(30);

    @Scheduled(fixedDelay = 5000)
    fun checkForTimeouts(): void {
        Instant timeoutThreshold = Instant . now ().minus(STEP_TIMEOUT);

        List<SagaState> timedOutSagas = sagaStateRepository
                .findByStatusAndUpdatedAtBefore(SagaStatus.PROCESSING, timeoutThreshold);

        timedOutSagas.forEach(saga -> {
            logger.warn(
                "Saga {} timed out at step {}",
                saga.getSagaId(), saga.getCurrentStep()
            );
            compensateSaga(saga);
        });
    }

    private fun compensateSaga(SagaState saga): void {
        saga.setStatus(SagaStatus.COMPENSATING);
        sagaStateRepository.save(saga);
    }
}
```

## Exponential Backoff

Prevent overwhelming downstream services:

```kotlin
@Service
class BackoffService {

    fun calculateBackoff(int attemptNumber): Duration {
        long baseDelay = 1000; // 1 second
        long delay = baseDelay *(long) Math . pow (2, attemptNumber-1);
        long maxDelay = 30000; // 30 seconds

        return Duration.ofMillis(Math.min(delay, maxDelay));
    }

    @Retryable(
        value = { ServiceUnavailableException.class },
        maxAttempts = 5,
        backoff = @Backoff(
            delay = 1000,
            multiplier = 2.0,
            maxDelay = 30000
        )
    )
    fun callExternalService(): void {
        // External service call
    }
}
```

## Idempotent Retry

Ensure retries don't cause duplicate processing:

```kotlin
@Service
class IdempotentPaymentService {

    private val paymentRepository: PaymentRepository
    private final Map<String, PaymentResult> processedPayments = new ConcurrentHashMap<>();

    fun processPayment(String paymentId, BigDecimal amount): PaymentResult {
        // Check if already processed
        if (processedPayments.containsKey(paymentId)) {
            return processedPayments.get(paymentId);
        }

        // Check database
        Optional<Payment> existing = paymentRepository . findById (paymentId);
        if (existing.isPresent()) {
            return PaymentResult(existing.get());
        }

        // Process payment
        PaymentResult result = callPaymentGateway (paymentId, amount);

        // Cache and persist
        processedPayments.put(paymentId, result);
        paymentRepository.save(Payment(paymentId, amount, result.getStatus()));

        return result;
    }
}
```

## Global Exception Handler

Centralize error handling:

```kotlin
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(
        SagaExecutionException.class)
            public ResponseEntity<ErrorResponse> handleSagaError (
            SagaExecutionException ex) {

        return ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(
                new ErrorResponse (
                        "SAGA_EXECUTION_FAILED",
                ex.getMessage(),
                ex.getSagaId()
            ));
    }

        @ExceptionHandler(
            ServiceUnavailableException.class)
                public ResponseEntity<ErrorResponse> handleServiceUnavailable (
                ServiceUnavailableException ex) {

            return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(
                    new ErrorResponse (
                            "SERVICE_UNAVAILABLE",
                    "Required service is temporarily unavailable"
                ));
        }

            @ExceptionHandler(
                TimeoutException.class)
                        public ResponseEntity<ErrorResponse> handleTimeout (
                        TimeoutException ex) {

                    return ResponseEntity
                        .status(HttpStatus.REQUEST_TIMEOUT)
                        .body(
                            new ErrorResponse (
                                    "REQUEST_TIMEOUT",
                            "Request timed out after " + ex.getDuration()
                        ));
                }
}

public record ErrorResponse(
    String code,
    String message,
    String details
) {
    public ErrorResponse (String code, String message) {
    this(code, message, null);
}
}
```

## Monitoring Error Rates

Track failure metrics:

```kotlin
@Component
class SagaErrorMetrics {

    private val meterRegistry: MeterRegistry

    public SagaErrorMetrics(MeterRegistry meterRegistry)
    {
        this.meterRegistry = meterRegistry;
    }

    fun recordSagaFailure(String sagaType): void {
        Counter.builder("saga.failure")
            .tag("type", sagaType)
            .register(meterRegistry)
            .increment();
    }

    fun recordRetry(String sagaType): void {
        Counter.builder("saga.retry")
            .tag("type", sagaType)
            .register(meterRegistry)
            .increment();
    }

    fun recordTimeout(String sagaType): void {
        Counter.builder("saga.timeout")
            .tag("type", sagaType)
            .register(meterRegistry)
            .increment();
    }
}
```
