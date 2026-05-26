# Spring Boot SAGA Pattern - Reference Documentation

## Table of Contents

1. [Saga Pattern Overview](#saga-pattern-overview)
2. [Choreography-Based Saga](#choreography-based-saga)
3. [Orchestration-Based Saga](#orchestration-based-saga)
4. [Spring Boot Integration](#spring-boot-integration)
5. [Saga Frameworks](#saga-frameworks)
6. [Event-Driven Architecture](#event-driven-architecture)
7. [Compensating Transactions](#compensating-transactions)
8. [State Management](#state-management)
9. [Error Handling and Retry](#error-handling-and-retry)
10. [Testing Strategies](#testing-strategies)

---

## Saga Pattern Overview

### Definition

A **Saga** is a sequence of local transactions where each transaction updates data within a single
service. Each local transaction publishes an event or message that triggers the next local
transaction in the saga. If a local transaction fails, the saga executes compensating transactions
to undo the changes made by preceding transactions.

### Key Characteristics

**Distributed Transactions**: Spans multiple microservices, each with its own database.

**Local Transactions**: Each service performs its own ACID transaction.

**Event-Driven**: Services communicate through events or commands.

**Compensations**: Rollback mechanism using compensating transactions.

**Eventual Consistency**: System reaches a consistent state over time.

### Saga vs Two-Phase Commit (2PC)

| Feature           | Saga Pattern              | Two-Phase Commit             |
|-------------------|---------------------------|------------------------------|
| Locking           | No distributed locks      | Requires locks during commit |
| Performance       | Better performance        | Performance bottleneck       |
| Scalability       | Highly scalable           | Limited scalability          |
| Complexity        | Business logic complexity | Protocol complexity          |
| Failure Handling  | Compensating transactions | Automatic rollback           |
| Isolation         | Lower isolation           | Full isolation               |
| NoSQL Support     | Yes                       | No                           |
| Microservices Fit | Excellent                 | Poor                         |

### ACID vs BASE

**ACID** (Traditional Databases):

- **A**tomicity: All or nothing
- **C**onsistency: Valid state transitions
- **I**solation: Concurrent transactions don't interfere
- **D**urability: Committed data persists

**BASE** (Saga Pattern):

- **B**asically **A**vailable: System is available most of the time
- **S**oft state: State may change over time
- **E**ventual consistency: System becomes consistent eventually

---

## Choreography-Based Saga

### Architecture

Each service produces and listens to events. Services know what to do when they receive an event.

```
Service A → Event → Service B → Event → Service C
    ↓                   ↓                   ↓
  Event              Event               Event
    ↓                   ↓                   ↓
Compensation    Compensation        Compensation
```

### Event Flow

**Success Flow**:

1. Order Service creates order → publishes `OrderCreated` event
2. Payment Service listens → processes payment → publishes `PaymentProcessed` event
3. Inventory Service listens → reserves inventory → publishes `InventoryReserved` event
4. Shipment Service listens → prepares shipment → publishes `ShipmentPrepared` event

**Failure Flow** (Payment fails):

1. Payment Service publishes `PaymentFailed` event
2. Order Service listens → cancels order → publishes `OrderCancelled` event

### Implementation Components

#### Event Publisher

```kotlin
@Component
class OrderEventPublisher {
    private val streamBridge: StreamBridge
    
    public OrderEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }
    
    fun publishOrderCreatedEvent(String orderId, BigDecimal amount, String itemId): void {
        OrderCreatedEvent event = OrderCreatedEvent(orderId, amount, itemId);
        streamBridge.send("orderCreated-out-0", 
            MessageBuilder
                .withPayload(event)
                .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
                .build());
    }
}
```

#### Event Listener

```kotlin
@Component
class PaymentEventListener {
    
    @Bean
    public Consumer<OrderCreatedEvent> handleOrderCreatedEvent() {
        return event -> processPayment(event.getOrderId());
    }
    
    private fun processPayment(String orderId): void {
        // Payment processing logic
    }
}
```

#### Event Classes

```kotlin
public record OrderCreatedEvent(
    String orderId,
    BigDecimal amount,
    String itemId
) {}

public record PaymentProcessedEvent(
    String paymentId,
    String orderId,
    String itemId
) {}

public record PaymentFailedEvent(
    String paymentId,
    String orderId,
    String itemId,
    String reason
) {}
```

### Spring Cloud Stream Configuration

```yaml
spring:
  cloud:
    stream:
      bindings:
        orderCreated-out-0:
          destination: order-events
        paymentProcessed-out-0:
          destination: payment-events
        paymentFailed-out-0:
          destination: payment-events
      kafka:
        binder:
          brokers: localhost:9092
```

### Maven Dependencies

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-stream</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-stream-binder-kafka</artifactId>
</dependency>
```

### Gradle Dependencies

```groovy
implementation 'org.springframework.cloud:spring-cloud-stream'
implementation 'org.springframework.cloud:spring-cloud-stream-binder-kafka'
```

---

## Orchestration-Based Saga

### Architecture

A central **Saga Orchestrator** coordinates the entire transaction flow, sending commands to
services and handling responses.

```
         Saga Orchestrator
         /     |      \
    Service A  Service B  Service C
```

### Orchestrator Responsibilities

1. **Command Dispatch**: Sends commands to services
2. **Response Handling**: Processes service responses
3. **State Management**: Tracks saga execution state
4. **Compensation Coordination**: Triggers compensating transactions on failure
5. **Timeout Management**: Handles service timeouts
6. **Retry Logic**: Manages retry attempts

### Axon Framework Implementation

#### Saga Class

```kotlin
@Saga
class OrderSaga {
    
    @Autowired
    private transient CommandGateway commandGateway;
    
    @StartSaga
    @SagaEventHandler(associationProperty = "orderId")
    fun handle(OrderCreatedEvent event): void {
        String paymentId = UUID.randomUUID().toString();
        ProcessPaymentCommand command = new ProcessPaymentCommand(
            paymentId, 
            event.getOrderId(), 
            event.getAmount(), 
            event.getItemId()
        );
        commandGateway.send(command);
    }
    
    @SagaEventHandler(associationProperty = "orderId")
    fun handle(PaymentProcessedEvent event): void {
        ReserveInventoryCommand command = new ReserveInventoryCommand(
            event.getOrderId(), 
            event.getItemId()
        );
        commandGateway.send(command);
    }
    
    @SagaEventHandler(associationProperty = "orderId")
    fun handle(PaymentFailedEvent event): void {
        CancelOrderCommand command = CancelOrderCommand(event.getOrderId());
        commandGateway.send(command);
        end();
    }
    
    @SagaEventHandler(associationProperty = "orderId")
    fun handle(InventoryReservedEvent event): void {
        PrepareShipmentCommand command = new PrepareShipmentCommand(
            event.getOrderId(), 
            event.getItemId()
        );
        commandGateway.send(command);
    }
    
    @EndSaga
    @SagaEventHandler(associationProperty = "orderId")
    fun handle(OrderCompletedEvent event): void {
        // Saga completed successfully
    }
}
```

#### Aggregate for Order Service

```kotlin
@Aggregate
class OrderAggregate {
    
    @AggregateIdentifier
    private var orderId: String
    
    private var status: OrderStatus
    
    public OrderAggregate() {
    }
    
    @CommandHandler
    public OrderAggregate(CreateOrderCommand command) {
        apply(new OrderCreatedEvent(
            command.getOrderId(), 
            command.getAmount(), 
            command.getItemId()
        ));
    }
    
    @EventSourcingHandler
    fun on(OrderCreatedEvent event): void {
        this.orderId = event.getOrderId();
        this.status = OrderStatus.PENDING;
    }
    
    @CommandHandler
    fun handle(CancelOrderCommand command): void {
        apply(OrderCancelledEvent(command.getOrderId()));
    }
    
    @EventSourcingHandler
    fun on(OrderCancelledEvent event): void {
        this.status = OrderStatus.CANCELLED;
    }
}
```

#### Aggregate for Payment Service

```kotlin
@Aggregate
class PaymentAggregate {
    
    @AggregateIdentifier
    private var paymentId: String
    
    public PaymentAggregate() {
    }
    
    @CommandHandler
    public PaymentAggregate(ProcessPaymentCommand command) {
        this.paymentId = command.getPaymentId();
        
        if (command.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            apply(new PaymentFailedEvent(
                command.getPaymentId(),
                command.getOrderId(),
                command.getItemId(),
                "Payment amount must be greater than zero"
            ));
        } else {
            apply(new PaymentProcessedEvent(
                command.getPaymentId(),
                command.getOrderId(),
                command.getItemId()
            ));
        }
    }
}
```

### Axon Configuration

```yaml
axon:
  serializer:
    general: jackson
    events: jackson
    messages: jackson
  eventhandling:
    processors:
      order-processor:
        mode: tracking
        source: eventBus
  axonserver:
    enabled: false
```

### Maven Dependencies for Axon

```xml
<dependency>
    <groupId>org.axonframework</groupId>
    <artifactId>axon-spring-boot-starter</artifactId>
    <version>4.9.0</version>
</dependency>
```

---

## Spring Boot Integration

### Application Configuration

```kotlin
@SpringBootApplication
@EnableScheduling
class SagaApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(SagaApplication.class, args);
    }
}
```

### Kafka Configuration

```kotlin
@Configuration
class KafkaConfig {
    
    @Bean
    fun orderTopic(): NewTopic {
        return NewTopic("order-events", 3, (short) 1);
    }
    
    @Bean
    fun paymentTopic(): NewTopic {
        return NewTopic("payment-events", 3, (short) 1);
    }
    
    @Bean
    fun inventoryTopic(): NewTopic {
        return NewTopic("inventory-events", 3, (short) 1);
    }
}
```

### Properties Configuration

```properties
# Application
spring.application.name=saga-service

# Kafka
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=saga-group
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.properties.spring.json.trusted.packages=*
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/sagadb
spring.datasource.username=saga
spring.datasource.password=saga
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# Actuator
management.endpoints.web.exposure.include=health,metrics,prometheus
management.endpoint.health.show-details=always
```

---

## Saga Frameworks

### Axon Framework

**Type**: Orchestration-based

**Features**:

- Event sourcing support
- CQRS pattern implementation
- Saga state management
- Automatic compensation
- Built-in retry mechanisms

**Use When**:

- Complex domain logic
- Event sourcing is beneficial
- CQRS pattern is needed
- Mature framework is required

### Eventuate Tram Sagas

**Type**: Orchestration-based

**Features**:

- Database-per-service support
- Transactional messaging
- Saga orchestration DSL
- Multiple messaging platforms

**Use When**:

- Existing JPA-based services
- Transactional outbox pattern needed
- Multiple message brokers support required

### Camunda

**Type**: BPMN-based orchestration

**Features**:

- Visual workflow design
- BPMN 2.0 standard
- Human tasks support
- Complex workflow modeling

**Use When**:

- Business process modeling needed
- Visual workflow design preferred
- Human approval steps required
- Complex orchestration logic

### Apache Camel Saga EIP

**Type**: Enterprise Integration Pattern

**Features**:

- Saga EIP implementation
- Multiple protocol support
- Route-based compensation
- Integration with multiple systems

**Use When**:

- Enterprise integration scenarios
- Multiple protocol support needed
- Existing Camel infrastructure

---

## Event-Driven Architecture

### Event Types

**Domain Events**: Represent business facts that happened

```kotlin
public record OrderCreatedEvent(
    String orderId,
    Instant createdAt,
    BigDecimal amount
) implements DomainEvent {}
```

**Integration Events**: Communication between bounded contexts

```kotlin
public record PaymentRequestedEvent(
    String orderId,
    String paymentId,
    BigDecimal amount
) implements IntegrationEvent {}
```

**Command Events**: Request for action

```kotlin
public record ProcessPaymentCommand(
    String paymentId,
    String orderId,
    BigDecimal amount
) {}
```

### Event Versioning

```kotlin
public record OrderCreatedEventV1(
    String orderId,
    BigDecimal amount
) {}

public record OrderCreatedEventV2(
    String orderId,
    BigDecimal amount,
    String customerId,
    Instant timestamp
) {}

// Event Upcaster
class OrderEventUpcaster implements EventUpcaster {
    @Override
    public Stream<IntermediateEventRepresentation> upcast(
        Stream<IntermediateEventRepresentation> eventStream) {
        
        return eventStream.map(event -> {
            if (event.getType().getName().equals("OrderCreatedEventV1")) {
                return upcastV1ToV2(event);
            }
            return event;
        });
    }
}
```

### Event Store

```kotlin
@Entity
@Table(name = "saga_events")
class SagaEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private var id: Long
    
    @Column(nullable = false)
    private var sagaId: String
    
    @Column(nullable = false)
    private var eventType: String
    
    @Column(columnDefinition = "TEXT")
    private var payload: String
    
    @Column(nullable = false)
    private var timestamp: Instant
    
    @Column(nullable = false)
    private var version: Integer
}
```

---

## Compensating Transactions

### Design Principles

**Idempotency**: Execute multiple times with same result

```kotlin
fun cancelPayment(String paymentId): void {
    Payment payment = paymentRepository.findById(paymentId)
        .orElse(null);
    
    if (payment == null) {
        // Already cancelled or doesn't exist
        return;
    }
    
    if (payment.getStatus() == PaymentStatus.CANCELLED) {
        // Already cancelled, idempotent
        return;
    }
    
    payment.setStatus(PaymentStatus.CANCELLED);
    paymentRepository.save(payment);
    
    // Refund logic here
}
```

**Retryability**: Safe to retry on failure

```kotlin
@Retryable(
    value = {TransientException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2)
)
fun releaseInventory(String itemId, int quantity): void {
    // Implementation
}
```

### Compensation Strategies

**Backward Recovery**: Undo completed steps

```kotlin
@SagaEventHandler(associationProperty = "orderId")
fun handle(PaymentFailedEvent event): void {
    // Step 1: Cancel shipment preparation
    commandGateway.send(CancelShipmentCommand(event.getOrderId()));
    
    // Step 2: Release inventory
    commandGateway.send(ReleaseInventoryCommand(event.getOrderId()));
    
    // Step 3: Cancel order
    commandGateway.send(CancelOrderCommand(event.getOrderId()));
    
    end();
}
```

**Forward Recovery**: Retry failed operation

```kotlin
@SagaEventHandler(associationProperty = "orderId")
fun handle(PaymentTransientFailureEvent event): void {
    if (event.getRetryCount() < MAX_RETRIES) {
        // Retry payment
        ProcessPaymentCommand retryCommand = new ProcessPaymentCommand(
            event.getPaymentId(),
            event.getOrderId(),
            event.getAmount()
        );
        commandGateway.send(retryCommand);
    } else {
        // Compensate
        handlePaymentFailure(event);
    }
}
```

### Semantic Lock Pattern

Prevent concurrent modifications during saga execution:

```kotlin
@Entity
class Order {
    @Id
    private var orderId: String
    
    @Enumerated(EnumType.STRING)
    private var status: OrderStatus
    
    @Version
    private var version: Long
    
    private var lockedUntil: Instant
    
    fun tryLock(Duration lockDuration): boolean {
        if (isLocked()) {
            return false;
        }
        this.lockedUntil = Instant.now().plus(lockDuration);
        return true;
    }
    
    fun isLocked(): boolean {
        return lockedUntil != null && 
               Instant.now().isBefore(lockedUntil);
    }
    
    fun unlock(): void {
        this.lockedUntil = null;
    }
}
```

---

## State Management

### Saga State

```kotlin
@Entity
@Table(name = "saga_state")
class SagaState {
    
    @Id
    private var sagaId: String
    
    @Enumerated(EnumType.STRING)
    private var status: SagaStatus
    
    @Column(columnDefinition = "TEXT")
    private var currentStep: String
    
    @Column(columnDefinition = "TEXT")
    private var compensationSteps: String
    
    private var startedAt: Instant
    private var completedAt: Instant
    
    @Version
    private var version: Long
}

enum class SagaStatus {
    STARTED,
    PROCESSING,
    COMPENSATING,
    COMPLETED,
    FAILED,
    CANCELLED
}
```

### State Machine with Spring Statemachine

```kotlin
@Configuration
@EnableStateMachine
class SagaStateMachineConfig 
    extends StateMachineConfigurerAdapter<SagaStatus, SagaEvent> {
    
    @Override
    public void configure(
        StateMachineStateConfigurer<SagaStatus, SagaEvent> states) 
        throws Exception {
        
        states
            .withStates()
            .initial(SagaStatus.STARTED)
            .states(EnumSet.allOf(SagaStatus.class))
            .end(SagaStatus.COMPLETED)
            .end(SagaStatus.FAILED);
    }
    
    @Override
    public void configure(
        StateMachineTransitionConfigurer<SagaStatus, SagaEvent> transitions) 
        throws Exception {
        
        transitions
            .withExternal()
                .source(SagaStatus.STARTED)
                .target(SagaStatus.PROCESSING)
                .event(SagaEvent.ORDER_CREATED)
            .and()
            .withExternal()
                .source(SagaStatus.PROCESSING)
                .target(SagaStatus.COMPLETED)
                .event(SagaEvent.ALL_STEPS_COMPLETED)
            .and()
            .withExternal()
                .source(SagaStatus.PROCESSING)
                .target(SagaStatus.COMPENSATING)
                .event(SagaEvent.STEP_FAILED)
            .and()
            .withExternal()
                .source(SagaStatus.COMPENSATING)
                .target(SagaStatus.FAILED)
                .event(SagaEvent.COMPENSATION_COMPLETED);
    }
}
```

---

## Error Handling and Retry

### Retry Configuration

```kotlin
@Configuration
@EnableRetry
class RetryConfig {
    
    @Bean
    fun retryTemplate(): RetryTemplate {
        RetryTemplate retryTemplate = RetryTemplate();
        
        FixedBackOffPolicy backOffPolicy = FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(2000L);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        SimpleRetryPolicy retryPolicy = SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        retryTemplate.setRetryPolicy(retryPolicy);
        
        return retryTemplate;
    }
}
```

### Circuit Breaker with Resilience4j

```kotlin
@Configuration
class CircuitBreakerConfig {
    
    @Bean
    fun circuitBreakerRegistry(): CircuitBreakerRegistry {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofMillis(1000))
            .slidingWindowSize(2)
            .build();
        
        return CircuitBreakerRegistry.of(config);
    }
}

@Service
class PaymentService {
    
    private val circuitBreaker: CircuitBreaker
    
    public PaymentService(CircuitBreakerRegistry registry) {
        this.circuitBreaker = registry.circuitBreaker("payment");
    }
    
    fun processPayment(PaymentRequest request): PaymentResult {
        return circuitBreaker.executeSupplier(
            () -> callPaymentGateway(request)
        );
    }
}
```

### Dead Letter Queue

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
    public void handle(Exception thrownException, 
                      List<ConsumerRecord<?, ?>> records,
                      Consumer<?, ?> consumer, 
                      MessageListenerContainer container) {
        
        records.forEach(record -> {
            kafkaTemplate.send("saga-dlq", record.key(), record.value());
        });
    }
}
```

---

## Testing Strategies

### Unit Testing Saga

```kotlin
@Test
void shouldCompensateWhenPaymentFails() {
    // Given
    OrderSaga saga = OrderSaga();
    FixtureConfiguration<OrderSaga> fixture = new SagaTestFixture<>(OrderSaga.class);
    
    String orderId = UUID.randomUUID().toString();
    String paymentId = UUID.randomUUID().toString();
    
    // When
    fixture
        .givenNoPriorActivity()
        .whenPublishingA(OrderCreatedEvent(orderId, BigDecimal.TEN, "item-1"))
        .expectDispatchedCommands(ProcessPaymentCommand(paymentId, orderId, BigDecimal.TEN));
    
    // Then - payment fails
    fixture
        .whenPublishingA(PaymentFailedEvent(paymentId, orderId, "item-1", "Insufficient funds"))
        .expectDispatchedCommands(CancelOrderCommand(orderId));
}
```

### Integration Testing with Testcontainers

```kotlin
@SpringBootTest
@Testcontainers
class SagaIntegrationTest {
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.4.0")
    );
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        "postgres:15-alpine"
    );
    
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Test
    void shouldCompleteOrderSagaSuccessfully() {
        // Test implementation
    }
}
```

### Testing Idempotency

```kotlin
@Test
void compensationShouldBeIdempotent() {
    String paymentId = "payment-123";
    
    // Execute compensation first time
    paymentService.cancelPayment(paymentId);
    Payment firstResult = paymentRepository.findById(paymentId).orElseThrow();
    
    // Execute compensation second time
    paymentService.cancelPayment(paymentId);
    Payment secondResult = paymentRepository.findById(paymentId).orElseThrow();
    
    // Should produce same result
    assertThat(firstResult).isEqualTo(secondResult);
    assertThat(secondResult.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
}
```

---

## Monitoring and Observability

### Micrometer Metrics

```kotlin
@Component
class SagaMetrics {
    
    private val sagaStarted: Counter
    private val sagaCompleted: Counter
    private val sagaFailed: Counter
    private val sagaDuration: Timer
    
    public SagaMetrics(MeterRegistry registry) {
        this.sagaStarted = Counter.builder("saga.started")
            .description("Number of sagas started")
            .register(registry);
            
        this.sagaCompleted = Counter.builder("saga.completed")
            .description("Number of sagas completed successfully")
            .register(registry);
            
        this.sagaFailed = Counter.builder("saga.failed")
            .description("Number of sagas failed")
            .register(registry);
            
        this.sagaDuration = Timer.builder("saga.duration")
            .description("Saga execution duration")
            .register(registry);
    }
    
    fun recordSagaStart(): void {
        sagaStarted.increment();
    }
    
    fun recordSagaCompletion(Duration duration): void {
        sagaCompleted.increment();
        sagaDuration.record(duration);
    }
    
    fun recordSagaFailure(): void {
        sagaFailed.increment();
    }
}
```

### Distributed Tracing

```kotlin
@Configuration
class TracingConfig {
    
    @Bean
    fun tracer(): Tracer {
        return new Tracer.Builder()
            .spanReporter(ZipkinSpanReporter())
            .build();
    }
}

@Service
class OrderService {
    
    @Autowired
    private var tracer: Tracer
    
    fun createOrder(OrderRequest request): void {
        Span span = tracer.newTrace().name("create-order").start();
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
            // Order creation logic
            span.tag("orderId", request.getOrderId());
        } finally {
            span.finish();
        }
    }
}
```

### Health Checks

```kotlin
@Component
class SagaHealthIndicator implements HealthIndicator {
    
    private val sagaStateRepository: SagaStateRepository
    
    @Override
    fun health(): Health {
        long stuckSagas = sagaStateRepository.countStuckSagas(
            Duration.ofMinutes(30)
        );
        
        if (stuckSagas > 10) {
            return Health.down()
                .withDetail("stuckSagas", stuckSagas)
                .build();
        }
        
        return Health.up()
            .withDetail("stuckSagas", stuckSagas)
            .build();
    }
}
```

---

## Performance Considerations

### Batch Processing

```kotlin
@Service
class BatchSagaProcessor {
    
    @Scheduled(fixedDelay = 5000)
    fun processPendingSagas(): void {
        List<SagaState> pendingSagas = sagaStateRepository
            .findByStatus(SagaStatus.PROCESSING, PageRequest.of(0, 100));
        
        pendingSagas.forEach(this::processSaga);
    }
}
```

### Parallel Execution

```kotlin
@SagaEventHandler(associationProperty = "orderId")
fun handle(PaymentProcessedEvent event): void {
    // Execute inventory and notification in parallel
    CompletableFuture.allOf(
        CompletableFuture.runAsync(() -> 
            commandGateway.send(ReserveInventoryCommand(event.getOrderId()))
        ),
        CompletableFuture.runAsync(() -> 
            commandGateway.send(SendNotificationCommand(event.getOrderId()))
        )
    ).join();
}
```

### Database Optimization

```sql
-- Index for saga state queries
CREATE INDEX idx_saga_state_status ON saga_state(status);
CREATE INDEX idx_saga_state_started_at ON saga_state(started_at);

-- Index for event store queries
CREATE INDEX idx_saga_events_saga_id ON saga_events(saga_id);
CREATE INDEX idx_saga_events_timestamp ON saga_events(timestamp);
```

---

## Security Best Practices

### Message Authentication

```kotlin
@Configuration
class MessageSecurityConfig {
    
    @Bean
    fun messageSigningInterceptor(): MessageSigningInterceptor {
        return MessageSigningInterceptor(secretKey);
    }
}

class MessageSigningInterceptor implements ProducerInterceptor<String, Object> {
    
    @Override
    public ProducerRecord<String, Object> onSend(ProducerRecord<String, Object> record) {
        String signature = computeSignature(record.value());
        Headers headers = record.headers();
        headers.add("signature", signature.getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
```

### Audit Logging

```kotlin
@Aspect
@Component
class SagaAuditAspect {
    
    @Around("@annotation(SagaOperation)")
    public Object auditSagaOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        String sagaId = extractSagaId(joinPoint);
        String operation = joinPoint.getSignature().getName();
        
        auditLog.info("Saga operation started: sagaId={}, operation={}", 
            sagaId, operation);
        
        try {
            Object result = joinPoint.proceed();
            auditLog.info("Saga operation completed: sagaId={}, operation={}", 
                sagaId, operation);
            return result;
        } catch (Exception e) {
            auditLog.error("Saga operation failed: sagaId={}, operation={}, error={}", 
                sagaId, operation, e.getMessage());
            throw e;
        }
    }
}
```

---

## Common Pitfalls and Solutions

### Pitfall 1: Lost Messages

**Problem**: Messages get lost due to broker failures.

**Solution**: Use persistent messages and acknowledgments.

```kotlin
@Bean
public ProducerFactory<String, Object> producerFactory() {
    Map<String, Object> config = mutableMapOf();
    config.put(ProducerConfig.ACKS_CONFIG, "all");
    config.put(ProducerConfig.RETRIES_CONFIG, 3);
    config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    return new DefaultKafkaProducerFactory<>(config);
}
```

### Pitfall 2: Duplicate Processing

**Problem**: Same message processed multiple times.

**Solution**: Implement idempotency with deduplication.

```kotlin
@Service
class DeduplicationService {
    
    private final Set<String> processedMessageIds = ConcurrentHashMap.newKeySet();
    
    fun isDuplicate(String messageId): boolean {
        return !processedMessageIds.add(messageId);
    }
}
```

### Pitfall 3: Saga State Inconsistency

**Problem**: Saga state doesn't match actual service states.

**Solution**: Use event sourcing or state reconciliation.

```kotlin
@Scheduled(fixedDelay = 60000)
fun reconcileSagaStates(): void {
    List<SagaState> processingSagas = 
        sagaStateRepository.findByStatus(SagaStatus.PROCESSING);
    
    processingSagas.forEach(saga -> {
        if (isActuallyCompleted(saga)) {
            saga.setStatus(SagaStatus.COMPLETED);
            sagaStateRepository.save(saga);
        }
    });
}
```

---

## Additional Resources

- [Microservices.io - Saga Pattern](https://microservices.io/patterns/data/saga.html)
- [Axon Framework Documentation](https://docs.axoniq.io/reference-guide/)
- [Spring Cloud Stream Reference](https://spring.io/projects/spring-cloud-stream)
- [Eventuate Tram Documentation](https://eventuate.io/docs/manual/eventuate-tram/latest/)
- [Camunda Platform](https://docs.camunda.org/)
- [Apache Camel Saga EIP](https://camel.apache.org/components/latest/eips/saga-eip.html)
