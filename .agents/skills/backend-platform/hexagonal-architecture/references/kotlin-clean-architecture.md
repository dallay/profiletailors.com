# Kotlin Clean Architecture Patterns

Specific patterns for implementing Clean Architecture in Kotlin 2.x applications.

## Value Objects with Value Classes and Data Classes

Kotlin value classes and data classes provide immutability and concise domain modeling.

```kotlin
import java.util.regex.Pattern

@JvmInline
value class Email(val value: String) {
    init {
        require(PATTERN.matcher(value).matches()) { "Invalid email: $value" }
    }

    fun domain(): String = value.substringAfter('@')

    private companion object {
        val PATTERN: Pattern = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$")
    }
}

data class Address(
    val street: String,
    val city: String,
    val postalCode: String,
    val country: String,
) {
    init {
        require(street.isNotBlank()) { "Street is required" }
        require(city.isNotBlank()) { "City is required" }
    }

    fun formatted(): String = "$street, $city $postalCode, $country"
}
```

## Sealed Domain Events

Use sealed interfaces for exhaustive event handling and controlled inheritance.

```kotlin
import java.time.Instant

sealed interface DomainEvent {
    val occurredAt: Instant
    val aggregateId: String
}

data class OrderCreatedEvent(
    val orderId: OrderId,
    val total: Money,
    override val occurredAt: Instant,
) : DomainEvent {
    override val aggregateId: String = orderId.value.toString()
}

data class OrderConfirmedEvent(
    val orderId: OrderId,
    val confirmedAt: Instant,
) : DomainEvent {
    override val occurredAt: Instant = confirmedAt
    override val aggregateId: String = orderId.value.toString()
}
```

## Strongly Typed IDs

Prevent ID confusion with type-safe wrappers.

```kotlin
import java.util.UUID

@JvmInline
value class OrderId(val value: UUID) {
    companion object {
        fun generate(): OrderId = OrderId(UUID.randomUUID())
        fun fromString(id: String): OrderId = OrderId(UUID.fromString(id))
    }
}

@JvmInline
value class CustomerId(val value: UUID)
```

## Factory Methods in Entities

Centralize creation and reconstitution while protecting invariants.

```kotlin
import java.math.BigDecimal

class Product private constructor(
    val id: ProductId,
    name: String,
    price: Money,
) {
    var name: String = name
        private set

    var description: String? = null
        private set

    var price: Money = price
        private set

    var stock: Stock = Stock.zero()
        private set

    fun updatePrice(newPrice: Money) {
        require(newPrice.amount > BigDecimal.ZERO) { "Price must be positive" }
        price = newPrice
    }

    companion object {
        fun create(name: String, price: Money): Product {
            validateName(name)
            validatePrice(price)
            return Product(
                id = ProductId.generate(),
                name = name,
                price = price,
            )
        }

        fun reconstitute(id: ProductId, name: String, price: Money, stock: Stock): Product {
            val product = Product(id = id, name = name, price = price)
            product.stock = stock
            return product
        }

        private fun validateName(name: String) {
            require(name.isNotBlank() && name.length <= 100) {
                "Product name must be 1-100 characters"
            }
        }

        private fun validatePrice(price: Money) {
            require(price.amount > BigDecimal.ZERO) { "Price must be positive" }
        }
    }
}
```

## Explicit Result Type for Domain Operations

Prefer a domain Result type when business failures are part of expected flow.

```kotlin
sealed interface Result<out T, out E> {
    data class Success<T>(val value: T) : Result<T, Nothing>
    data class Failure<E>(val error: E) : Result<Nothing, E>
}

inline fun <T, E, R> Result<T, E>.fold(
    onSuccess: (T) -> R,
    onFailure: (E) -> R,
): R = when (this) {
    is Result.Success -> onSuccess(value)
    is Result.Failure -> onFailure(error)
}

fun Order.confirm(): Result<Order, OrderError> {
    if (status != OrderStatus.PENDING) {
        return Result.Failure(OrderError.ALREADY_CONFIRMED)
    }
    if (items.isEmpty()) {
        return Result.Failure(OrderError.EMPTY_ORDER)
    }

    status = OrderStatus.CONFIRMED
    return Result.Success(this)
}
```

## Builder Pattern with Kotlin DSL

Kotlin can replace classic Java builder classes with a builder + DSL style.

```kotlin
class Order private constructor(
    val id: OrderId,
    val customerId: CustomerId,
    val items: List<OrderItem>,
    val shippingAddress: ShippingAddress?,
    val paymentMethod: PaymentMethod?,
) {
    companion object {
        fun build(customerId: CustomerId, block: Builder.() -> Unit): Order {
            val builder = Builder(customerId)
            builder.block()
            return builder.build()
        }
    }

    class Builder internal constructor(
        private val customerId: CustomerId,
    ) {
        private var id: OrderId = OrderId.generate()
        private val items: MutableList<OrderItem> = mutableListOf()
        private var shippingAddress: ShippingAddress? = null
        private var paymentMethod: PaymentMethod? = null

        fun item(productId: ProductId, quantity: Int, price: Money) {
            items += OrderItem(productId, quantity, price)
        }

        fun shipTo(address: ShippingAddress) {
            shippingAddress = address
        }

        fun payWith(method: PaymentMethod) {
            paymentMethod = method
        }

        internal fun build(): Order {
            require(items.isNotEmpty()) { "Order must have at least one item" }
            return Order(
                id = id,
                customerId = customerId,
                items = items.toList(),
                shippingAddress = shippingAddress,
                paymentMethod = paymentMethod,
            )
        }
    }
}

val order = Order.build(customerId) {
    item(product1, quantity = 2, price = Money("29.99", EUR))
    item(product2, quantity = 1, price = Money("49.99", EUR))
    shipTo(ShippingAddress("123 Main St", "City", "12345"))
    payWith(PaymentMethod.CREDIT_CARD)
}
```

## Domain Service Pattern

Use a domain service when behavior spans multiple aggregates or does not fit one entity.

```kotlin
import java.math.BigDecimal

interface PricingService {
    fun calculateTotal(items: List<OrderItem>, customerType: CustomerType): Money
}

class StandardPricingService : PricingService {
    private val vipDiscount = BigDecimal("0.90")

    override fun calculateTotal(items: List<OrderItem>, customerType: CustomerType): Money {
        val subtotal = items
            .map { it.subtotal }
            .fold(Money.zero()) { acc, money -> acc + money }

        return if (customerType == CustomerType.VIP) subtotal.multiply(vipDiscount) else subtotal
    }
}
```

## Specification Pattern

Model composable business rules with a functional interface and combinators.

```kotlin
fun interface Specification<T> {
    fun isSatisfiedBy(candidate: T): Boolean

    infix fun and(other: Specification<T>): Specification<T> =
        Specification { candidate ->
            isSatisfiedBy(candidate) && other.isSatisfiedBy(candidate)
        }

    infix fun or(other: Specification<T>): Specification<T> =
        Specification { candidate ->
            isSatisfiedBy(candidate) || other.isSatisfiedBy(candidate)
        }

    fun not(): Specification<T> =
        Specification { candidate -> !isSatisfiedBy(candidate) }
}

object OrderSpecifications {
    fun isPending(): Specification<Order> =
        Specification { it.status == OrderStatus.PENDING }

    fun hasMinimumValue(minimum: Money): Specification<Order> =
        Specification { it.total.amount >= minimum.amount }

    fun isEligibleForAutoApproval(): Specification<Order> =
        (isPending() and hasMinimumValue(Money("1000.00", EUR))).not()
}
```

## Thread Safe Domain Events in Aggregate Roots

Track domain events in aggregates without exposing mutable state.

```kotlin
import java.util.concurrent.CopyOnWriteArrayList

abstract class AggregateRoot {
    private val domainEvents: CopyOnWriteArrayList<DomainEvent> = CopyOnWriteArrayList()

    protected fun registerEvent(event: DomainEvent) {
        domainEvents += event
    }

    fun domainEvents(): List<DomainEvent> = domainEvents.toList()

    fun pullDomainEvents(): List<DomainEvent> {
        val snapshot = domainEvents.toList()
        domainEvents.clear()
        return snapshot
    }
}
```

## Hexagonal Layer Reminder

- `domain/`: pure Kotlin only (entities, value objects, domain services, ports, events)
- `application/`: use-case orchestration (commands, queries, handlers)
- `infrastructure/`: adapters (Spring, persistence, messaging, HTTP)

Keep dependency direction one-way:

`domain <- application <- infrastructure`


