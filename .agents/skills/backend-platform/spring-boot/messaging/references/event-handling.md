# Event Handling Patterns

## Local Event Handling

### Transactional Event Listener

```kotlin
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class ProductEventHandler {
    private val notificationService: NotificationService
    private val auditService: AuditService

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onProductCreated(ProductCreatedEvent event): void {
        auditService.logProductCreation(
            event.getProductId().getValue(),
            event.getName(),
            event.getPrice(),
            event.getCorrelationId()
        );

        notificationService.sendProductCreatedNotification(event.getName());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onProductStockDecreased(ProductStockDecreasedEvent event): void {
        notificationService.sendStockUpdateNotification(
            event.getProductId().getValue(),
            event.getQuantity()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    fun onTransactionRollback(DomainEvent event): void {
        log.error("Transaction rolled back for event: {}", event.getEventId());
    }
}
```

### Async Event Listener

```kotlin
@Component
@RequiredArgsConstructor
class AsyncEventHandler {
    private val emailService: EmailService

    @Async
    @EventListener
    fun handleOrderCreatedEvent(OrderCreatedEvent event): void {
        // Executes asynchronously in a separate thread
        emailService.sendOrderConfirmationEmail(
            event.getCustomerId().getValue(),
            event.getOrderId().getValue()
        );
    }
}
```

## Kafka Event Consumption

### Kafka Listener

```kotlin
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
class ProductEventConsumer {

    private val orderService: OrderService

    @KafkaListener(
        topics = "product-events",
        groupId = "order-service",
        properties = {
            "spring.json.value.default.type=com.example.events.ProductCreatedEventDto"
        }
    )
    fun handleProductCreated(ProductCreatedEventDto event): void {
        log.info("Received ProductCreatedEvent: {}", event.getProductId());

        try {
            orderService.onProductCreated(event);
        } catch (Exception e) {
            log.error("Failed to handle ProductCreatedEvent", e);
            throw e; // Re-throw to trigger retry
        }
    }

    @KafkaListener(
        topics = "product-events",
        groupId = "order-service",
        properties = {
            "spring.json.value.default.type=com.example.events.ProductStockDecreasedEventDto"
        }
    )
    fun handleProductStockDecreased(ProductStockDecreasedEventDto event): void {
        log.info("Received ProductStockDecreasedEvent: {}", event.getProductId());

        orderService.onProductStockDecreased(event);
    }
}
```

### Manual Acknowledgment

```kotlin
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;

@Component
@Slf4j
class ManualAckConsumer {

    @KafkaListener(
        topics = "product-events",
        groupId = "order-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleWithManualAck(
    @Payload ProductCreatedEventDto event,
    @Header(KafkaHeaders.ACKNOWLEDGMENT) Acknowledgment acknowledgment
    )
    {
        try {
            // Process event
            orderService.onProductCreated(event);

            // Manually acknowledge
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process event", e);
            // Don't acknowledge - message will be redelivered
        }
    }
}
```

### Error Handling with Dead Letter Queue

```kotlin
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;

@Component
@Slf4j
class ResilientEventConsumer {

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2),
        autoCreateTopics = "false",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(
        topics = "product-events",
        groupId = "order-service"
    )
    fun handleProductEvent(ProductCreatedEventDto event): void {
        log.info("Processing product event: {}", event.getProductId());

        // Process event
        orderService.onProductCreated(event);
    }

    @KafkaListener(
        topics = "product-events-dlt",
        groupId = "order-service-dlt"
    )
    fun handleDeadLetterEvent(ProductCreatedEventDto event): void {
        log.error("Event moved to DLT: {}", event.getProductId());

        // Log to monitoring system
        monitoringService.alertDeadLetterEvent(event);

        // Store for manual inspection
        deadLetterRepository.save(event);
    }
}
```

## Spring Cloud Stream Consumption

### Functional Consumer

```kotlin
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
class ProductEventStreamConsumer {
    private val orderService: OrderService

    @Bean
    public Consumer<ProductCreatedEventDto> productCreated()
    {
        return event -> {
        log.info("Received ProductCreatedEvent: {}", event.getProductId());
        orderService.onProductCreated(event);
    };
    }

    @Bean
    public Consumer<ProductStockDecreasedEventDto> productStockDecreased()
    {
        return event -> {
        log.info("Received ProductStockDecreasedEvent: {}", event.getProductId());
        orderService.onProductStockDecreased(event);
    };
    }
}
```

### Consumer with Error Handling

```kotlin
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
class ErrorHandlingConsumer {

    @Bean
    public Consumer<Message<ProductCreatedEventDto>> productCreatedWithRetry()
    {
        return message -> {
        try {
            ProductCreatedEventDto event = message . getPayload ();
            orderService.onProductCreated(event);
        } catch (Exception e) {
            log.error("Failed to process event", e);

            // Send to dead letter topic
            throw RuntimeException("Failed to process event", e);
        }
    };
    }
}
```

## Event Handler Best Practices

### 1. Idempotent Handlers

```kotlin
@Component
@RequiredArgsConstructor
class IdempotentEventHandler {
    private val processedEventRepository: ProcessedEventRepository

    fun handleProductCreated(ProductCreatedEventDto event): void {
        // Check if event was already processed
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            log.info("Event already processed: {}", event.getEventId());
            return;
        }

        // Process event
        orderService.onProductCreated(event);

        // Mark as processed
        processedEventRepository.save(ProcessedEvent(event.getEventId()));
    }
}
```

### 2. Event Handler with Validation

```kotlin
@Component
class ValidatingEventHandler {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderCreated(OrderCreatedEvent event): void {
        // Validate event
        if (event.getItems().isEmpty()) {
            throw InvalidEventException("Order items cannot be empty");
        }

        if (event.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw InvalidEventException("Total amount must be positive");
        }

        // Process valid event
        inventoryService.reserveItems(event.getItems());
        paymentService.charge(event.getTotalAmount());
    }
}
```

### 3. Event Handler with Circuit Breaker

```kotlin
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

@Component
@RequiredArgsConstructor
class ResilientEventHandler {
    private val externalServiceClient: ExternalServiceClient

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @CircuitBreaker(
        name = "externalService",
        fallbackMethod = "handleExternalServiceFailure"
    )
    fun handleOrderCreated(OrderCreatedEvent event): void {
        externalServiceClient.notifyOrderCreated(event);
    }

    private fun handleExternalServiceFailure(OrderCreatedEvent event, Exception ex): void {
        log.error("External service unavailable for event: {}", event.getOrderId(), ex);

        // Store event for later retry
        outboxRepository.save(OutboxEvent.from(event));
    }
}
```

### 4. Event Handler with Timeout

```kotlin
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.TimeUnit;

@Component
class TimeoutEventHandler {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleOrderCreated(OrderCreatedEvent event): void {
        ExecutorService executor = Executors . newSingleThreadExecutor ();

        try {
            Future<?> future = executor . submit (() -> {
                notificationService.sendOrderConfirmation(event);
            });

            future.get(5, TimeUnit.SECONDS); // Timeout after 5 seconds

        } catch (TimeoutException e) {
            log.error("Notification timed out for order: {}", event.getOrderId());
            // Handle timeout appropriately
        } catch (Exception e) {
            log.error("Failed to send notification", e);
            throw EventHandlingException("Failed to handle event", e);
        } finally {
            executor.shutdown();
        }
    }
}
```

### 5. Batch Event Processing

```kotlin
import org.springframework.scheduling.annotation.Scheduled;
import java.util.List;

@Component
class BatchEventHandler {
    private final List<DomainEvent> eventBuffer = mutableListOf();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun bufferEvent(DomainEvent event): void {
        synchronized(eventBuffer) {
            eventBuffer.add(event);

            if (eventBuffer.size() >= 100) {
                processBatch();
            }
        }
    }

    @Scheduled(fixedDelay = 5000)
    public synchronized void processBatch()
    {
        if (eventBuffer.isEmpty()) {
            return;
        }

        List<DomainEvent> batch = new ArrayList<>(eventBuffer);
        eventBuffer.clear();

        // Process batch
        batchProcessor.process(batch);
    }
}
```
