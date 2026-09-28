# Domain Events Design

## Domain Event Base Class

Create an immutable base class for all domain events:

```kotlin
import java.time.LocalDateTime;
import java.util.UUID;

public abstract class DomainEvent {
    private val eventId: UUID
    private val occurredAt: LocalDateTime
    private val correlationId: UUID

    protected DomainEvent()
    {
        this.eventId = UUID.randomUUID();
        this.occurredAt = LocalDateTime.now();
        this.correlationId = UUID.randomUUID();
    }

    protected DomainEvent(UUID correlationId)
    {
        this.eventId = UUID.randomUUID();
        this.occurredAt = LocalDateTime.now();
        this.correlationId = correlationId;
    }

    fun getEventId(): UUID {
        return eventId;
    }

    fun getOccurredAt(): LocalDateTime {
        return occurredAt;
    }

    fun getCorrelationId(): UUID {
        return correlationId;
    }
}
```

## Specific Domain Events

### Product Created Event

```kotlin
import java.math.BigDecimal;
import java.util.UUID;

class ProductCreatedEvent extends DomainEvent {
    private val productId: ProductId
    private val name: String
    private val price: BigDecimal
    private val stock: Integer

    public ProductCreatedEvent (ProductId productId, String name, BigDecimal price, Integer stock) {
    super();
    this.productId = productId;
    this.name = name;
    this.price = price;
    this.stock = stock;
}

    fun getProductId(): ProductId {
        return productId;
    }

    fun getName(): String {
        return name;
    }

    fun getPrice(): BigDecimal {
        return price;
    }

    fun getStock(): Integer {
        return stock;
    }
}
```

### Product Stock Decreased Event

```kotlin
class ProductStockDecreasedEvent extends DomainEvent {
    private val productId: ProductId
    private val quantity: Integer
    private val remainingStock: Integer

    public ProductStockDecreasedEvent (ProductId productId, Integer quantity, Integer remainingStock) {
    super();
    this.productId = productId;
    this.quantity = quantity;
    this.remainingStock = remainingStock;
}

    fun getProductId(): ProductId {
        return productId;
    }

    fun getQuantity(): Integer {
        return quantity;
    }

    fun getRemainingStock(): Integer {
        return remainingStock;
    }
}
```

### Order Created Event

```kotlin
import java.util.List;

class OrderCreatedEvent extends DomainEvent {
    private val orderId: OrderId
    private val customerId: CustomerId
    private final List<OrderItem> items;
    private val total: BigDecimal

    public OrderCreatedEvent (OrderId orderId, CustomerId customerId, List<OrderItem> items, BigDecimal total) {
    super();
    this.orderId = orderId;
    this.customerId = customerId;
    this.items = List.copyOf(items);
    this.total = total;
}

    fun getOrderId(): OrderId {
        return orderId;
    }

    fun getCustomerId(): CustomerId {
        return customerId;
    }

    public List < OrderItem > getItems () {
        return items;
    }

    fun getTotal(): BigDecimal {
        return total;
    }
}
```

## Event Design Guidelines

### Naming Conventions

- **Use past tense**: `ProductCreated` (not `CreateProduct`)
- **Reflect business domain**: `OrderPaid`, `InventoryReserved`
- **Be explicit**: `ProductStockDecreased` (not `ProductStockChanged`)

### Event Content

- **Include all relevant data**: Events should be self-contained
- **Use value objects**: `ProductId`, `OrderId` instead of primitive `Long`
- **Make events immutable**: All fields should be `final`

### Event Metadata

- **eventId**: Unique identifier for the event
- **occurredAt**: Timestamp when the event occurred
- **correlationId**: Links related events across aggregates

### Example: Rich Event Design

```kotlin
class OrderPlacedEvent extends DomainEvent {
    private val orderId: OrderId
    private val customerId: CustomerId
    private final List<OrderItem> items;
    private val totalAmount: BigDecimal
    private val shippingAddress: String
    private val paymentMethod: PaymentMethod
    private val estimatedDeliveryDate: Instant

    public OrderPlacedEvent (
            OrderId orderId,
    CustomerId customerId,
    List<OrderItem> items,
    BigDecimal totalAmount,
    String shippingAddress,
    PaymentMethod paymentMethod,
    Instant estimatedDeliveryDate,
    UUID correlationId
    ) {
    super(correlationId);
    this.orderId = orderId;
    this.customerId = customerId;
    this.items = List.copyOf(items);
    this.totalAmount = totalAmount;
    this.shippingAddress = shippingAddress;
    this.paymentMethod = paymentMethod;
    this.estimatedDeliveryDate = estimatedDeliveryDate;
}

    // Getters...

    public record OrderItem(
        ProductId productId,
        String productName,
        Integer quantity,
        BigDecimal unitPrice
    ) {}
}
```

## Event Serialization

### JSON Serialization

```kotlin
import com.fasterxml.jackson.annotation.JsonFormat;

class ProductCreatedEvent extends DomainEvent {
    private val productId: String
    private val name: String

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private val price: BigDecimal

    private val stock: Integer

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private val occurredAt: LocalDateTime

    // Constructor and getters...
}
```

### Event DTO Pattern

```kotlin
// Domain event (internal)
class ProductCreatedEvent extends DomainEvent {
    private val productId: ProductId
    private val name: String
    private val price: BigDecimal
}

// Event DTO (external communication)
class ProductCreatedEventDto {
    private val eventId: String
    private val productId: String
    private val name: String
    private val price: BigDecimal
    private val occurredAt: LocalDateTime
    private val correlationId: String

    public static ProductCreatedEventDto from(ProductCreatedEvent event)
    {
        return new ProductCreatedEventDto (
                event.getEventId().toString(),
        event.getProductId().getValue(),
        event.getName(),
        event.getPrice(),
        event.getOccurredAt(),
        event.getCorrelationId().toString()
        );
    }
}
```

## Event Versioning

### Versioned Events

```kotlin
class ProductCreatedEventV2 extends DomainEvent {
    private val productId: ProductId
    private val name: String
    private val price: BigDecimal
    private val stock: Integer
    private val category: String // New field in V2

    // Include version information
    private final String eventVersion = "2.0";

    // Constructor and getters...
}
```

### Upcaster Pattern

```kotlin
@Component
class EventUpcaster {
    fun upcast(ProductCreatedEventV1 v1Event): ProductCreatedEventV2 {
        return new ProductCreatedEventV2 (
                v1Event.getProductId(),
        v1Event.getName(),
        v1Event.getPrice(),
        v1Event.getStock(),
        "uncategorized" // Default value for new field
        );
    }
}
```
