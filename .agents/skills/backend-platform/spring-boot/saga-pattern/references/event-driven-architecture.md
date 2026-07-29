# Event-Driven Architecture in Sagas

## Event Types

### Domain Events

Represent business facts that happened within a service:

```kotlin
public record OrderCreatedEvent(
    String orderId,
    Instant createdAt,
    BigDecimal amount
) implements DomainEvent {}
```

### Integration Events

Communication between bounded contexts (microservices):

```kotlin
public record PaymentRequestedEvent(
    String orderId,
    String paymentId,
    BigDecimal amount
) implements IntegrationEvent {}
```

### Command Events

Request for action by another service:

```kotlin
public record ProcessPaymentCommand(
    String paymentId,
    String orderId,
    BigDecimal amount
) {}
```

## Event Versioning

Handle event schema evolution using versioning:

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
    public Stream < IntermediateEventRepresentation > upcast (
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

## Event Store

Store all events for audit trail and recovery:

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

## Event Publishing Patterns

### Outbox Pattern (Transactional)

Ensure atomic update of database and event publishing:

```kotlin
@Service
class OrderService {

    private val orderRepository: OrderRepository
    private val outboxRepository: OutboxRepository

    @Transactional
    fun createOrder(CreateOrderRequest request): void {
        // 1. Create and save order
        Order order = Order (...);
        orderRepository.save(order);

        // 2. Create outbox entry in same transaction
        OutboxEntry entry = new OutboxEntry(
            "OrderCreated",
            order.getId(),
            OrderCreatedEvent(...
        )
        );
        outboxRepository.save(entry);
    }
}

@Component
class OutboxPoller {

    @Scheduled(fixedDelay = 1000)
    fun pollAndPublish(): void {
        List<OutboxEntry> unpublished = outboxRepository . findUnpublished ();

        unpublished.forEach(entry -> {
            eventPublisher.publish(entry.getEvent());
            outboxRepository.markAsPublished(entry.getId());
        });
    }
}
```

### Direct Publishing Pattern

Publish events immediately after transaction:

```kotlin
@Service
class OrderService {

    private val orderRepository: OrderRepository
    private val eventPublisher: EventPublisher

    @Transactional
    fun createOrder(CreateOrderRequest request): void {
        Order order = Order (...);
        orderRepository.save(order);

        // Publish event after transaction commits
        TransactionSynchronizationManager.registerSynchronization(
            TransactionSynchronization() {
                @Override
                fun afterCommit(): void {
                    eventPublisher.publish(OrderCreatedEvent(...));
                }
            }
        );
    }
}
```

## Event Sourcing

Store all state changes as events instead of current state:

**Benefits**:

- Complete audit trail
- Time-travel debugging
- Natural fit for sagas
- Event replay for recovery

**Implementation**:

```kotlin
@Entity
class Order {

    @Id
    private var orderId: String

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DomainEvent> events = mutableListOf();

    fun createOrder(...): void {
        apply(OrderCreatedEvent(...));
    }

    protected void apply(DomainEvent event)
    {
        if (event instanceof OrderCreatedEvent e) {
            this.orderId = e.orderId();
            this.status = OrderStatus.PENDING;
        }
        events.add(event);
    }

    public List<DomainEvent> getUncommittedEvents()
    {
        return new ArrayList < > (events);
    }

    fun clearUncommittedEvents(): void {
        events.clear();
    }
}
```

## Event Ordering and Consistency

### Maintain Event Order

Use partitioning to maintain order within a saga:

```kotlin
@Bean
public ProducerFactory < String, Object> producerFactory() {
    Map<String, Object> config = mutableMapOf ();
    config.put(
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        StringSerializer.class);
    return new DefaultKafkaProducerFactory < > (config);
}

@Service
class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    fun publish(DomainEvent event): void {
        // Use sagaId as key to maintain order
        kafkaTemplate.send("events", event.getSagaId(), event);
    }
}
```

### Handle Out-of-Order Events

Use saga state to detect and handle out-of-order events:

```kotlin
@SagaEventHandler(associationProperty = "orderId")
fun handle(PaymentProcessedEvent event): void {
    if (saga.getStatus() != SagaStatus.AWAITING_PAYMENT) {
        // Out of order event, ignore or queue for retry
        logger.warn("Unexpected event in state: {}", saga.getStatus());
        return;
    }
    // Process event
}
```
