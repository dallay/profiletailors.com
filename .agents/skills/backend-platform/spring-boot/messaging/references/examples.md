# Spring Boot Event-Driven Patterns - Examples

Comprehensive examples demonstrating event-driven architecture from basic local events to advanced
distributed messaging.

## Example 1: Basic Domain Events

A simple product lifecycle with domain events.

```kotlin
// Domain event
data class ProductCreatedEvent(
    val productId: String,
    val name: String,
    val price: BigDecimal
) : DomainEvent()

// Aggregate publishing events
class Product protected constructor() {
    var id: String = ""
        private set
    var name: String = ""
        private set
    var price: BigDecimal = BigDecimal.ZERO
        private set

    @Transient
    private val domainEvents: MutableList<DomainEvent> = mutableListOf()

    companion object {
        fun create(name: String, price: BigDecimal): Product {
            val product = Product()
            product.id = UUID.randomUUID().toString()
            product.name = name
            product.price = price

            // Publish domain event
            product.publishEvent(ProductCreatedEvent(product.id, name, price))

            return product
        }
    }

    protected fun publishEvent(event: DomainEvent) {
        domainEvents.add(event)
    }

    fun getDomainEvents(): List<DomainEvent> {
        return domainEvents.toList()
    }

    fun clearDomainEvents() {
        domainEvents.clear()
    }
}
```

---

## Example 2: Local Event Publishing

Using ApplicationEventPublisher for in-process events.

```kotlin
// Application service
@Service
@Transactional
class ProductApplicationService(
    private val productRepository: ProductRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun createProduct(request: CreateProductRequest): ProductResponse {
        val product = Product.create(request.name, request.price)
        val saved = productRepository.save(product)

        // Publish domain events
        saved.getDomainEvents().forEach { event ->
            log.debug("Publishing event: {}", event::class.simpleName)
            eventPublisher.publishEvent(event)
        }
        saved.clearDomainEvents()

        return mapper.toResponse(saved)
    }
}

// Event listener
@Component
class ProductEventHandler(
    private val notificationService: NotificationService,
    private val inventoryService: InventoryService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onProductCreated(event: ProductCreatedEvent) {
        log.info("Handling ProductCreatedEvent")

        // Send notification
        notificationService.sendProductCreatedNotification(
            event.name, event.price
        )

        // Update inventory
        inventoryService.registerProduct(event.productId)
    }
}

// Test
@SpringBootTest
class ProductEventTest {
    @Autowired
    private lateinit var productService: ProductApplicationService

    @MockBean
    private lateinit var notificationService: NotificationService

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Test
    fun shouldPublishProductCreatedEvent() {
        // Act
        productService.createProduct(
            CreateProductRequest("Laptop", BigDecimal.valueOf(999.99))
        )

        // Assert - Event was handled
        verify(notificationService).sendProductCreatedNotification(
            "Laptop", BigDecimal.valueOf(999.99)
        )
    }
}
```

---

## Example 3: Transactional Outbox Pattern

Ensures reliable event publishing even on failures.

```kotlin
// Outbox entity
@Entity
@Table(name = "outbox_events")
class OutboxEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    var aggregateId: String = "",
    var eventType: String = "",

    @Column(columnDefinition = "TEXT")
    var payload: String = "",

    var createdAt: LocalDateTime = LocalDateTime.now(),
    var publishedAt: LocalDateTime? = null,
    var retryCount: Int = 0
)

// Application service using outbox
@Service
@Transactional
class ProductApplicationService(
    private val productRepository: ProductRepository,
    private val outboxRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun createProduct(request: CreateProductRequest): ProductResponse {
        val product = Product.create(request.name, request.price)
        val saved = productRepository.save(product)

        // Store event in outbox (same transaction)
        saved.getDomainEvents().forEach { event ->
            try {
                val payload = objectMapper.writeValueAsString(event)
                val outboxEvent = OutboxEvent(
                    aggregateId = saved.id,
                    eventType = event::class.simpleName ?: "UnknownEvent",
                    payload = payload,
                    createdAt = LocalDateTime.now(),
                    retryCount = 0
                )

                outboxRepository.save(outboxEvent)
                log.debug("Outbox event created: {}", event::class.simpleName)
            } catch (e: Exception) {
                log.error("Failed to create outbox event", e)
                throw RuntimeException(e)
            }
        }

        return mapper.toResponse(saved)
    }
}

// Scheduled publisher
@Component
class OutboxEventPublisher(
    private val outboxRepository: OutboxEventRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 5000)
    @Transactional
    fun publishPendingEvents() {
        val pending = outboxRepository.findByPublishedAtIsNull()

        for (event in pending) {
            try {
                kafkaTemplate.send(
                    "product-events",
                    event.aggregateId, event.payload
                )

                event.publishedAt = LocalDateTime.now()
                outboxRepository.save(event)

                log.info("Published outbox event: {}", event.id)
            } catch (e: Exception) {
                log.error("Failed to publish event: {}", event.id, e)
                event.retryCount = event.retryCount + 1
                outboxRepository.save(event)
            }
        }
    }
}
```

---

## Example 4: Kafka Event Publishing

Distributed event publishing with Spring Cloud Stream.

```kotlin
// Application configuration
@Configuration
class KafkaConfig {

    @Bean
    fun objectMapper(): ObjectMapper {
        return ObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
}

// Event publisher
@Component
class KafkaProductEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun publishProductCreatedEvent(event: ProductCreatedEvent) {
        log.info("Publishing ProductCreatedEvent to Kafka: {}", event.productId)

        kafkaTemplate.send(
            "product-events",
            event.productId,
            event
        )
    }
}

// Event consumer
@Component
class ProductEventStreamConsumer(
    private val inventoryService: InventoryService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun productCreatedConsumer(): java.util.function.Consumer<ProductCreatedEvent> {
        return java.util.function.Consumer { event ->
            log.info("Consumed ProductCreatedEvent: {}", event.productId)
            inventoryService.registerProduct(event.productId, event.name)
        }
    }

    @Bean
    fun productUpdatedConsumer(): java.util.function.Consumer<ProductUpdatedEvent> {
        return java.util.function.Consumer { event ->
            log.info("Consumed ProductUpdatedEvent: {}", event.productId)
            inventoryService.updateProduct(event.productId, event.price)
        }
    }
}

// Application properties
```

**application.yml:**

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: product-service
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"

  cloud:
    stream:
      bindings:
        productCreatedConsumer-in-0:
          destination: product-events
          group: product-inventory-service
        productUpdatedConsumer-in-0:
          destination: product-events
          group: product-inventory-service
```

---

## Example 5: Event Saga Pattern

Coordinating multiple services with events.

```kotlin
// Events
data class OrderPlacedEvent(
    val orderId: String,
    val productId: String,
    val quantity: Int
) : DomainEvent()

data class OrderPaymentConfirmedEvent(
    val orderId: String
) : DomainEvent()

// Saga orchestrator
@Component
class OrderFulfillmentSaga(
    private val orderService: OrderService,
    private val paymentService: PaymentService,
    private val inventoryService: InventoryService,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    @EventListener
    fun onOrderPlaced(event: OrderPlacedEvent) {
        log.info("Starting order fulfillment saga for order: {}", event.orderId)

        try {
            // Step 1: Reserve inventory
            inventoryService.reserveStock(event.productId, event.quantity)
            log.info("Inventory reserved for order: {}", event.orderId)

            // Step 2: Process payment
            paymentService.processPayment(event.orderId)
            log.info("Payment processed for order: {}", event.orderId)

            // Step 3: Publish confirmation
            eventPublisher.publishEvent(OrderPaymentConfirmedEvent(event.orderId))

            // Step 4: Update order status
            orderService.markAsConfirmed(event.orderId)
            log.info("Order confirmed: {}", event.orderId)

        } catch (e: PaymentFailedException) {
            log.warn("Payment failed, releasing inventory")
            inventoryService.releaseStock(event.productId, event.quantity)
            orderService.markAsFailed(event.orderId, e.message ?: "Payment failed")
        }
    }
}

// Test
@SpringBootTest
class OrderFulfillmentSagaTest {
    @Autowired
    private lateinit var eventPublisher: ApplicationEventPublisher

    @MockBean
    private lateinit var inventoryService: InventoryService

    @MockBean
    private lateinit var paymentService: PaymentService

    @MockBean
    private lateinit var orderService: OrderService

    @Test
    fun shouldCompleteOrderFulfillmentSaga() {
        // Arrange
        val event = OrderPlacedEvent("order-123", "product-456", 2)

        // Act
        eventPublisher.publishEvent(event)

        // Assert
        verify(inventoryService).reserveStock("product-456", 2)
        verify(paymentService).processPayment("order-123")
        verify(orderService).markAsConfirmed("order-123")
    }
}
```

---

## Example 6: Event Sourcing Foundation

Storing state changes as events.

```kotlin
// Event store
@Repository
interface EventStoreRepository : JpaRepository<StoredEvent, UUID> {
    fun findByAggregateIdOrderBySequenceAsc(aggregateId: String): List<StoredEvent>
}

// Stored event
@Entity
@Table(name = "event_store")
class StoredEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    var aggregateId: String = "",
    var eventType: String = "",
    var sequence: Int = 0,

    @Column(columnDefinition = "TEXT")
    var payload: String = "",

    var occurredAt: LocalDateTime = LocalDateTime.now()
)

// Event sourcing service
@Service
class EventSourcingService(
    private val eventStoreRepository: EventStoreRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun storeEvent(aggregateId: String, event: DomainEvent) {
        try {
            val existing = eventStoreRepository
                .findByAggregateIdOrderBySequenceAsc(aggregateId)

            val nextSequence = if (existing.isEmpty()) 1
            else existing.last().sequence + 1

            val storedEvent = StoredEvent(
                aggregateId = aggregateId,
                eventType = event::class.simpleName ?: "UnknownEvent",
                sequence = nextSequence,
                payload = objectMapper.writeValueAsString(event),
                occurredAt = LocalDateTime.now()
            )

            eventStoreRepository.save(storedEvent)
            log.info(
                "Event stored: {} for aggregate: {}",
                event::class.simpleName, aggregateId
            )
        } catch (e: JsonProcessingException) {
            throw RuntimeException("Failed to store event", e)
        }
    }

    fun getEventHistory(aggregateId: String): List<DomainEvent> {
        return eventStoreRepository
            .findByAggregateIdOrderBySequenceAsc(aggregateId)
            .map { deserializeEvent(it) }
    }

    private fun deserializeEvent(stored: StoredEvent): DomainEvent {
        try {
            val eventClass = Class.forName(
                "com.example.product.domain.event.${stored.eventType}"
            )
            return objectMapper.readValue(stored.payload, eventClass) as DomainEvent
        } catch (e: Exception) {
            throw RuntimeException("Failed to deserialize event", e)
        }
    }
}
```

These examples cover local events, transactional outbox pattern, Kafka publishing, saga
coordination, and event sourcing foundations for comprehensive event-driven architecture.
