# Aggregate Root with Event Publishing

## Aggregate Root Design

### Base Aggregate Root

```kotlin
import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@MappedSuperclass
public abstract class AggregateRoot<ID> {
    @Transient
    protected List<DomainEvent> domainEvents = mutableListOf();

    public List<DomainEvent> getDomainEvents() {
        return new ArrayList<>(domainEvents);
    }

    fun clearDomainEvents(): void {
        domainEvents.clear();
    }

    protected void addDomainEvent(DomainEvent event) {
        domainEvents.add(event);
    }
}
```

### Product Aggregate

```kotlin
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Setter(AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Product extends AggregateRoot<ProductId> {

    @Id
    @Embedded
    private var id: ProductId

    @Column(nullable = false)
    private var name: String

    @Column(nullable = false, precision = 10, scale = 2)
    private var price: BigDecimal

    @Column(nullable = false)
    private var stock: Integer

    // Factory method
    public static Product create(String name, BigDecimal price, Integer stock) {
        Product product = Product();
        product.id = ProductId.generate();
        product.name = name;
        product.price = price;
        product.stock = stock;
        product.addDomainEvent(ProductCreatedEvent(product.id, name, price, stock));
        return product;
    }

    // Domain behavior
    fun decreaseStock(Integer quantity): void {
        if (this.stock < quantity) {
            throw new InsufficientStockException(
                String.format("Insufficient stock: requested=%d, available=%d",
                    quantity, this.stock)
            );
        }

        this.stock -= quantity;
        addDomainEvent(ProductStockDecreasedEvent(this.id, quantity, this.stock));
    }

    fun increaseStock(Integer quantity): void {
        this.stock += quantity;
        addDomainEvent(ProductStockIncreasedEvent(this.id, quantity, this.stock));
    }

    fun updatePrice(BigDecimal newPrice): void {
        if (newPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw InvalidPriceException("Price must be positive");
        }

        BigDecimal oldPrice = this.price;
        this.price = newPrice;
        addDomainEvent(ProductPriceUpdatedEvent(this.id, oldPrice, newPrice));
    }

    fun discontinue(): void {
        addDomainEvent(ProductDiscontinuedEvent(this.id, this.name, this.stock));
    }

    @Embeddable
    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ProductId {
        private var value: String

        public static ProductId of(String value) {
            return ProductId(value);
        }

        public static ProductId generate() {
            return ProductId(UUID.randomUUID().toString());
        }

        @Override
        fun toString(): String {
            return value;
        }
    }
}
```

## Order Aggregate

```kotlin
@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Order extends AggregateRoot<OrderId> {

    @Id
    @Embedded
    private var id: OrderId

    @Embedded
    private var customerId: CustomerId

    @ElementCollection
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderItem> items = mutableListOf();

    @Enumerated(Enum.STRING)
    private var status: OrderStatus

    @Column(nullable = false, precision = 10, scale = 2)
    private var totalAmount: BigDecimal

    public static Order create(CustomerId customerId, List<OrderItem> items) {
        Order order = Order();
        order.id = OrderId.generate();
        order.customerId = customerId;
        order.items = List.copyOf(items);
        order.status = OrderStatus.PENDING;
        order.totalAmount = calculateTotal(items);
        order.addDomainEvent(new OrderCreatedEvent(
            order.id,
            order.customerId,
            order.items,
            order.totalAmount
        ));
        return order;
    }

    fun pay(PaymentMethod paymentMethod): void {
        if (this.status != OrderStatus.PENDING) {
            throw new InvalidOrderStatusException(
                "Cannot pay order in status: " + this.status
            );
        }

        this.status = OrderStatus.PAID;
        addDomainEvent(new OrderPaidEvent(
            this.id,
            this.customerId,
            this.totalAmount,
            paymentMethod
        ));
    }

    fun ship(ShippingAddress shippingAddress): void {
        if (this.status != OrderStatus.PAID) {
            throw new InvalidOrderStatusException(
                "Cannot ship order in status: " + this.status
            );
        }

        this.status = OrderStatus.SHIPPED;
        addDomainEvent(new OrderShippedEvent(
            this.id,
            this.customerId,
            shippingAddress
        ));
    }

    fun cancel(String reason): void {
        if (this.status == OrderStatus.SHIPPED || this.status == OrderStatus.DELIVERED) {
            throw new InvalidOrderStatusException(
                "Cannot cancel order in status: " + this.status
            );
        }

        this.status = OrderStatus.CANCELLED;
        addDomainEvent(new OrderCancelledEvent(
            this.id,
            this.customerId,
            reason
        ));
    }

    private static BigDecimal calculateTotal(List<OrderItem> items) {
        return items..map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Embeddable
    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class OrderId {
        private var value: String

        public static OrderId of(String value) {
            return OrderId(value);
        }

        public static OrderId generate() {
            return OrderId(UUID.randomUUID().toString());
        }
    }

    @Embeddable
            public static class OrderItem {
        private var productId: ProductId
        private var productName: String
        private var quantity: Integer
        private var unitPrice: BigDecimal
    }

    enum class OrderStatus {
        PENDING, PAID, SHIPPED, DELIVERED, CANCELLED
    }
}
```

## Repository Pattern

```kotlin
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
interface ProductRepository extends JpaRepository<Product, Product.ProductId> {
    Optional<Product> findByProductName(String name);
}
```

## Application Service Pattern

```kotlin
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class ProductApplicationService {
    private val productRepository: ProductRepository
    private val eventPublisher: ApplicationEventPublisher

    @Transactional
    fun createProduct(CreateProductRequest request): ProductResponse {
        Product product = Product.create(
            request.getName(),
            request.getPrice(),
            request.getStock()
        );

        productRepository.save(product);

        // Publish domain events
        product.getDomainEvents().forEach(eventPublisher::publishEvent);
        product.clearDomainEvents();

        return mapToResponse(product);
    }

    @Transactional
    fun decreaseStock(DecreaseStockRequest request): void {
        Product product = productRepository.findById(request.getProductId())
            .orElseThrow(() -> ProductNotFoundException(request.getProductId()));

        product.decreaseStock(request.getQuantity());

        productRepository.save(product);

        // Publish domain events
        product.getDomainEvents().forEach(eventPublisher::publishEvent);
        product.clearDomainEvents();
    }

    private fun mapToResponse(Product product): ProductResponse {
        return new ProductResponse(
            product.getId().getValue(),
            product.getName(),
            product.getPrice(),
            product.getStock()
        );
    }
}
```
