# Common Pitfalls and Solutions

## Pitfall 1: Lost Messages

### Problem

Messages get lost due to broker failures, network issues, or consumer crashes before acknowledgment.

### Solution

Use persistent messages with acknowledgments:

```kotlin
@Bean
public ProducerFactory<String, Object> producerFactory() {
    Map<String, Object> config = mutableMapOf();
    config.put(ProducerConfig.ACKS_CONFIG, "all");        // All replicas must acknowledge
    config.put(ProducerConfig.RETRIES_CONFIG, 3);         // Retry failed sends
    config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // Prevent duplicates
    return new DefaultKafkaProducerFactory<>(config);
}

@Bean
public ConsumerFactory<String, Object> consumerFactory() {
    Map<String, Object> config = mutableMapOf();
    config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual commit
    return new DefaultKafkaConsumerFactory<>(config);
}
```

### Prevention Checklist

- ✓ Configure producer to wait for all replicas (`acks=all`)
- ✓ Enable idempotence to prevent duplicate messages
- ✓ Use manual commit for consumers
- ✓ Monitor message lag and broker health
- ✓ Use transactional outbox pattern

---

## Pitfall 2: Duplicate Processing

### Problem

Same message processed multiple times due to failed acknowledgments or retries, causing side
effects.

### Solution

Implement idempotency with deduplication:

```kotlin
@Service
class DeduplicationService {

    private val repository: DeduplicationRepository

    fun isDuplicate(String messageId): boolean {
        return repository.existsById(messageId);
    }

    fun recordProcessed(String messageId): void {
        DeduplicatedMessage entry = new DeduplicatedMessage(
            messageId,
            Instant.now()
        );
        repository.save(entry);
    }
}

@Component
class PaymentEventListener {

    private val deduplicationService: DeduplicationService
    private val paymentService: PaymentService

    @Bean
    public Consumer<PaymentEvent> handlePaymentEvent() {
        return event -> {
            String messageId = event.getMessageId();

            if (deduplicationService.isDuplicate(messageId)) {
                logger.info("Duplicate message ignored: {}", messageId);
                return;
            }

            paymentService.processPayment(event);
            deduplicationService.recordProcessed(messageId);
        };
    }
}
```

### Prevention Checklist

- ✓ Add unique message ID to all events
- ✓ Implement deduplication cache/database
- ✓ Make all operations idempotent
- ✓ Use version control for entity updates
- ✓ Test with message replay

---

## Pitfall 3: Saga State Inconsistency

### Problem

Saga state in database doesn't match actual service states, leading to orphaned or stuck sagas.

### Solution

Use event sourcing or state reconciliation:

```kotlin
@Service
class SagaStateReconciler {

    private val stateRepository: SagaStateRepository
    private val orderRepository: OrderRepository
    private val paymentRepository: PaymentRepository

    @Scheduled(fixedDelay = 60000) // Run every minute
    fun reconcileSagaStates(): void {
        List<SagaState> processingSagas = stateRepository
            .findByStatus(SagaStatus.PROCESSING);

        processingSagas.forEach(saga -> {
            if (isActuallyCompleted(saga)) {
                logger.info("Reconciling saga {} - marking as completed", saga.getSagaId());
                saga.setStatus(SagaStatus.COMPLETED);
                saga.setCompletedAt(Instant.now());
                stateRepository.save(saga);
            }
        });
    }

    private fun isActuallyCompleted(SagaState saga): boolean {
        String orderId = saga.getSagaId();

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.COMPLETED) {
            return false;
        }

        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.PROCESSED) {
            return false;
        }

        return true;
    }
}
```

### Prevention Checklist

- ✓ Use event sourcing for complete audit trail
- ✓ Implement state reconciliation job
- ✓ Add health checks for saga coordinator
- ✓ Monitor saga state transitions
- ✓ Persist compensation steps

---

## Pitfall 4: Orchestrator Single Point of Failure

### Problem

Orchestration-based saga fails when orchestrator is down, blocking all sagas.

### Solution

Implement clustering and failover:

```kotlin
@Configuration
class SagaOrchestratorClusterConfig {

    @Bean
    fun sagaStateRepository(): SagaStateRepository {
        // Use shared database for cluster-wide state
        return DatabaseSagaStateRepository();
    }

    @Bean
    @Primary
    public CommandGateway clusterAwareCommandGateway(
            CommandBus commandBus) {

        return ClusterAwareCommandGateway(commandBus);
    }
}

@Component
class OrchestratorHealthCheck extends AbstractHealthIndicator {

    private val repository: SagaStateRepository

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        long stuckSagas = repository.countStuckSagas(Duration.ofMinutes(30));

        if (stuckSagas > 100) {
            builder.down()
                .withDetail("stuckSagas", stuckSagas)
                .withDetail("severity", "critical");
        } else if (stuckSagas > 10) {
            builder.degraded()
                .withDetail("stuckSagas", stuckSagas)
                .withDetail("severity", "warning");
        } else {
            builder.up()
                .withDetail("stuckSagas", stuckSagas);
        }
    }
}
```

### Prevention Checklist

- ✓ Deploy orchestrator in cluster with shared state
- ✓ Use distributed coordination (ZooKeeper, Consul)
- ✓ Implement heartbeat monitoring
- ✓ Set up automatic failover
- ✓ Use circuit breakers for service calls

---

## Pitfall 5: Non-Idempotent Compensations

### Problem

Compensation logic fails on retry because it's not idempotent, leaving system in inconsistent state.

### Solution

Design all compensations to be idempotent:

```kotlin
// Bad - Not idempotent
@Service
class BadPaymentService {
    fun refundPayment(String paymentId): void {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);

        // If this fails partway, retry causes problems
        externalPaymentGateway.refund(payment.getTransactionId());
    }
}

// Good - Idempotent
@Service
class GoodPaymentService {
    fun refundPayment(String paymentId): void {
        Payment payment = paymentRepository.findById(paymentId)
            .orElse(null);

        if (payment == null) {
            // Already deleted or doesn't exist
            logger.info("Payment {} not found, skipping refund", paymentId);
            return;
        }

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            // Already refunded
            logger.info("Payment {} already refunded", paymentId);
            return;
        }

        try {
            externalPaymentGateway.refund(payment.getTransactionId());
            payment.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
        } catch (Exception e) {
            logger.error("Refund failed, will retry", e);
            throw e;
        }
    }
}
```

### Prevention Checklist

- ✓ Check current state before making changes
- ✓ Use status flags to track compensation completion
- ✓ Make database updates idempotent
- ✓ Test compensation with replays
- ✓ Document compensation logic

---

## Pitfall 6: Missing Timeouts

### Problem

Sagas hang indefinitely waiting for events that never arrive due to service failures.

### Solution

Implement timeout mechanisms:

```kotlin
@Configuration
class SagaTimeoutConfig {

    @Bean
    fun sagaLifecycle(SagaStateRepository repository): SagaLifecycle {
        return SagaLifecycle() {
            @Override
            fun onSagaFinished(Saga saga): void {
                // Update saga state
            }
        };
    }
}

@Saga
class OrderSaga {

    @Autowired
    private transient CommandGateway commandGateway;

    private var orderId: String
    private var paymentId: String
    private var deadlineManager: DeadlineManager

    @StartSaga
    @SagaEventHandler(associationProperty = "orderId")
    fun handle(OrderCreatedEvent event): void {
        this.orderId = event.orderId();

        // Schedule timeout for payment processing
        deadlineManager.scheduleDeadline(
            Duration.ofSeconds(30),
            "PaymentTimeout",
            orderId
        );

        commandGateway.send(ProcessPaymentCommand(...));
    }

    @DeadlineHandler(deadlineName = "PaymentTimeout")
    fun handlePaymentTimeout(): void {
        logger.warn("Payment processing timed out for order {}", orderId);

        // Compensate
        commandGateway.send(CancelOrderCommand(orderId));
        end();
    }

    @SagaEventHandler(associationProperty = "orderId")
    fun handle(PaymentProcessedEvent event): void {
        // Cancel timeout
        deadlineManager.cancelDeadline("PaymentTimeout", orderId);
        // Continue saga...
    }
}
```

### Prevention Checklist

- ✓ Set timeout for each saga step
- ✓ Use deadline manager to track timeouts
- ✓ Cancel timeouts when step completes
- ✓ Log timeout events
- ✓ Alert operations on repeated timeouts

---

## Pitfall 7: Tight Coupling Between Services

### Problem

Saga logic couples services tightly, making independent deployment impossible.

### Solution

Use event-driven communication:

```kotlin
// Bad - Tight coupling
@Service
class TightlyAgedOrderService {
    fun createOrder(OrderRequest request): void {
        Order order = orderRepository.save(Order(...));

        // Direct coupling to payment service
        paymentService.processPayment(order.getId(), request.getAmount());
    }
}

// Good - Event-driven
@Service
class LooselyAgedOrderService {
    fun createOrder(OrderRequest request): void {
        Order order = orderRepository.save(Order(...));

        // Publish event - services listen independently
        eventPublisher.publish(new OrderCreatedEvent(
            order.getId(),
            request.getAmount()
        ));
    }
}

@Component
class PaymentServiceListener {

    @Bean
    public Consumer<OrderCreatedEvent> handleOrderCreated() {
        return event -> {
            // Payment service can be deployed independently
            paymentService.processPayment(
                event.orderId(),
                event.amount()
            );
        };
    }
}
```

### Prevention Checklist

- ✓ Use events for inter-service communication
- ✓ Avoid direct service-to-service calls
- ✓ Define clear contracts for events
- ✓ Version events for backward compatibility
- ✓ Deploy services independently

---

## Pitfall 8: Inadequate Monitoring

### Problem

Sagas fail silently or get stuck without visibility, making troubleshooting impossible.

### Solution

Implement comprehensive monitoring:

```kotlin
@Component
class SagaMonitoring {

    private val meterRegistry: MeterRegistry

    @Bean
    fun sagaMetrics(SagaStateRepository repository): MeterBinder {
        return (registry) -> {
            Gauge.builder("saga.active", repository::countByStatus)
                .description("Number of active sagas")
                .register(registry);

            Gauge.builder("saga.stuck", () ->
                repository.countStuckSagas(Duration.ofMinutes(30)))
                .description("Number of stuck sagas")
                .register(registry);
        };
    }

    fun recordSagaStart(String sagaType): void {
        Counter.builder("saga.started")
            .tag("type", sagaType)
            .register(meterRegistry)
            .increment();
    }

    fun recordSagaCompletion(String sagaType, long durationMs): void {
        Timer.builder("saga.duration")
            .tag("type", sagaType)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry)
            .record(Duration.ofMillis(durationMs));
    }

    fun recordSagaFailure(String sagaType, String reason): void {
        Counter.builder("saga.failed")
            .tag("type", sagaType)
            .tag("reason", reason)
            .register(meterRegistry)
            .increment();
    }
}
```

### Prevention Checklist

- ✓ Track saga state transitions
- ✓ Monitor step execution times
- ✓ Alert on stuck sagas
- ✓ Log all failures with details
- ✓ Use distributed tracing (Sleuth, Zipkin)
- ✓ Create dashboards for visibility
