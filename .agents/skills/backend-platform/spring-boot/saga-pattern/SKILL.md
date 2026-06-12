---
name: spring-boot-saga-pattern
description: Use when implementing saga-based distributed consistency in Spring Boot 4, coordinating compensating transactions, or designing choreography and orchestration flows across services.
allowed-tools: Read, Write, Edit, Bash, Glob, Grep
---

# Spring Boot Saga Pattern

## Overview

Implements distributed transactions across microservices using the Saga Pattern. Replaces two-phase
commit with a sequence of local transactions and compensating actions. Supports choreography (
event-driven) and orchestration (centralized coordinator) approaches with Kafka, RabbitMQ, or Axon
Framework.

## When to Use

- Building distributed transactions across multiple microservices
- Replacing two-phase commit (2PC) with a more scalable solution
- Handling transaction rollback when a service fails
- Ensuring eventual consistency in microservices architecture
- Implementing compensating transactions for failed operations
- Coordinating complex business processes spanning multiple services

**Trigger phrases**: distributed transactions, saga pattern, compensating transactions,
microservices transaction, eventual consistency, rollback across services, orchestration pattern,
choreography pattern

## Instructions

### 1. Design Transaction Flow

Map the sequence of operations and their compensating transactions:

```
Order → Payment → Inventory → Shipment
  ↓        ↓        ↓          ↓
Cancel  Refund   Release    Cancel
```

**Validation**: Verify every forward step has a corresponding compensation.

### 2. Choose Implementation Approach

| Approach      | Use Case                      | Stack                                   |
|---------------|-------------------------------|-----------------------------------------|
| Choreography  | Greenfield, few participants  | Spring Cloud Stream + Kafka/RabbitMQ    |
| Orchestration | Complex workflows, brownfield | Axon Framework, Eventuate Tram, Camunda |

**Validation**: Review team expertise and system complexity before choosing.

### 3. Implement Services with Local Transactions

Each service completes its local ACID transaction atomically:

```kotlin
@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val kafka: KafkaTemplate<String, Any>,
) {
    @Transactional
    fun createOrder(cmd: CreateOrderCommand): Order {
        val order = orderRepository.save(Order(cmd.orderId, cmd.items))
        kafka.send("order.created", OrderCreatedEvent(order.id, order.items))
        return order
    }
}
```

**Validation**: Test that local transaction commits before event is published.

### 4. Implement Compensating Transactions

Every forward operation requires an idempotent compensation:

```kotlin
@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val kafka: KafkaTemplate<String, Any>,
) {
    fun processPayment(request: PaymentRequest) {
        val payment = paymentRepository.save(Payment(request.orderId, request.amount))
        kafka.send("payment.processed", PaymentProcessedEvent(payment.id, request.orderId))
    }

    @Transactional
    fun refundPayment(paymentId: String) {
        paymentRepository.findById(paymentId).ifPresent { payment ->
            payment.status = PaymentStatus.REFUNDED
            paymentRepository.save(payment)
            kafka.send("payment.refunded", PaymentRefundedEvent(paymentId))
        }
    }
}
```

**Validation**: Confirm compensation can execute safely multiple times (idempotency).

### 5. Set Up Message Broker

Configure Kafka with idempotent consumers:

```kotlin
@Configuration
@EnableKafka
class KafkaConfig {
    @Bean
    fun kafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, Any>
    ): ConcurrentKafkaListenerContainerFactory<String, Any> =
        ConcurrentKafkaListenerContainerFactory<String, Any>().apply {
            setConsumerFactory(consumerFactory)
            setCommonErrorHandler(DefaultErrorHandler())
        }
}
```

**Validation**: Enable transactional ID and verify exactly-once semantics.

### 6. Implement Saga Orchestrator (Orchestration Only)

```kotlin
@Service
class OrderSagaOrchestrator(
    private val kafka: KafkaTemplate<String, Any>,
    private val sagaStateRepo: SagaStateRepository,
) {
    fun startSaga(request: OrderRequest) {
        val sagaId = UUID.randomUUID().toString()
        sagaStateRepo.save(SagaState(sagaId, SagaStatus.STARTED, LocalDateTime.now()))
        kafka.send("saga.order.start", StartOrderSagaCommand(sagaId, request))
    }

    @KafkaListener(topics = ["payment.failed"])
    fun handlePaymentFailed(event: PaymentFailedEvent) {
        kafka.send("order.compensate", CompensateOrderCommand(event.sagaId))
        kafka.send("inventory.compensate", ReleaseInventoryCommand(event.sagaId))
        sagaStateRepo.updateStatus(event.sagaId, SagaStatus.FAILED)
    }
}
```

**Validation**: Verify saga state persists before sending commands. Check compensation triggers on
each failure path.

### 7. Implement Event Handlers (Choreography Only)

```kotlin
@Service
class OrderEventHandler(
    private val orderService: OrderService,
    private val kafka: KafkaTemplate<String, Any>,
) {
    @KafkaListener(topics = ["payment.processed"], groupId = "order-service")
    fun onPaymentProcessed(event: PaymentProcessedEvent) {
        try {
            val result = orderService.reserveInventory(event.toInventoryRequest())
            kafka.send("inventory.reserved", result)
        } catch (e: InsufficientInventoryException) {
            kafka.send(
                "inventory.insufficient",
                InsufficientInventoryEvent(event.orderId, event.paymentId)
            )
        }
    }
}
```

**Validation**: Test that each event handler correctly triggers the next step or compensation.

### 8. Add Monitoring and Observability

```kotlin
@Configuration
class SagaMetricsConfig {
    @Bean
    fun meterRegistry(): MeterRegistry =
        PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
}
```

Track: saga execution duration, compensation count, failure rate, stuck sagas.

**Validation**: Set up alerts for sagas exceeding expected duration.

## Best Practices

**Design**:

- Make compensating transactions **idempotent** using database constraints or deduplication tables
- Use **immutable events** (Java records) to prevent accidental mutation
- Store saga state in persistent storage for recovery

**Error Handling**:

- Implement **circuit breakers** for inter-service calls
- Use **dead-letter queues** for messages exceeding retry limits
- Set appropriate **timeouts** per saga step (30s default, configurable)

**Monitoring**:

- Track saga status: PENDING, COMPLETED, COMPENSATING, FAILED
- Monitor compensation execution time
- Alert when sagas exceed SLA duration

## Constraints and Warnings

- Every forward transaction MUST have a corresponding compensating transaction
- Compensating transactions MUST be idempotent to handle retry scenarios
- Saga state MUST be persisted to handle failures and recovery
- Never use synchronous communication between saga participants
- Sagas provide eventual consistency, not strong consistency
- Test all failure scenarios including partial failures
- Consider Axon Framework or Eventuate for complex orchestrations
- Ensure message brokers are highly available

## Examples

### Choreography-Based Saga

```kotlin
// Application.kt
@SpringBootApplication
@EnableKafka
class OrderApplication

fun main(args: Array<String>) {
    runApplication<OrderApplication>(*args)
}

// Event Classes (immutable)
data class OrderCreatedEvent(val orderId: String, val items: List<OrderItem>)
data class PaymentProcessedEvent(val paymentId: String, val orderId: String)
data class InventoryReservedEvent(val reservationId: String, val orderId: String)
data class PaymentFailedEvent(val orderId: String, val reason: String)
data class InsufficientInventoryEvent(val orderId: String, val paymentId: String)

// OrderService with compensation
@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val kafka: KafkaTemplate<String, Any>,
) {
    @KafkaListener(topics = ["payment.failed"], groupId = "order-service")
    fun handleCompensation(event: PaymentFailedEvent) {
        orderRepository.findByOrderId(event.orderId)?.let { order ->
            order.status = OrderStatus.CANCELLED
            orderRepository.save(order)
        }
    }
}
```

### Orchestration-Based Saga with Axon Framework

```kotlin
// Command
@Aggregate
class OrderAggregate() {
    @AggregateIdentifier
    private lateinit var orderId: String

    @CommandHandler
    constructor(cmd: CreateOrderCommand) : this() {
        apply(OrderCreatedEvent(cmd.orderId, cmd.items))
    }

    @EventSourcingHandler
    fun on(event: OrderCreatedEvent) {
        this.orderId = event.orderId
    }

    @CommandHandler
    fun handle(cmd: CancelOrderCommand) {
        apply(OrderCancelledEvent(cmd.orderId, cmd.reason))
    }
}
```

## References

- [Saga Pattern Definition](references/saga-pattern-definition.md)
- [Choreography Implementation](references/choreography-implementation.md)
- [Orchestration Implementation](references/orchestration-implementation.md)
- [Compensating Transactions](references/compensating-transactions.md)
- [State Management](references/state-management.md)
- [Error Handling and Retry](references/error-handling-retry.md)
- [Testing Strategies](references/testing-strategies.md)
- [Pitfalls and Solutions](references/pitfalls-solutions.md)
- [Examples](references/examples.md)
