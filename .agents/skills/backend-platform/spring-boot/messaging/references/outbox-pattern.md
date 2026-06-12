# Transactional Outbox Pattern

## Outbox Entity

### Basic Outbox Event

```kotlin
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private var id: UUID

    @Column(nullable = false)
    private var aggregateId: String

    @Column(nullable = false)
    private var aggregateType: String

    @Column(nullable = false)
    private var eventType: String

    @Lob
    @Column(nullable = false)
    private var payload: String

    @Column(nullable = false)
    private var correlationId: UUID

    @Column(nullable = false)
    private var createdAt: LocalDateTime

    private var publishedAt: LocalDateTime

    @Column(nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    private var errorMessage: String

    private var lastAttemptAt: LocalDateTime
}
```

### Outbox Repository

```kotlin
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByPublishedAtNullOrderByCreatedAtAsc ();

    List<OutboxEvent> findByPublishedAtNullAndRetryCountLessThanOrderByCreatedAtAsc (Integer maxRetries);

    @Query(
        """
        SELECT e FROM OutboxEvent e
        WHERE e.publishedAt IS NULL
        AND e.retryCount < :maxRetries
        AND (e.lastAttemptAt IS NULL OR e.lastAttemptAt < :threshold)
        ORDER BY e.createdAt ASC
    """
    )
    List<OutboxEvent> findPendingEvents (
            Integer maxRetries,
    LocalDateTime threshold
    );
}
```

## Outbox Event Creation

### Save Outbox Event with Aggregate

```kotlin
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class OrderApplicationService {
    private val orderRepository: OrderRepository
    private val outboxRepository: OutboxEventRepository
    private val objectMapper: ObjectMapper

    @Transactional
    fun createOrder(CreateOrderRequest request): OrderResponse {
        Order order = Order . create (
                request.getCustomerId(),
        request.getItems()
        );

        orderRepository.save(order);

        // Create outbox events atomically with order
        order.getDomainEvents().forEach(domainEvent -> {
            try {
                OutboxEvent outboxEvent = OutboxEvent . builder ()
                    .aggregateId(order.getId().getValue())
                    .aggregateType("Order")
                    .eventType(domainEvent.getClass().getSimpleName())
                    .payload(objectMapper.writeValueAsString(domainEvent))
                    .correlationId(domainEvent.getCorrelationId())
                    .createdAt(LocalDateTime.now())
                    .build();

                outboxRepository.save(outboxEvent);
            } catch (JsonProcessingException e) {
                throw EventSerializationException("Failed to serialize event", e);
            }
        });

        order.clearDomainEvents();

        return mapToResponse(order);
    }
}
```

## Outbox Event Processor

### Scheduled Event Publisher

```kotlin
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
class OutboxEventProcessor {
    private val outboxRepository: OutboxEventRepository
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    fun processPendingEvents(): void {
        List<OutboxEvent> pendingEvents = outboxRepository . findByPublishedAtNullOrderByCreatedAtAsc ();

        for (OutboxEvent event : pendingEvents) {
            try {
                publishEvent(event);
                event.setPublishedAt(LocalDateTime.now());
                outboxRepository.save(event);

                log.info("Published outbox event: {}", event.getId());

            } catch (Exception e) {
                handlePublishFailure(event, e);
            }
        }
    }

    private void publishEvent(OutboxEvent event) throws JsonProcessingException
    {
        String topic = determineTopic (event.getEventType());

        kafkaTemplate.send(
            topic,
            event.getAggregateId(),
            event.getPayload()
        ).get(5, TimeUnit.SECONDS);
    }

    private fun handlePublishFailure(OutboxEvent event, Exception e): void {
        log.error("Failed to publish outbox event: {}", event.getId(), e);

        event.setRetryCount(event.getRetryCount() + 1);
        event.setLastAttemptAt(LocalDateTime.now());
        event.setErrorMessage(e.getMessage());

        outboxRepository.save(event);

        if (event.getRetryCount() >= 3) {
            log.error("Max retries exceeded for event: {}", event.getId());
            // Send alert to monitoring system
        }
    }

    private fun determineTopic(String eventType): String {
        return switch(eventType) {
            case "OrderCreatedEvent", "OrderPaidEvent" -> "order-events";
            case "ProductCreatedEvent", "ProductStockDecreasedEvent" -> "product-events";
            default -> "default-events";
        };
    }
}
```

### Idempotent Event Publisher

```kotlin
@Component
@RequiredArgsConstructor
class IdempotentOutboxProcessor {
    private val outboxRepository: OutboxEventRepository
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    fun processPendingEvents(): void {
        LocalDateTime threshold = LocalDateTime . now ().minusMinutes(5);
        List<OutboxEvent> pendingEvents = outboxRepository . findPendingEvents (3, threshold);

        for (OutboxEvent event : pendingEvents) {
            if (shouldProcessEvent(event)) {
                publishEvent(event);
            }
        }
    }

    private fun shouldProcessEvent(OutboxEvent event): boolean {
        // Don't process if recently attempted
        if (event.getLastAttemptAt() != null &&
            event.getLastAttemptAt().isAfter(LocalDateTime.now().minusMinutes(1))
        ) {
            return false;
        }

        return true;
    }

    private fun publishEvent(OutboxEvent event): void {
        try {
            kafkaTemplate.send(
                determineTopic(event.getEventType()),
                event.getAggregateId(),
                event.getPayload()
            ).get();

            event.setPublishedAt(LocalDateTime.now());
            outboxRepository.save(event);

        } catch (Exception e) {
            event.setRetryCount(event.getRetryCount() + 1);
            event.setLastAttemptAt(LocalDateTime.now());
            event.setErrorMessage(e.getMessage());
            outboxRepository.save(event);
        }
    }
}
```

## Cleanup Strategy

### Purge Published Events

```kotlin
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class OutboxCleanupService {
    private val outboxRepository: OutboxEventRepository

    @Scheduled(cron = "0 0 2 * * ?") // 2 AM daily
    @Transactional
    fun purgePublishedEvents(): void {
        LocalDateTime cutoff = LocalDateTime . now ().minusDays(7);

        List<OutboxEvent> eventsToDelete = outboxRepository
                .findByPublishedAtBeforeAndPublishedAtIsNotNull(cutoff);

        outboxRepository.deleteAll(eventsToDelete);

        log.info("Purged {} published outbox events", eventsToDelete.size());
    }
}
```

### Archive Old Events

```kotlin
@Scheduled(cron = "0 0 3 * * ?") // 3 AM daily
@Transactional
fun archivePublishedEvents(): void {
    LocalDateTime cutoff = LocalDateTime . now ().minusDays(30);

    List<OutboxEvent> eventsToArchive = outboxRepository
            .findByPublishedAtBeforeAndPublishedAtIsNotNull(cutoff);

    // Move to archive table or external storage
    archiveService.archiveEvents(eventsToArchive);

    outboxRepository.deleteAll(eventsToArchive);

    log.info("Archived {} outbox events", eventsToArchive.size());
}
```

## Outbox Pattern Variations

### Optimistic Locking

```kotlin
@Entity
@Table(name = "outbox_events")
class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private var id: UUID

    @Version
    private var version: Long

    // ... other fields
}

@Component
class OptimisticLockingProcessor {
    @Transactional
    fun processEvent(OutboxEvent event): void {
        try {
            publishEvent(event);

            event.setPublishedAt(LocalDateTime.now());
            outboxRepository.save(event);

        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Concurrent modification detected for event: {}", event.getId());
            // Retry after delay
        }
    }
}
```

### Batch Processing

```kotlin
@Component
class BatchOutboxProcessor {
    private static final int BATCH_SIZE = 100;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    fun processPendingEvents(): void {
        int page = 0;
        List<OutboxEvent> batch;

        do {
            Pageable pageable = PageRequest . of (page, BATCH_SIZE);
            batch = outboxRepository.findByPublishedAtNullOrderByCreatedAtAsc(pageable);

            if (!batch.isEmpty()) {
                publishBatch(batch);
            }

            page++;
        } while (batch.size() == BATCH_SIZE);
    }

    private fun publishBatch(List<OutboxEvent> batch): void {
        batch.forEach(event -> {
            try {
                publishEvent(event);
                event.setPublishedAt(LocalDateTime.now());
            } catch (Exception e) {
                event.setRetryCount(event.getRetryCount() + 1);
                event.setErrorMessage(e.getMessage());
            }
        });

        outboxRepository.saveAll(batch);
    }
}
```

## Monitoring and Alerts

### Outbox Metrics

```kotlin
@Component
@RequiredArgsConstructor
class OutboxMetricsReporter {
    private val outboxRepository: OutboxEventRepository
    private val meterRegistry: MeterRegistry

    @Scheduled(fixedDelay = 60000)
    fun reportMetrics(): void {
        long pendingCount = outboxRepository . countByPublishedAtNull ();
        long failedCount = outboxRepository . countByRetryCountGreaterThanEqual (3);

        meterRegistry.gauge("outbox.pending.events", pendingCount);
        meterRegistry.gauge("outbox.failed.events", failedCount);

        if (pendingCount > 1000) {
            log.warn("High outbox backlog: {} pending events", pendingCount);
        }

        if (failedCount > 100) {
            log.error("Many failed outbox events: {}", failedCount);
            // Send alert
        }
    }
}
```
